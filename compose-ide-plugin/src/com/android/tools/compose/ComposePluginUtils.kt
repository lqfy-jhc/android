/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.compose

import androidx.compose.compiler.plugins.kotlin.ComposeClassIds
import androidx.compose.compiler.plugins.kotlin.ComposeFqNames
import androidx.compose.compiler.plugins.kotlin.hasComposableAnnotation
import com.android.tools.idea.kotlin.findAnnotation
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtLocalVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.util.OperatorNameConventions

fun isComposeEnabled(element: PsiElement): Boolean = element.getModuleSystem()?.usesCompose ?: false

fun isModifierChainLongerThanTwo(element: KtElement): Boolean {
  if (element.getChildrenOfType<KtDotQualifiedExpression>().isNotEmpty()) {
    val fqName = element.callReturnTypeFqName()?.asString()
    if (fqName == COMPOSE_MODIFIER_FQN) {
      return true
    }
  }
  return false
}

internal fun KotlinType.isClassOrExtendsClass(classFqName:String): Boolean {
  return fqName?.asString() == classFqName || supertypes().any { it.fqName?.asString() == classFqName }
}

internal fun KtValueArgument.matchingParamTypeFqName(callee: KtNamedFunction): FqName? {
  return if (isNamed()) {
    val argumentName = getArgumentName()!!.asName.asString()
    val matchingParam = callee.valueParameters.find { it.name == argumentName } ?: return null
    matchingParam.returnTypeFqName()
  }
  else {
    val argumentIndex = (parent as KtValueArgumentList).arguments.indexOf(this)
    val paramAtIndex = callee.valueParameters.getOrNull(argumentIndex) ?: return null
    paramAtIndex.returnTypeFqName()
  }
}

internal fun KtDeclaration.returnTypeFqName(): FqName? = if (isK2Plugin()) {
  if (this !is KtCallableDeclaration) null
  else analyze(this) { asFqName(this@returnTypeFqName.getReturnKtType()) }
}
else {
  this.type()?.fqName
}

@OptIn(KtAllowAnalysisOnEdt::class)
internal fun KtElement.callReturnTypeFqName() = if (isK2Plugin()) {
  allowAnalysisOnEdt {
    analyze(this) {
      val callReturnType = this@callReturnTypeFqName.resolveCall()?.singleFunctionCallOrNull()?.symbol?.returnType
      callReturnType?.let { asFqName(it) }
    }
  }
}
else {
  resolveToCall(BodyResolveMode.PARTIAL)?.resultingDescriptor?.returnType?.fqName
}

// TODO(274630452): When the upstream APIs are available, implement it based on `fullyExpandedType` and `KtTypeRenderer`.
private fun KtAnalysisSession.asFqName(type: KtType) = type.expandedClassSymbol?.classIdIfNonLocal?.asSingleFqName()

internal fun KtFunction.hasComposableAnnotation() = if (isK2Plugin()) {
  findAnnotation(ComposeFqNames.Composable) != null
} else {
  descriptor?.hasComposableAnnotation() == true
}

internal fun KtAnalysisSession.isComposableInvocation(callableSymbol: KtCallableSymbol): Boolean {
  fun hasComposableAnnotation(annotated: KtAnnotated?) =
    annotated != null && annotated.hasAnnotation(ComposeClassIds.Composable)

  val type = callableSymbol.returnType
  if (hasComposableAnnotation(type)) return true
  val functionSymbol = callableSymbol as? KtFunctionSymbol
  if (functionSymbol != null &&
      functionSymbol.isOperator &&
      functionSymbol.name == OperatorNameConventions.INVOKE
    ) {
    functionSymbol.receiverType?.let { receiverType ->
      if (hasComposableAnnotation(receiverType)) return true
    }
  }
  return when (callableSymbol) {
    is KtValueParameterSymbol -> false
    is KtLocalVariableSymbol -> false
    is KtPropertySymbol -> hasComposableAnnotation(callableSymbol.getter)
    else -> hasComposableAnnotation(callableSymbol)
  }
}
