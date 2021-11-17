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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdActionPanel;
import com.android.tools.idea.devicemanager.Tables;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.ListSelectionModel;
import org.jetbrains.annotations.NotNull;

final class SelectNextColumnCellAction extends AbstractAction {
  @Override
  public void actionPerformed(@NotNull ActionEvent event) {
    VirtualDeviceTable table = (VirtualDeviceTable)event.getSource();

    if (table.isEmpty()) {
      return;
    }

    ListSelectionModel model = table.getColumnModel().getSelectionModel();
    int viewColumnIndex = model.getLeadSelectionIndex();
    int actionsViewColumnIndex = table.actionsViewColumnIndex();
    AvdInfo device = table.getSelectedDevice().orElseThrow(AssertionError::new);

    switch (table.convertColumnIndexToModel(viewColumnIndex)) {
      case -1:
        table.setRowSelectionInterval(0, 0);
        table.setColumnSelectionInterval(0, 0);

        break;
      case VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX:
      case VirtualDeviceTableModel.API_MODEL_COLUMN_INDEX:
        model.setLeadSelectionIndex(viewColumnIndex + 1);
        break;
      case VirtualDeviceTableModel.SIZE_ON_DISK_MODEL_COLUMN_INDEX:
        model.setLeadSelectionIndex(actionsViewColumnIndex);

        table.editCellAt(table.getSelectedRow(), actionsViewColumnIndex);
        ((ActionsTableCell)table.getCellEditor()).getComponent(device).setFocusedComponent(0);

        break;
      case VirtualDeviceTableModel.ACTIONS_MODEL_COLUMN_INDEX:
        AvdActionPanel panel = ((ActionsTableCell)table.getCellEditor()).getComponent(device);
        int i = panel.getFocusedComponent() + 1;

        if (i < panel.getVisibleComponentCount()) {
          panel.setFocusedComponent(i);
          panel.repaint();
        }
        else {
          table.removeEditor();

          Tables.selectNextOrFirstRow(table);
          model.setLeadSelectionIndex(table.deviceViewColumnIndex());
        }

        break;
      default:
        assert false;
    }
  }
}
