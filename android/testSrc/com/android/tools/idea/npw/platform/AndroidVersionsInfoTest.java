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
package com.android.tools.idea.npw.platform;

import com.android.annotations.NonNull;
import com.android.repository.testframework.FakePackage;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.internal.androidTarget.MockAddonTarget;
import com.android.sdklib.internal.androidTarget.MockPlatformTarget;
import com.android.sdklib.repository.generated.addon.v1.AddonDetailsType;
import com.android.sdklib.repository.targets.SystemImage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.MockitoAnnotations.initMocks;

public final class AndroidVersionsInfoTest {
  private static int DEFAULT_VERSION = 101;
  private static int ADDON_VERSION   = 102;
  private static int HIGHEST_VERSION = 103;
  private static int PREVIEW_VERSION = 104;

  @Mock
  private AndroidVersionsInfo myMockAndroidVersionsInfo;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    Mockito.when(myMockAndroidVersionsInfo.getHighestInstalledVersion()).thenReturn(new AndroidVersion(HIGHEST_VERSION, null));
  }

  /**
   * For Google Glass Addon, the Build API level should be the one specified by the addon
   */
  @Test
  public void remotePlatformWithAddon() {
    FakePackage remotePlatform = new FakePackage("platforms;android-23");
    AddonDetailsType addonDetailsType = new AddonDetailsType();
    addonDetailsType.setTag(SystemImage.GLASS_TAG);
    addonDetailsType.setApiLevel(ADDON_VERSION);

    remotePlatform.setTypeDetails(addonDetailsType);

    AndroidVersionsInfo.VersionItem versionItem = myMockAndroidVersionsInfo.new VersionItem(remotePlatform);
    assertNotNull(versionItem.getAddon());
    assertEquals(ADDON_VERSION, versionItem.getApiLevel());
    assertEquals(ADDON_VERSION, versionItem.getBuildApiLevel());
  }

  /**
   * For versions without an Android target, the Build API should be the highest known stable API
   */
  @Test
  public void noAndroidTarget() {
    AndroidVersionsInfo.VersionItem versionItem = myMockAndroidVersionsInfo.new VersionItem(DEFAULT_VERSION);
    assertEquals(DEFAULT_VERSION, versionItem.getApiLevel());
    assertEquals(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API, versionItem.getBuildApiLevel());
  }

  /**
   * For preview Android target versions, the Build API should be the same as the preview
   */
  @Test
  public void withPreviewAndroidTarget() {
    MockPlatformTarget androidTarget = new MockPlatformTarget(PREVIEW_VERSION, 0) {
      @NonNull
      @Override
      public AndroidVersion getVersion() {
        return new AndroidVersion(PREVIEW_VERSION - 1, "TEST_CODENAME");
      }
    };
    AndroidVersionsInfo.VersionItem versionItem = myMockAndroidVersionsInfo.new VersionItem(androidTarget);
    assertEquals(PREVIEW_VERSION, versionItem.getApiLevel());
    assertEquals(PREVIEW_VERSION, versionItem.getBuildApiLevel());
  }

  /**
   * For platform Android target versions, the Build API should be the same as the platform target
   */
  @Test
  public void withPlatformAndroidTarget() {
    final MockPlatformTarget baseTarget = new MockPlatformTarget(DEFAULT_VERSION, 0);
    MockAddonTarget projectTarget = new MockAddonTarget("google", baseTarget, 1);
    AndroidVersionsInfo.VersionItem versionItem = myMockAndroidVersionsInfo.new VersionItem(projectTarget);
    assertEquals(DEFAULT_VERSION, versionItem.getApiLevel());
    assertEquals(DEFAULT_VERSION, versionItem.getBuildApiLevel());
  }

  /**
   * For preview Android target versions, the Build API should be the same as the preview
   */
  @Test
  public void highestInstalledTarget() {
    MockPlatformTarget androidTarget = new MockPlatformTarget(DEFAULT_VERSION, 0);
    AndroidVersionsInfo.VersionItem versionItem = myMockAndroidVersionsInfo.new VersionItem(androidTarget);
    assertEquals(DEFAULT_VERSION, versionItem.getApiLevel());
    assertEquals(HIGHEST_VERSION, versionItem.getBuildApiLevel());
  }
}
