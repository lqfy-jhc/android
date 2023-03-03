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
package com.android.tools.idea.lint.inspections

import com.android.tools.idea.assistant.OpenAssistSidePanelAction
import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.DefaultLintQuickFix
import com.android.tools.lint.checks.GradleDetector
import com.intellij.psi.PsiElement

class LaunchTargetSdkVersionAssistantFix :
  DefaultLintQuickFix("Launch Android SDK Upgrade Assistant") {
  override fun isApplicable(
    startElement: PsiElement,
    endElement: PsiElement,
    contextType: AndroidQuickfixContexts.ContextType
  ): Boolean = true
  override fun apply(
    startElement: PsiElement,
    endElement: PsiElement,
    context: AndroidQuickfixContexts.Context
  ) {
    GradleDetector.Companion.stopFlaggingTargetSdkEdits()
    OpenAssistSidePanelAction()
      .openWindow("DeveloperServices.TargetSDKVersionUpgradeAssistant", startElement.project)
  }
}
