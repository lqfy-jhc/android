/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.databinding.reference

import com.android.tools.idea.lang.databinding.model.PsiModelClass
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier

/**
 * Reference that refers to a [PsiField]
 */
internal class PsiFieldReference(refExpr: PsiDbRefExpr, field: PsiField)
  : DbExprReference(refExpr, field, refExpr.id.textRange.shiftLeft(refExpr.textOffset)) {

  override val resolvedType: PsiModelClass
    get() = PsiModelClass((resolve() as PsiField).type)

  override val isStatic: Boolean
    get() {
      val modifierList = (resolve() as PsiField).modifierList
      return modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)
    }
}
