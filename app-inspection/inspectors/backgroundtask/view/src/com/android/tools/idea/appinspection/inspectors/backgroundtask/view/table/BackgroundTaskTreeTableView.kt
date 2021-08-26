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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.view.table

import androidx.work.inspection.WorkManagerInspectorProtocol
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskTreeModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntrySelectionModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.AlarmEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.BackgroundTaskEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.JobEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WakeLockEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WorkEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.toFormattedTimeString
import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.tree.TreeModelAdapter
import icons.StudioIcons
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.event.TreeModelEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * A [JBScrollPane] that consists of a tree table with basic information of all background tasks.
 */
class BackgroundTaskTreeTableView(client: BackgroundTaskInspectorClient,
                                  selectionModel: EntrySelectionModel,
                                  scope: CoroutineScope,
                                  uiDispatcher: CoroutineDispatcher) {
  val component: JComponent
  val treeModel = BackgroundTaskTreeModel(client, scope, uiDispatcher)

  init {
    val tree = JTree(treeModel)
    tree.isRootVisible = false
    // Allow variable row heights.
    tree.rowHeight = 0
    tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

    treeModel.addTreeModelListener(object : TreeModelAdapter() {
      override fun treeStructureChanged(event: TreeModelEvent) {
        super.treeStructureChanged(event)
        tree.expandPath(event.treePath)
      }
    })

    tree.addTreeSelectionListener { event ->
      if (event.isAddedPath) {
        val node = event.path.lastPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
        val entry = node.userObject as? BackgroundTaskEntry ?: return@addTreeSelectionListener
        selectionModel.selectedEntry = entry
      }
      else {
        selectionModel.selectedEntry = null
      }
    }

    selectionModel.registerEntrySelectionListener { entry ->
      if (entry == null) {
        with(tree.selectionPath) {
          tree.removeSelectionPath(this)
        }
      }
      else {
        val node = treeModel.getTreeNode(entry.id) ?: return@registerEntrySelectionListener
        tree.selectionModel.selectionPath = TreePath(node.path)
      }
    }

    val builder = ColumnTreeBuilder(tree).setShowVerticalLines(true)

    builder.setCustomRenderer { _, value, _, _, _, _, _ ->
      JLabel((value as DefaultMutableTreeNode).userObject as String).apply {
        preferredSize = Dimension(preferredSize.width, 30)
      }
    }

    builder.addColumn(ColumnTreeBuilder.ColumnBuilder().setName("Class").setRenderer(object : ColoredTreeCellRenderer() {
      override fun customizeCellRenderer(tree: JTree,
                                         value: Any?,
                                         selected: Boolean,
                                         expanded: Boolean,
                                         leaf: Boolean,
                                         row: Int,
                                         hasFocus: Boolean) {
        when (val data = (value as DefaultMutableTreeNode).userObject) {
          is BackgroundTaskEntry -> {
            append(data.className)
          }
        }
      }
    }))
    builder.addColumn(ColumnTreeBuilder.ColumnBuilder().setName("Status").setRenderer(object : ColoredTreeCellRenderer() {
      override fun customizeCellRenderer(tree: JTree,
                                         value: Any?,
                                         selected: Boolean,
                                         expanded: Boolean,
                                         leaf: Boolean,
                                         row: Int,
                                         hasFocus: Boolean) {
        when (val data = (value as DefaultMutableTreeNode).userObject) {
          is BackgroundTaskEntry -> {
            append(data.status)
            val stateIcon = data.icon()
            icon = if (selected && stateIcon != null) ColoredIconGenerator.generateWhiteIcon(stateIcon) else stateIcon
          }
        }
      }
    }))
    builder.addColumn(ColumnTreeBuilder.ColumnBuilder().setName("Start").setRenderer(object : ColoredTreeCellRenderer() {
      override fun customizeCellRenderer(tree: JTree,
                                         value: Any?,
                                         selected: Boolean,
                                         expanded: Boolean,
                                         leaf: Boolean,
                                         row: Int,
                                         hasFocus: Boolean) {
        when (val data = (value as DefaultMutableTreeNode).userObject) {
          is BackgroundTaskEntry -> {
            append(data.startTimeMs.toFormattedTimeString())
          }
        }
      }

    }))

    component = builder.build()
  }
}

private fun BackgroundTaskEntry.icon(): Icon? {
  return when (this) {
    is AlarmEntry -> {
      when (status) {
        AlarmEntry.State.SET.name -> StudioIcons.LayoutEditor.Palette.ANALOG_CLOCK
        AlarmEntry.State.FIRED.name -> AllIcons.RunConfigurations.TestPassed
        AlarmEntry.State.CANCELLED.name -> StudioIcons.Common.CLOSE
        else -> null
      }
    }
    is JobEntry -> {
      when (status) {
        JobEntry.State.SCHEDULED.name -> StudioIcons.LayoutEditor.Palette.ANALOG_CLOCK
        JobEntry.State.STARTED.name -> AnimatedIcon.Default()
        JobEntry.State.STOPPED.name -> AllIcons.RunConfigurations.TestIgnored
        JobEntry.State.FINISHED.name -> AllIcons.RunConfigurations.TestPassed
        else -> null
      }
    }
    is WakeLockEntry -> {
      when (status) {
        WakeLockEntry.State.ACQUIRED.name -> StudioIcons.LayoutEditor.Toolbar.LOCK
        WakeLockEntry.State.RELEASED.name -> StudioIcons.LayoutEditor.Toolbar.UNLOCK
        else -> null
      }
    }
    is WorkEntry -> {
      when (status) {
        WorkManagerInspectorProtocol.WorkInfo.State.ENQUEUED.name -> StudioIcons.LayoutEditor.Palette.CHRONOMETER
        WorkManagerInspectorProtocol.WorkInfo.State.RUNNING.name -> AnimatedIcon.Default()
        WorkManagerInspectorProtocol.WorkInfo.State.BLOCKED.name -> AllIcons.RunConfigurations.TestPaused
        WorkManagerInspectorProtocol.WorkInfo.State.CANCELLED.name -> AllIcons.RunConfigurations.TestIgnored
        WorkManagerInspectorProtocol.WorkInfo.State.FAILED.name -> AllIcons.RunConfigurations.ToolbarError
        WorkManagerInspectorProtocol.WorkInfo.State.SUCCEEDED.name -> AllIcons.RunConfigurations.TestPassed
        else -> null
      }
    }
    else -> null
  }
}
