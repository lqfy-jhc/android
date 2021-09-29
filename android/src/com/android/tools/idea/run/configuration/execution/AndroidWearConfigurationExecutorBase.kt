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
package com.android.tools.idea.run.configuration.execution

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiLineReceiver
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.configuration.AndroidWearConfiguration
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider
import com.android.tools.idea.run.util.LaunchUtils
import com.android.tools.idea.wearpairing.await
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle

abstract class AndroidWearConfigurationExecutorBase(private val environment: ExecutionEnvironment) : RunProfileState {

  protected val configuration = environment.runProfile as AndroidWearConfiguration
  protected val project = configuration.project
  protected val facet = AndroidFacet.getInstance(configuration.configurationModule.module!!)!!
  protected val appId = project.getProjectSystem().getApplicationIdProvider(configuration)?.packageName
                        ?: throw RuntimeException("Cannot get ApplicationIdProvider")

  override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult? {
    throw RuntimeException("Unexpected code path")
  }

  @WorkerThread
  fun execute(): RunContentDescriptor? {
    ProgressManager.checkCanceled()
    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()!!
    indicator.text = "Waiting for all target devices to come online"

    val devices = getDevices()
    devices.forEach { LaunchUtils.initiateDismissKeyguard(it) }
    return doOnDevices(devices, indicator)
  }

  abstract fun doOnDevices(devices: List<IDevice>, indicator: ProgressIndicator): RunContentDescriptor?

  private fun getDevices(): List<IDevice> {
    val devices = runBlocking {
      val provider = DeviceAndSnapshotComboBoxTargetProvider()
      val deployTarget = if (provider.requiresRuntimePrompt(project)) invokeAndWaitIfNeeded {
        provider.showPrompt(facet)
      }
      else provider.getDeployTarget(project)
      val deviceFutureList = deployTarget?.getDevices(facet)?.get() ?: return@runBlocking emptyList()
      return@runBlocking deviceFutureList.map { it.await() }
    }
    if (devices.isEmpty()) {
      throw ExecutionException(AndroidBundle.message("deployment.target.not.found"))
    }
    return devices
  }

  class EmptyProcessHandler : ProcessHandler() {
    override fun destroyProcessImpl() = notifyProcessTerminated(0)
    override fun detachProcessImpl() = notifyProcessDetached()
    override fun detachIsDefault() = true
    override fun getProcessInput() = null
  }

  open class AndroidLaunchReceiver(private val indicator: ProgressIndicator, private val consoleView: ConsoleView) : MultiLineReceiver() {
    override fun isCancelled() = indicator.isCanceled

    override fun processNewLines(lines: Array<String>) = lines.forEach {
      consoleView.print(it + "\n", ConsoleViewContentType.NORMAL_OUTPUT)
    }
  }
}
