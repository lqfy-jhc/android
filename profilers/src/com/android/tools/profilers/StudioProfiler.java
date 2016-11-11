/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers;


import com.android.tools.profiler.proto.Profiler;

public abstract class StudioProfiler {
  abstract public ProfilerMonitor newMonitor(int processId);

  public abstract void startProfiling(Profiler.Process process);

  public abstract void stopProfiling(Profiler.Process process);
}
