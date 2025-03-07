/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.pipeline.AbstractInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLaunchMonitor
import com.android.tools.idea.layoutinspector.pipeline.TreeLoader
import com.android.tools.idea.layoutinspector.pipeline.adb.FakeShellCommandHandler
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.net.Socket
import java.nio.file.Path

class AbstractInspectorClientTest {

  private var shouldEcho = true
  private val commandHandler =
    object : DeviceCommandHandler("shell") {
      override fun accept(
        server: FakeAdbServer,
        socketScope: CoroutineScope,
        socket: Socket,
        device: DeviceState,
        command: String,
        args: String
      ) =
        if (command == ("shell")) {
          writeOkay(socket.getOutputStream())
          if (shouldEcho && args.startsWith("echo ")) {
            writeString(socket.getOutputStream(), args.substringAfter("echo "))
          }
          true
        } else {
          false
        }
    }

  private val disposableRule = DisposableRule()
  private val projectRule = ProjectRule()
  private val adbRule = FakeAdbRule().withDeviceCommandHandler(commandHandler)
  private val adbService = AdbServiceRule(projectRule::project, adbRule)

  @get:Rule
  val ruleChain =
    RuleChain.outerRule(projectRule).around(disposableRule).around(adbRule).around(adbService)!!

  @Before
  fun before() {
    adbRule.attachDevice(
      MODERN_DEVICE.serial,
      MODERN_DEVICE.manufacturer,
      MODERN_DEVICE.model,
      MODERN_DEVICE.version,
      MODERN_DEVICE.apiLevel.toString()
    )
  }

  @Test
  fun clientWithAdbResponseConnects() {
    shouldEcho = true
    adbRule.withDeviceCommandHandler(FakeShellCommandHandler())
    val project = projectRule.project
    val client = MyClient(project, NotificationModel(project), disposableRule.disposable)
    val monitor = mock<InspectorClientLaunchMonitor>()
    client.launchMonitor = monitor
    runBlocking { client.connect(projectRule.project) }
    assertThat(client.isConnected).isTrue()
    verify(monitor).updateProgress(DynamicLayoutInspectorErrorInfo.AttachErrorState.ADB_PING)
  }

  @Test
  fun clientWithNoAdbResponseFailsToConnect() {
    shouldEcho = false
    adbRule.withDeviceCommandHandler(FakeShellCommandHandler())
    val project = projectRule.project
    val client = MyClient(project, NotificationModel(project), disposableRule.disposable)
    val monitor = mock<InspectorClientLaunchMonitor>()
    client.launchMonitor = monitor
    runBlocking { client.connect(projectRule.project) }
    assertThat(client.isConnected).isFalse()
    verify(monitor, times(0))
      .updateProgress(DynamicLayoutInspectorErrorInfo.AttachErrorState.ADB_PING)
  }

  class MyClient(project: Project, notificationModel: NotificationModel, disposable: Disposable) :
    AbstractInspectorClient(
      ClientType.UNKNOWN_CLIENT_TYPE,
      project,
      notificationModel,
      MODERN_DEVICE.createProcess(),
      true,
      DisconnectedClient.stats,
      AndroidCoroutineScope(disposable),
      disposable
    ) {
    override suspend fun doConnect() {}

    override suspend fun doDisconnect() {}

    override suspend fun startFetching() {}

    override suspend fun stopFetching() {}

    override fun refresh() {}

    override fun saveSnapshot(path: Path) {}

    override val treeLoader: TreeLoader = mock()
    override val isCapturing = false
    override val provider: PropertiesProvider = mock()
  }
}
