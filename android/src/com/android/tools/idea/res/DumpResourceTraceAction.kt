/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.tools.idea.IdeInfo
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.DumbAware
import org.jetbrains.android.util.AndroidUtils

/**
 * Dumps resource trace to idea.log.
 */
class DumpResourceTraceAction : AnAction(), DumbAware {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    ResourceUpdateTracer.dumpTrace(null)
  }

  override fun update(event: AnActionEvent) {
    val project = event.project
    event.presentation.isVisible = ApplicationInfo.getInstance().isEAP && project != null
                                   && (IdeInfo.getInstance().isAndroidStudio || AndroidUtils.hasAndroidFacets(project))
    event.presentation.isEnabled = ResourceUpdateTracer.isTracingActive()
  }
}