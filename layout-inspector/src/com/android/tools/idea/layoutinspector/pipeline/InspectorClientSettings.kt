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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project

private const val IS_CAPTURING_KEY = "live.layout.inspector.capturing"

/**
 * Inspector pipeline settings that should be considered global within a project and preserved
 * across multiple inspector clients.
 */
class InspectorClientSettings(private val project: Project) {
  /**
   * The user's preference to use continuous capturing mode in the layout inspector, whenever
   * supported by the current client.
   */
  var isCapturingModeOn
    get() = PropertiesComponent.getInstance(project).getBoolean(IS_CAPTURING_KEY, true)
    set(value) = PropertiesComponent.getInstance(project).setValue(IS_CAPTURING_KEY, value, true)

  /**
   * Disable capturing of bitmap screenshots. The agent will only capture view boundaries and SKP
   * screenshots.
   */
  val disableBitmapScreenshot: Boolean
    get() = LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled
}
