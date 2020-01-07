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
package com.android.tools.idea.ui.resourcemanager.rendering

import java.awt.Color
import java.awt.image.BufferedImage

@Suppress("UndesirableClassUsage")
internal fun createTestImage(): BufferedImage =
  BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB).apply {
    with(createGraphics()) {
      color = Color(0x012345)
      fillRect(0, 0, 100, 100)
    }
  }