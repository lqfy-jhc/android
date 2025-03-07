/*
 * Copyright (C) 2023 The Android Open Source Project
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
@file:JvmName("ModuleRenderContexts")
package org.jetbrains.android.uipreview

import com.android.tools.rendering.ModuleRenderContext
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import org.jetbrains.kotlin.idea.base.util.module

/** Studio-specific [ModuleRenderContext] constructor. */
fun forFile(file: PsiFile): ModuleRenderContext {
  val filePointer = runReadAction { SmartPointerManager.createPointer(file) }
  val module = runReadAction { file.module!! }
  return ModuleRenderContext.forFile({ module }) { runReadAction { filePointer.element } }
}