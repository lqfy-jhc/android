/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.DeploymentApplicationService;
import com.android.tools.idea.run.LaunchableAndroidDevice;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.intellij.execution.runners.ExecutionUtil;
import icons.StudioIcons;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Future;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A virtual device. If it's in the Android Virtual Device Manager Device.myKey is a VirtualDevicePath and myNameKey is not null. If not,
 * Device.myKey may be a VirtualDevicePath, VirtualDeviceName, or SerialNumber depending on what the IDevice returns and myNameKey is null.
 */
final class VirtualDevice extends Device {
  @VisibleForTesting
  static final Icon ourConnectedIcon = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE);

  /**
   * Either a VirtualDeviceNameAndSnapshotPath or a VirtualDeviceName. The virtual device names match with ConnectedDevices that don't
   * support the avd path emulator console subcommand added to the emulator in Version 30.0.18.
   */
  private final @Nullable Key myNameKey;

  private final @NotNull Collection<@NotNull Snapshot> mySnapshots;

  @NotNull
  static VirtualDevice newConnectedDevice(@NotNull ConnectedDevice connectedDevice,
                                          @NotNull KeyToConnectionTimeMap map,
                                          @Nullable VirtualDevice virtualDevice) {
    Device device;
    Key nameKey;

    if (virtualDevice == null) {
      device = connectedDevice;
      nameKey = null;
    }
    else {
      device = virtualDevice;
      nameKey = virtualDevice.myNameKey;
    }

    Key key = device.getKey();

    return new Builder()
      .setName(device.getName())
      .setValid(connectedDevice.isValid())
      .setValidityReason(connectedDevice.getValidityReason())
      .setKey(key)
      .setConnectionTime(map.get(key))
      .setAndroidDevice(connectedDevice.getAndroidDevice())
      .setNameKey(nameKey)
      .addAllSnapshots(device.getSnapshots())
      .build();
  }

  static final class Builder extends Device.Builder {
    private @Nullable Key myNameKey;
    private final @NotNull Collection<@NotNull Snapshot> mySnapshots = new ArrayList<>();

    @NotNull
    Builder setName(@NotNull String name) {
      myName = name;
      return this;
    }

    @NotNull
    Builder setValid(boolean valid) {
      myValid = valid;
      return this;
    }

    @NotNull
    Builder setValidityReason(@Nullable String validityReason) {
      myValidityReason = validityReason;
      return this;
    }

    @NotNull
    Builder setKey(@NotNull Key key) {
      myKey = key;
      return this;
    }

    @NotNull
    @VisibleForTesting
    Builder setConnectionTime(@NotNull Instant connectionTime) {
      myConnectionTime = connectionTime;
      return this;
    }

    @NotNull
    Builder setAndroidDevice(@NotNull AndroidDevice androidDevice) {
      myAndroidDevice = androidDevice;
      return this;
    }

    @NotNull Builder setNameKey(@Nullable Key nameKey) {
      myNameKey = nameKey;
      return this;
    }

    @NotNull Builder addSnapshot(@NotNull Snapshot snapshot) {
      mySnapshots.add(snapshot);
      return this;
    }

    @NotNull Builder addAllSnapshots(@NotNull Collection<@NotNull Snapshot> snapshots) {
      mySnapshots.addAll(snapshots);
      return this;
    }

    @NotNull
    @Override
    VirtualDevice build() {
      return new VirtualDevice(this);
    }
  }

  private VirtualDevice(@NotNull Builder builder) {
    super(builder);

    myNameKey = builder.myNameKey;
    mySnapshots = new ArrayList<>(builder.mySnapshots);
  }

  @NotNull
  @Override
  Icon getIcon() {
    return isConnected() ? ourConnectedIcon : StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE;
  }

  @Override
  boolean isConnected() {
    return getConnectionTime() != null;
  }

  @Override
  @NotNull Collection<@NotNull Snapshot> getSnapshots() {
    return mySnapshots;
  }

  @Override
  boolean matches(@NotNull Key key) {
    return getKey().equals(key) || Objects.equals(myNameKey, key);
  }

  @Override
  boolean hasKeyContainedBy(@NotNull Collection<@NotNull Key> keys) {
    return keys.contains(getKey()) || keys.contains(myNameKey);
  }

  @NotNull
  @Override
  Future<AndroidVersion> getAndroidVersion() {
    Object androidDevice = getAndroidDevice();

    if (androidDevice instanceof LaunchableAndroidDevice) {
      return Futures.immediateFuture(((LaunchableAndroidDevice)androidDevice).getAvdInfo().getAndroidVersion());
    }

    IDevice ddmlibDevice = getDdmlibDevice();
    assert ddmlibDevice != null;

    return DeploymentApplicationService.getInstance().getVersion(ddmlibDevice);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof VirtualDevice)) {
      return false;
    }

    VirtualDevice device = (VirtualDevice)object;

    return getName().equals(device.getName()) &&
           isValid() == device.isValid() &&
           Objects.equals(getValidityReason(), device.getValidityReason()) &&
           getKey().equals(device.getKey()) &&
           Objects.equals(getConnectionTime(), device.getConnectionTime()) &&
           getAndroidDevice().equals(device.getAndroidDevice()) &&
           Objects.equals(myNameKey, device.myNameKey) &&
           mySnapshots.equals(device.mySnapshots);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      getName(),
      isValid(),
      getValidityReason(),
      getKey(),
      getConnectionTime(),
      getAndroidDevice(),
      myNameKey,
      mySnapshots);
  }
}
