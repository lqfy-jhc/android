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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.wearpairing.PairingDevice;
import com.android.tools.idea.wearpairing.WearDevicePairingWizard;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.android.tools.idea.wearpairing.WearPairingManager.PhoneWearPair;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import javax.swing.JMenuItem;
import javax.swing.JTable;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

final class PhysicalDevicePopUpMenuButtonTableCellEditor extends PopUpMenuButtonTableCellEditor {
  private final @NotNull Project myProject;
  private Device myDevice;

  PhysicalDevicePopUpMenuButtonTableCellEditor(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @NotNull Iterable<@NotNull JMenuItem> newItems() {
    Collection<JMenuItem> items = new ArrayList<>();

    items.add(newPairDeviceItem());

    // TODO(http://b/193748564)
    // newUnpairDeviceItem().ifPresent(items::add);

    return items;
  }

  private @NotNull JMenuItem newPairDeviceItem() {
    JMenuItem item = new JBMenuItem("Pair device");

    boolean phone = myDevice.getType().equals(DeviceType.PHONE);
    boolean online = myDevice.isOnline();

    item.setEnabled(phone && online);

    String key;

    if (phone && online) {
      key = "wear.assistant.device.list.tooltip.ok";
    }
    else if (phone) {
      key = "wear.assistant.device.list.tooltip.offline";
    }
    else {
      key = "wear.assistant.device.list.tooltip.unsupported";
    }

    item.setToolTipText(AndroidBundle.message(key));

    item.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(DeviceManagerEvent.EventKind.PHYSICAL_PAIR_DEVICE_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);
      new WearDevicePairingWizard().show(myProject, myDevice.getKey().toString());
    });

    return item;
  }

  @SuppressWarnings("unused")
  private @NotNull Optional<@NotNull JMenuItem> newUnpairDeviceItem() {
    String key = myDevice.getKey().toString();
    PhoneWearPair pair = WearPairingManager.INSTANCE.getPairedDevices(key);

    if (pair == null) {
      return Optional.empty();
    }

    JMenuItem item = new JBMenuItem("Unpair device");

    PairingDevice otherDevice = pair.getPhone().getDeviceID().equals(key) ? pair.getWear() : pair.getPhone();
    item.setToolTipText(AndroidBundle.message("wear.assistant.device.list.forget.connection", otherDevice.getDisplayName()));

    item.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(DeviceManagerEvent.EventKind.PHYSICAL_UNPAIR_DEVICE_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);

      try {
        CoroutineContext context = GlobalScope.INSTANCE.getCoroutineContext();
        BuildersKt.runBlocking(context, (scope, continuation) -> WearPairingManager.INSTANCE.removePairedDevices(key, true, continuation));
      }
      catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        Logger.getInstance(PhysicalDevicePopUpMenuButtonTableCellEditor.class).warn(exception);
      }
    });

    return Optional.of(item);
  }

  @Override
  public @NotNull Component getTableCellEditorComponent(@NotNull JTable table,
                                                        @NotNull Object value,
                                                        boolean selected,
                                                        int viewRowIndex,
                                                        int viewColumnIndex) {
    super.getTableCellEditorComponent(table, value, selected, viewRowIndex, viewColumnIndex);
    myDevice = ((PhysicalDeviceTable)table).getDeviceAt(viewRowIndex);

    return myButton;
  }
}
