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

import com.android.tools.idea.avdmanager.AvdActionPanel;
import com.android.tools.idea.devicemanager.virtualtab.columns.AvdActionsColumnInfo.ActionRenderer;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import org.jetbrains.annotations.NotNull;

final class SelectPreviousColumnAction extends AbstractAction {
  @Override
  public void actionPerformed(@NotNull ActionEvent event) {
    JTable table = (JTable)event.getSource();
    ListSelectionModel model = table.getColumnModel().getSelectionModel();
    int viewColumnIndex = model.getLeadSelectionIndex();

    switch (viewColumnIndex) {
      case VirtualTableView.ACTIONS_VIEW_COLUMN_INDEX:
        AvdActionPanel panel = ((ActionRenderer)table.getCellEditor()).getComponent();

        if (panel.getFocusedComponent() != 0) {
          selectPreviousAvdActionPanelComponent(panel);
        }
        else {
          table.removeEditor();
          model.setLeadSelectionIndex(VirtualTableView.SIZE_ON_DISK_VIEW_COLUMN_INDEX);
        }

        break;
      case VirtualTableView.SIZE_ON_DISK_VIEW_COLUMN_INDEX:
      case VirtualTableView.API_VIEW_COLUMN_INDEX:
        model.setLeadSelectionIndex(viewColumnIndex - 1);
        break;
      case VirtualTableView.DEVICE_VIEW_COLUMN_INDEX:
        break;
      default:
        assert false;
    }
  }

  private static void selectPreviousAvdActionPanelComponent(@NotNull AvdActionPanel panel) {
    int i = panel.getFocusedComponent() - 1;

    if (i >= 0) {
      panel.setFocusedComponent(i);
      panel.repaint();
    }
  }
}
