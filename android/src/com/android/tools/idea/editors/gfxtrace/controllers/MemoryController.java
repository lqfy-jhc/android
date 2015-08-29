/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MemoryController extends Controller {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new MemoryController(editor).myPanel;
  }

  @NotNull private final JPanel myPanel = new JPanel();

  private MemoryController(@NotNull GfxTraceEditor editor) {
    super(editor);
  }

  @Override
  public void notifyPath(Path path) {
  }
}
