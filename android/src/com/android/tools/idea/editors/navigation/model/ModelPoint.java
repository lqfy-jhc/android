/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation.model;

import com.android.tools.idea.editors.navigation.annotations.Property;

public class ModelPoint {
  public static final ModelPoint ORIGIN = new ModelPoint(0, 0);

  public final int x;
  public final int y;

  public ModelPoint(@Property("x") int x, @Property("y") int y) {
    this.x = x;
    this.y = y;
  }
}
