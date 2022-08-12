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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.annotations.concurrency.Slow
import com.android.ddmlib.AndroidDebugBridge
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.layoutinspector.pipeline.adb.AbortAdbCommandRunnable
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbUtils
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.android.tools.idea.project.AndroidNotification
import com.google.common.html.HtmlEscapers
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

private const val PER_DEVICE_SETTING = "debug_view_attributes"
private const val PER_APP_SETTING = "debug_view_attributes_application_package"

/**
 * Helper class that handles setting debug settings on the device via ADB.
 *
 * These debug settings, when set, tell the system to expose some debug data (e.g. Composables)
 * that it normally would hide.
 */
class DebugViewAttributes(private val project: Project, private val process: ProcessDescriptor) {
  private var abortDeleteRunnable: AbortAdbCommandRunnable? = null

  /**
   * Enable debug view attributes for the current process.
   *
   * Ignore failures since we are able to inspect the process without debug view attributes.
   * @return true if the global attributes were changed.
   */
  @Slow
  fun set(): Boolean {
    if (abortDeleteRunnable != null) return false

    var errorMessage: String
    var settingsUpdated = false
    try {
      val adb = AdbUtils.getAdbFuture(project).get() ?: return false
      if (isPerDeviceSettingOn(adb)) {
        return false
      }
      if(isPerAppSettingOn(adb, process.name)) {
        return false
      }
      errorMessage = setPerAppSetting(adb, process.name)

      if (errorMessage.isEmpty()) {
        settingsUpdated = true

        // Later, we'll try to clear the setting via `clear`, but we also register additional logic to trigger automatically
        // (a trap command) if the user forcefully closes the connection under us (e.g. closing the emulator or
        // pulling their USB cable).
        abortDeleteRunnable = AbortAdbCommandRunnable(
          adb,
          process.device,
          // This works by spawning a subshell which hangs forever (waiting for a read that never gets satisfied)
          // but triggers the delete request when that shell is forcefully exited.
          "sh -c 'trap \"settings delete global $PER_APP_SETTING\" EXIT; read'"
        ).also {
          AndroidExecutors.getInstance().workerThreadExecutor.execute(it)
        }
      }
    }
    catch (ex: Exception) {
      Logger.getInstance(DebugViewAttributes::class.java).warn(ex)
      errorMessage = ex.message ?: ex.javaClass.simpleName
    }
    if (errorMessage.isNotEmpty()) {
      val encoder = HtmlEscapers.htmlEscaper()
      val text = encoder.escape("Unable to set the global setting:") + "<br/>" +
                 encoder.escape("\"$PER_APP_SETTING\"") + "<br/>" +
                 encoder.escape("to: \"${process.name}\"") + "<br/><br/>" +
                 encoder.escape("Error: $errorMessage")
      AndroidNotification.getInstance(project).showBalloon("Could not enable resolution traces",
                                                           text, NotificationType.WARNING)
    }
    return settingsUpdated
  }

  /**
   * Disable debug view attributes for the current process that were set when we connected.
   *
   * Return true if the debug view attributes were successfully disabled.
   */
  @Slow
  fun clear() {
    if (abortDeleteRunnable == null) return

    try {
      val adb = AdbUtils.getAdbFuture(project).get() ?: return
      if (isPerAppSettingOn(adb, process.name)) {
        adb.executeShellCommand(process.device, "settings delete global $PER_APP_SETTING")
      }
    }
    catch (_: Exception) { }
    finally {
      abortDeleteRunnable?.stop()
      abortDeleteRunnable = null
    }
  }

  /**
   * Turns on the [PER_APP_SETTING] for [packageName]
   * @return empty string in case of success, or error message otherwise.
   */
  private fun setPerAppSetting(adb: AndroidDebugBridge, packageName: String): String {
    return adb.executeShellCommand(process.device, "settings put global $PER_APP_SETTING $packageName")
  }

  private fun isPerAppSettingOn(adb: AndroidDebugBridge, packageName: String): Boolean {
    // A return value of process.name means: the debug_view_attributes are already turned on for this process.
    val app = adb.executeShellCommand(process.device, "settings get global $PER_APP_SETTING")
    return app == packageName
  }

  private fun isPerDeviceSettingOn(adb: AndroidDebugBridge): Boolean {
    // A return value of "null" or "0" means: "debug_view_attributes" is not currently turned on for all processes on the device.
    return adb.executeShellCommand(process.device, "settings get global $PER_DEVICE_SETTING") !in listOf("null", "0")
  }
}