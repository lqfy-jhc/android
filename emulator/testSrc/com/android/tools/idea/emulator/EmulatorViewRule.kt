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
package com.android.tools.idea.emulator

import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit

/**
 * Allows tests to create [EmulatorView]s connected to [FakeEmulator]s.
 */
class EmulatorViewRule : TestRule {
  private val projectRule = AndroidProjectRule.inMemory()
  private val emulatorRule = FakeEmulatorRule()
  private val fakeEmulators = Int2ObjectOpenHashMap<FakeEmulator>()
  private var counter = 0

  val testRootDisposable: Disposable
    get() = projectRule.fixture.testRootDisposable

  val project: Project
    get() = projectRule.project

  fun newEmulatorView(): EmulatorView {
    val catalog = RunningEmulatorCatalog.getInstance()
    val tempFolder = emulatorRule.root.toPath()
    val grpcPort = 8554 + counter++
    val fakeEmulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), grpcPort)
    fakeEmulators[grpcPort] = fakeEmulator
    fakeEmulator.start()
    val emulators = catalog.updateNow().get()
    val emulatorController = emulators.find { it.emulatorId.grpcPort == grpcPort }!!
    val view = EmulatorView(testRootDisposable, emulatorController, true)
    waitForCondition(5, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    fakeEmulator.getNextGrpcCall(2, TimeUnit.SECONDS) // Skip the initial "getVmState" call.
    return view
  }

  fun executeAction(actionId: String, emulatorView: EmulatorView) {
    val actionManager = ActionManager.getInstance()
    val action = actionManager.getAction(actionId)
    val presentation = Presentation()
    val event = AnActionEvent(null, TestDataContext(emulatorView), ActionPlaces.UNKNOWN, presentation, actionManager, 0)
    action.update(event)
    assertThat(presentation.isEnabledAndVisible).isTrue()
    action.actionPerformed(event)
  }

  fun getFakeEmulator(emulatorView: EmulatorView): FakeEmulator {
    return fakeEmulators[emulatorView.emulator.emulatorId.grpcPort]
  }

  override fun apply(base: Statement, description: Description): Statement {
    return projectRule.apply(emulatorRule.apply(base, description), description)
  }

  private inner class TestDataContext(private val emulatorView: EmulatorView) : DataContext {

    override fun getData(dataId: String): Any? {
      return when (dataId) {
        EMULATOR_CONTROLLER_KEY.name -> emulatorView.emulator
        EMULATOR_VIEW_KEY.name, ZOOMABLE_KEY.name -> emulatorView
        CommonDataKeys.PROJECT.name -> projectRule.project
        else -> null
      }
    }
  }
}