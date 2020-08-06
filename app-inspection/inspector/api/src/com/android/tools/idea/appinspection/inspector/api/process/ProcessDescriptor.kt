/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspector.api.process

/**
 * Basic information about a process running on a device.
 */
interface ProcessDescriptor {
  /** The manufacturer of the device. */
  val manufacturer: String

  /** The model of the device. */
  val model: String

  /** The serial number of the device. */
  val serial: String

  /** The name of the process running on the device. */
  val processName: String

  /** Whether this process is running on a virtual device or a physical one. */
  val isEmulator: Boolean

  /** Whether this process is actively running or not. If not running, that implies it has been terminated. */
  val isRunning: Boolean
}
