/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

/**
 * Tests for {@link GradleProjectSettingsFinder}.
 */
public class GradleProjectSettingsFinderTest extends AndroidGradleTestCase {
  private GradleProjectSettingsFinder mySettingsFinder;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySettingsFinder = new GradleProjectSettingsFinder();
  }

  public void testWithAndroidGradleProject() throws Exception {
    if (SystemInfoRt.isWindows) {
      return; // TODO(b/162746378) failing on windows
    }
    loadSimpleApplication();

    Project project = getProject();
    GradleProjectSettings settings = mySettingsFinder.findGradleProjectSettings(project);
    assertNotNull(settings);

    assertEquals(project.getBasePath(), settings.getExternalProjectPath());
  }

  public void testWithNonGradleProject() {
    Project project = getProject();
    GradleProjectSettings settings = mySettingsFinder.findGradleProjectSettings(project);
    assertNull(settings);
  }
}