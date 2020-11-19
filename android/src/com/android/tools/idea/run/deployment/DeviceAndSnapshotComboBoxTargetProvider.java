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

import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetConfigurable;
import com.android.tools.idea.run.editor.DeployTargetConfigurableContext;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.google.common.base.MoreObjects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeviceAndSnapshotComboBoxTargetProvider extends DeployTargetProvider {
  static final @NotNull com.intellij.openapi.util.Key<@NotNull Boolean> MULTIPLE_DEPLOY_TARGETS = com.intellij.openapi.util.Key
    .create("com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider.MULTIPLE_DEPLOY_TARGETS");

  @NotNull
  @Override
  public String getId() {
    return TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX.name();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Use the device/snapshot drop down";
  }

  @NotNull
  @Override
  public DeployTargetState createState() {
    return DeployTargetState.DEFAULT_STATE;
  }

  @NotNull
  @Override
  public DeployTargetConfigurable createConfigurable(@NotNull Project project,
                                                     @NotNull Disposable parent,
                                                     @NotNull DeployTargetConfigurableContext context) {
    return DeployTargetConfigurable.DEFAULT_CONFIGURABLE;
  }

  // TODO(b/162278375) Remove when prompt-based multiple device deployment is removed.
  @Override
  public boolean requiresRuntimePrompt(@NotNull Project project) {
    return MoreObjects.firstNonNull(project.getUserData(MULTIPLE_DEPLOY_TARGETS), false);
  }

  // TODO(b/162278375) Remove when prompt-based multiple device deployment is removed.
  @Override
  public @Nullable DeployTarget showPrompt(@NotNull AndroidFacet facet) {
    Project project = facet.getModule().getProject();
    List<Device> devices = AsyncDevicesGetter.getInstance(project).get().orElse(Collections.emptyList());

    if (!new SelectMultipleDevicesDialog(project, devices).showAndGet()) {
      return null;
    }

    Collection<Key> keys = DevicesSelectedService.getInstance(project).getTargetsSelectedWithDialog().stream()
      .map(Target::getDeviceKey)
      .collect(Collectors.toSet());

    devices = devices.stream()
      .filter(device -> device.hasKeyContainedBy(keys))
      .collect(Collectors.toList());

    return new DeviceAndSnapshotComboBoxTarget(devices);
  }

  @NotNull
  @Override
  public DeployTarget getDeployTarget(@NotNull Project project) {
    return new DeviceAndSnapshotComboBoxTarget(DeviceAndSnapshotComboBoxAction.getInstance().getSelectedDevices(project));
  }
}
