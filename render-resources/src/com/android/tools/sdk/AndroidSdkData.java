// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.android.tools.sdk;

import static com.intellij.openapi.util.io.FileUtil.pathHashCode;
import static com.intellij.openapi.util.io.FileUtil.pathsEqual;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;

import com.android.prefs.AndroidLocationsSingleton;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.google.common.collect.Maps;
import com.intellij.openapi.projectRoots.Sdk;
import java.io.File;
import java.lang.ref.SoftReference;
import java.nio.file.Path;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

public class AndroidSdkData {
  private final DeviceManager myDeviceManager;
  private static final ConcurrentMap<String/* sdk path */, SoftReference<AndroidSdkData>> ourCache = Maps.newConcurrentMap();
  private final AndroidSdkHandler mySdkHandler;

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull File sdkLocation) {
    return getSdkData(sdkLocation, false);
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull File sdkLocation, boolean forceReparse) {
    String canonicalPath = toCanonicalPath(sdkLocation.getPath());

    // Try to use cached data.
    if (!forceReparse) {
      SoftReference<AndroidSdkData> cachedRef = ourCache.get(canonicalPath);
      if (cachedRef != null) {
        AndroidSdkData cachedData = cachedRef.get();
        if (cachedData == null) {
          ourCache.remove(canonicalPath, cachedRef);
        }
        else {
          return cachedData;
        }
      }
    }

    File canonicalLocation = new File(canonicalPath);
    if (!AndroidSdkPath.isValid(canonicalLocation)) {
      return null;
    }

    AndroidSdkData sdkData = new AndroidSdkData(canonicalLocation);
    ourCache.put(canonicalPath, new SoftReference<>(sdkData));
    return sdkData;
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull String sdkPath) {
    return getSdkData(new File(sdkPath));
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull Sdk sdk) {
    String sdkHomePath = sdk.getHomePath();
    if (sdkHomePath != null) {
      return getSdkData(sdk.getHomePath());
    }
    return null;
  }

  private AndroidSdkData(@NotNull File localSdk) {
    mySdkHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, localSdk.toPath());
    myDeviceManager = DeviceManagers.getDeviceManager(mySdkHandler);
  }

  @NotNull
  public Path getLocation() {
    Path location = mySdkHandler.getLocation();
    // We only construct AndroidSdkData when we have a local SDK, which means location must not be null.
    assert location != null;
    return location;
  }

  @NotNull
  public File getLocationFile() {
    return mySdkHandler.getLocation().toFile();
  }

  @Deprecated
  @NotNull
  public String getPath() {
    return getLocation().toString();
  }

  @Nullable
  public BuildToolInfo getLatestBuildTool(boolean allowPreview) {
    return mySdkHandler.getLatestBuildTool(new LoggerProgressIndicator(getClass()), allowPreview);
  }

  @NotNull
  public IAndroidTarget[] getTargets() {
    Collection<IAndroidTarget> targets = getTargetCollection();
    return targets.toArray(new IAndroidTarget[0]);
  }

  @NotNull
  private Collection<IAndroidTarget> getTargetCollection() {
    ProgressIndicator progress = new LoggerProgressIndicator(getClass());
    return mySdkHandler.getAndroidTargetManager(progress).getTargets(progress);
  }

  @NotNull
  public IAndroidTarget[] getTargets(boolean includeAddOns) {
    Collection<IAndroidTarget> targets = getTargetCollection();
    Collection<IAndroidTarget> result = new ArrayList<>();
    if (!includeAddOns) {
      for (IAndroidTarget target : targets) {
        if (target.isPlatform()) {
          result.add(target);
        }
      }
    }
    else {
      result.addAll(targets);
    }
    return result.toArray(new IAndroidTarget[0]);
  }

  private static boolean targetHasId(@NotNull IAndroidTarget target, @NotNull String id) {
    return id.equals(target.getVersion().getApiString()) || id.equals(target.getVersionName());
  }

  @Nullable
  public IAndroidTarget findTargetByApiLevel(@NotNull String apiLevel) {
    for (IAndroidTarget target : getTargets()) {
      if (targetHasId(target, apiLevel)) {
        return target;
      }
    }
    return null;
  }

  @Nullable
  public IAndroidTarget findTargetByHashString(@NotNull String hashString) {
    ProgressIndicator progress = new LoggerProgressIndicator(getClass());
    return mySdkHandler.getAndroidTargetManager(progress).getTargetFromHashString(hashString, progress);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) return false;
    if (obj.getClass() != getClass()) return false;
    AndroidSdkData sdkData = (AndroidSdkData)obj;
    return pathsEqual(getLocation().toString(), sdkData.getLocation().toString());
  }

  @Override
  public int hashCode() {
    return pathHashCode(getLocation().toString());
  }

  @NotNull
  public DeviceManager getDeviceManager() {
    return myDeviceManager;
  }

  @NotNull
  public AndroidSdkHandler getSdkHandler() {
    return mySdkHandler;
  }
}
