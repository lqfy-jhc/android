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
package com.android.tools.idea.testing

import com.android.flags.Flag
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

/**
 * Overrides the flag value and clears the override when [disposable] is disposed.
 */
fun <T : Any> Flag<T>.override(value: T, disposable: Disposable) {
    Disposer.register(disposable, ::clearOverride)
    override(value)
}
