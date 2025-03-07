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
package com.android.tools.idea.file.explorer.toolwindow;

import com.google.wireless.android.sdk.stats.DeviceExplorerEvent;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class FileTransferSummary {
  private int myFileCount;
  private int myDirectoryCount;
  private long myByteCount;
  private long myDurationMillis;
  @NotNull private DeviceExplorerEvent.Action myAction = DeviceExplorerEvent.Action.UNSPECIFIED;
  @NotNull private List<Throwable> myProblems = new ArrayList<>();

  @NotNull
  public List<Throwable> getProblems() {
    return myProblems;
  }

  public int getFileCount() {
    return myFileCount;
  }

  public void addFileCount(@SuppressWarnings("SameParameterValue") int fileCount) {
    myFileCount += fileCount;
  }

  public int getDirectoryCount() {
    return myDirectoryCount;
  }

  public void addDirectoryCount(@SuppressWarnings("SameParameterValue") int directoryCount) {
    myDirectoryCount += directoryCount;
  }

  public long getByteCount() {
    return myByteCount;
  }

  public void addByteCount(long byteCount) {
    myByteCount += byteCount;
  }

  public long getDurationMillis() {
    return myDurationMillis;
  }

  public void setDurationMillis(long durationMillis) {
    myDurationMillis = durationMillis;
  }

  void setAction(DeviceExplorerEvent.Action action) {
    myAction = action;
  }

  DeviceExplorerEvent.Action getAction() {
    return myAction;
  }
}
