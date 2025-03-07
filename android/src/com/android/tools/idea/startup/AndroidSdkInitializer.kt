/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.startup

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.jetbrains.annotations.NonNls



class AndroidSdkInitializer : ApplicationInitializedListener {
  override suspend fun execute(asyncScope: CoroutineScope) {
    val androidPlatformToCreate = StudioFlags.ANDROID_PLATFORM_TO_AUTOCREATE.get()
    if (androidPlatformToCreate == 0) return

    val androidSdkPath = IdeSdks.getInstance().androidSdkPath ?: return

    thisLogger().info("Automatically creating an Android platform using SDK path $androidSdkPath and SDK version $androidPlatformToCreate")
    invokeLater {
      AndroidSdkUtils.createNewAndroidPlatform(androidSdkPath.toString())
    }
  }
}
