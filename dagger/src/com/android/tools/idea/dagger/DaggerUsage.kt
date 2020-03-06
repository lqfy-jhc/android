/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.dagger

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.AndroidPsiUtils.toPsiType
import com.android.tools.idea.flags.StudioFlags.DAGGER_SUPPORT_ENABLED
import com.android.tools.idea.kotlin.psiType
import com.android.tools.idea.kotlin.toPsiType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getResolveScope
import com.intellij.find.findUsages.CustomUsageSearcher
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass

private val PROVIDED_BY_DAGGER = UsageType("Provided by Dagger")
private val CONSUMED_BY_DAGGER = UsageType("Consumed by Dagger")

/**
 * [UsageTypeProvider] that labels Dagger providers and consumers with the right description.
 */
class DaggerUsageTypeProvider : UsageTypeProvider {
  override fun getUsageType(element: PsiElement?): UsageType? {
    return when {
      !DAGGER_SUPPORT_ENABLED.get() -> null
      element?.module?.isDaggerPresent() != true -> null
      element.isDaggerProvider -> PROVIDED_BY_DAGGER
      element.isDaggerConsumer -> CONSUMED_BY_DAGGER
      else -> null
    }
  }
}

/**
 * Adds custom usages to a find usages window.
 *
 * For Dagger consumers adds providers.
 * For Dagger providers adds consumers.
 *
 * Currently doesn't work for Kotlin if "Search in text occurrences" is not selected.
 * See a bug https://youtrack.jetbrains.com/issue/KT-36657.
 */
class DaggerCustomUsageSearcher : CustomUsageSearcher() {
  @WorkerThread
  override fun processElementUsages(element: PsiElement, processor: Processor<Usage>, options: FindUsagesOptions) {
    runReadAction {
      when {
        !DAGGER_SUPPORT_ENABLED.get() -> return@runReadAction
        element.module?.isDaggerPresent() != true -> return@runReadAction
        element.isDaggerConsumer -> processProviders(element, processor)
        element.isDaggerProvider -> processConsumers(element, processor)
        else -> return@runReadAction
      }
    }
  }

  /**
   * Adds Dagger providers of [element] to [element]'s usages.
   */
  @WorkerThread
  private fun processProviders(element: PsiElement, processor: Processor<Usage>) {
    val type: PsiType =
      when (element) {
        is PsiField -> element.type
        is KtProperty -> element.psiType
        is PsiParameter -> element.type
        is KtParameter -> element.psiType
        else -> null
      } ?: return

    val scope = ModuleUtil.findModuleForPsiElement(element)?.getModuleSystem()?.getResolveScope(element) ?: return

    getDaggerProvidersForType(type, scope).forEach {
      val info = UsageInfo(it)
      processor.process(UsageInfo2UsageAdapter(info))
    }
  }

  /**
   * Adds Dagger consumers of [element] to [element]'s usages.
   */
  @WorkerThread
  private fun processConsumers(element: PsiElement, processor: Processor<Usage>) {
    val type: PsiType =
      when (element) {
        is PsiMethod -> if (element.isConstructor) element.containingClass?.let { toPsiType(it) } else element.returnType
        is KtFunction -> if (element is KtConstructor<*>) element.containingClass()?.toPsiType() else element.psiType
        else -> null
      } ?: return

    val scope = ModuleUtil.findModuleForPsiElement(element)?.getModuleSystem()?.getResolveScope(element) ?: return

    getDaggerConsumersForType(type, scope).forEach {
      val info = UsageInfo(it)
      processor.process(UsageInfo2UsageAdapter(info))
    }
  }
}
