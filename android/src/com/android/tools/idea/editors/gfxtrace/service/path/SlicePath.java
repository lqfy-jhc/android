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
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.idea.editors.gfxtrace.service.path;

import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class SlicePath extends Path {
  //<<<Start:Java.ClassBody:1>>>
  Path myArray;
  long myStart;
  long myEnd;

  // Constructs a default-initialized {@link SlicePath}.
  public SlicePath() {}


  public Path getArray() {
    return myArray;
  }

  public SlicePath setArray(Path v) {
    myArray = v;
    return this;
  }

  public long getStart() {
    return myStart;
  }

  public SlicePath setStart(long v) {
    myStart = v;
    return this;
  }

  public long getEnd() {
    return myEnd;
  }

  public SlicePath setEnd(long v) {
    myEnd = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {-46, 42, 12, 30, -111, 46, 107, -115, -63, -34, 5, -14, 23, 30, -12, 66, 59, 18, -39, 118, };
  public static final BinaryID ID = new BinaryID(IDBytes);

  static {
    Namespace.register(ID, Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public BinaryID id() { return ID; }

    @Override @NotNull
    public BinaryObject create() { return new SlicePath(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      SlicePath o = (SlicePath)obj;
      e.object(o.myArray.unwrap());
      e.uint64(o.myStart);
      e.uint64(o.myEnd);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      SlicePath o = (SlicePath)obj;
      o.myArray = Path.wrap(d.object());
      o.myStart = d.uint64();
      o.myEnd = d.uint64();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
