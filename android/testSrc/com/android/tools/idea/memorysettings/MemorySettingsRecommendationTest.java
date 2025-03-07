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
package com.android.tools.idea.memorysettings;

import static com.android.tools.idea.memorysettings.MemorySettingsRecommendation.DEFAULT_HEAP_SIZE_IN_MB;
import static com.android.tools.idea.memorysettings.MemorySettingsRecommendation.LARGE_HEAP_SIZE_RECOMMENDATION_IN_MB;
import static com.android.tools.idea.memorysettings.MemorySettingsRecommendation.LARGE_MODULE_COUNT;
import static com.android.tools.idea.memorysettings.MemorySettingsRecommendation.LARGE_RAM_IN_GB;
import static com.android.tools.idea.memorysettings.MemorySettingsRecommendation.XLARGE_HEAP_SIZE_RECOMMENDATION_IN_MB;
import static com.android.tools.idea.memorysettings.MemorySettingsRecommendation.XLARGE_MODULE_COUNT;
import static com.android.tools.idea.memorysettings.MemorySettingsRecommendation.XLARGE_RAM_IN_GB;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.analytics.HostData;
import com.android.tools.analytics.stubs.StubOperatingSystemMXBean;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightPlatformTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MemorySettingsRecommendationTest extends LightPlatformTestCase {

  private final int smallRAM = LARGE_RAM_IN_GB - 1;
  private final int normalModuleCount = LARGE_MODULE_COUNT - 1;

  @Test
  public void nullProjectRecommendsDefault() {
    assertEquals(DEFAULT_HEAP_SIZE_IN_MB, MemorySettingsRecommendation.getRecommended(null, DEFAULT_HEAP_SIZE_IN_MB));
  }
  @Test
  public void negativeHeapSizeRecommendsDefault() {
    assertEquals(DEFAULT_HEAP_SIZE_IN_MB, getRecommended(-1, 10, normalModuleCount));
  }
  @Test
  public void smallHeapRecommendsDefault() {
    assertEquals(DEFAULT_HEAP_SIZE_IN_MB, getRecommended(1000, smallRAM, normalModuleCount));
  }
  @Test
  public void defaultHeapStillRecommendsDefaultHeap() {
    assertEquals(DEFAULT_HEAP_SIZE_IN_MB, getRecommended(DEFAULT_HEAP_SIZE_IN_MB, smallRAM, normalModuleCount));
  }
  @Test
  public void smallRAMWithBigProjectRecommendsDefault() {
    assertEquals(DEFAULT_HEAP_SIZE_IN_MB, getRecommended(DEFAULT_HEAP_SIZE_IN_MB, smallRAM, XLARGE_MODULE_COUNT));
  }
  @Test
  public void largeRAMWithBigProjectRecommendsLargeHeap() {
    assertEquals(LARGE_HEAP_SIZE_RECOMMENDATION_IN_MB, getRecommended(DEFAULT_HEAP_SIZE_IN_MB, LARGE_RAM_IN_GB, LARGE_MODULE_COUNT));
  }
  @Test
  public void xLargeRAMWithXLargeProjectRecommendsXLargeHeap() {
    assertEquals(XLARGE_HEAP_SIZE_RECOMMENDATION_IN_MB, getRecommended(DEFAULT_HEAP_SIZE_IN_MB, XLARGE_RAM_IN_GB, XLARGE_MODULE_COUNT));
  }
  @Test
  public void largeRAMWithXLargeProjectRecommendsLargeHeap() {
    assertEquals(LARGE_HEAP_SIZE_RECOMMENDATION_IN_MB, getRecommended(DEFAULT_HEAP_SIZE_IN_MB, LARGE_RAM_IN_GB, XLARGE_MODULE_COUNT));
  }

  @Test
  public void xLargeRAMWithLargeProjectRecommendsLargeHeap() {
    assertEquals(LARGE_HEAP_SIZE_RECOMMENDATION_IN_MB, getRecommended(DEFAULT_HEAP_SIZE_IN_MB, XLARGE_RAM_IN_GB, LARGE_MODULE_COUNT));
  }

  @Test
  public void doNotRecommendSmallerHeap() {
    assertEquals(XLARGE_HEAP_SIZE_RECOMMENDATION_IN_MB, getRecommended(XLARGE_HEAP_SIZE_RECOMMENDATION_IN_MB, smallRAM, normalModuleCount));
  }

  private int getRecommended(int currentXmxInMB, int machineMemInGB, int moduleCount) {
    Project project = mock(Project.class);
    ModuleManager moduleManager = mock(ModuleManager.class);

    when(project.getService(ModuleManager.class)).thenReturn(moduleManager);
    Module[] modules = new Module[moduleCount];
    when(moduleManager.getModules()).thenReturn(modules);
    stubHostData(machineMemInGB);
    return MemorySettingsRecommendation.getRecommended(project, currentXmxInMB);
  }

  private void stubHostData(int machineMemInGB) {
    HostData.setOsBean(new StubOperatingSystemMXBean() {
      @Override
      public long getTotalPhysicalMemorySize() {
        return machineMemInGB * 1024 * 1024L * 1024L;
      }
    });
  }
}
