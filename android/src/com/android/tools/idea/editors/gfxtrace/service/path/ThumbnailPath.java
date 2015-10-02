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

import com.android.tools.rpclib.any.Box;
import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class ThumbnailPath extends Path {
  @Override
  public StringBuilder stringPath(StringBuilder builder) {
    return myObject.stringPath(builder).append(".Thumbnail<").append(myDesiredFormat).append(myDesiredMaxWidth).append(" x ")
      .append(myDesiredMaxHeight).append(">");
  }

  @Override
  public Path getParent() {
    return myObject;
  }

  //<<<Start:Java.ClassBody:1>>>
  private Path myObject;
  private int myDesiredMaxWidth;
  private int myDesiredMaxHeight;
  private Object myDesiredFormat;

  // Constructs a default-initialized {@link ThumbnailPath}.
  public ThumbnailPath() {}


  public Path getObject() {
    return myObject;
  }

  public ThumbnailPath setObject(Path v) {
    myObject = v;
    return this;
  }

  public int getDesiredMaxWidth() {
    return myDesiredMaxWidth;
  }

  public ThumbnailPath setDesiredMaxWidth(int v) {
    myDesiredMaxWidth = v;
    return this;
  }

  public int getDesiredMaxHeight() {
    return myDesiredMaxHeight;
  }

  public ThumbnailPath setDesiredMaxHeight(int v) {
    myDesiredMaxHeight = v;
    return this;
  }

  public Object getDesiredFormat() {
    return myDesiredFormat;
  }

  public ThumbnailPath setDesiredFormat(Object v) {
    myDesiredFormat = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {121, -102, 46, -13, -46, -107, -20, 11, -118, -15, 117, 25, 34, 101, 50, -9, -103, 84, 33, 49, };
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
    public BinaryObject create() { return new ThumbnailPath(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      ThumbnailPath o = (ThumbnailPath)obj;
      e.object(o.myObject.unwrap());
      e.uint32(o.myDesiredMaxWidth);
      e.uint32(o.myDesiredMaxHeight);
      e.variant(Box.wrap(o.myDesiredFormat));
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      ThumbnailPath o = (ThumbnailPath)obj;
      o.myObject = Path.wrap(d.object());
      o.myDesiredMaxWidth = d.uint32();
      o.myDesiredMaxHeight = d.uint32();
      o.myDesiredFormat = ((Box)d.variant()).unwrap();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
