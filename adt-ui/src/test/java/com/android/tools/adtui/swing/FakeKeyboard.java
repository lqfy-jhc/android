/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.swing;

import com.intellij.openapi.util.SystemInfo;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A fake keyboard device that can be used for holding down keys in tests.
 *
 * Do not instantiate directly - use {@link FakeUi#keyboard} instead.
 */
public final class FakeKeyboard {
  public static final int MENU_KEY_CODE = SystemInfo.isMac ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;
  public static final Key MENU_KEY = SystemInfo.isMac ? Key.META : Key.CTRL;

  private final IntArrayList myPressedKeys = new IntArrayList();
  @Nullable private Component myFocus;

  /**
   * Set (or clear) the component that will receive the key events. Note that if the focus is
   * {@code null}, you can still press/release keys but no events will be dispatched.
   */
  public void setFocus(@Nullable Component focus) {
    myFocus = focus;
  }

  public boolean isPressed(int keyCode) {
    return myPressedKeys.contains(keyCode);
  }

  /**
   * Begins holding down the specified key. You may release it later using {@link #release(int)}.
   * This method will not generate {@code KEY_TYPED} events. For now those must be generated using
   * {@link #type(int)}.
   *
   * Unfortunately, it seems like it may take some time for the key event to propagate to its
   * target component. Therefore, you may need to call {@link SwingUtilities#invokeLater(Runnable)}
   * after calling this method, to ensure the key is handled before you check a component's state.
   */
  public void press(int keyCode) {
    performDownKeyEvent(keyCode, KeyEvent.KEY_PRESSED);
  }

  /**
   * Begins holding down the specified key. You may release it later using {@link #release(Key)}.
   * This method will not generate {@code KEY_TYPED} events. For now those must be generated using
   * {@link #type(Key)}.
   *
   * Unfortunately, it seems like it may take some time for the key event to propagate to its
   * target component. Therefore, you may need to call {@link SwingUtilities#invokeLater(Runnable)}
   * after calling this method, to ensure the key is handled before you check a component's state.
   */
  public void press(@NotNull Key key) {
    press(key.code);
  }

  public void release(int keyCode) {
    if (!isPressed(keyCode)) {
      throw new IllegalStateException(String.format("Can't release key %s as it's not pressed.", KeyEvent.getKeyText(keyCode)));
    }

    myPressedKeys.rem(keyCode);
    // Dispatch AFTER removing the key from our list of pressed keys. If it is a modifier key, we
    // don't want it to included in "toModifiersCode" logic called by "dispatchKeyEvent".
    dispatchKeyEvent(KeyEvent.KEY_RELEASED, keyCode);
  }

  public void release(@NotNull Key key) {
    release(key.code);
  }

  public void pressAndRelease(int keyCode) {
    press(keyCode);
    release(keyCode);
  }

  /**
   * Types the specified key. This is a convenience method for generating {@code KEY_TYPED} events.
   *
   * Unfortunately, it seems like it may take some time for the key event to propagate to its
   * target component. Therefore, you may need to call {@link SwingUtilities#invokeLater(Runnable)}
   * after calling this method, to ensure the key is handled before you check a component's state.
   *
   * TODO: We should consider having these events be generated by {@link #press(Key)}, but as the mapping
   *       between key presses and typed characters isn't straightforward in some cases (the simplest being
   *       typing capital letters and a more complex example being the generation of e.g. chinese characters
   *       using an input method) this might be a significant undertaking to do correctly.
   */
  public void type(int keyCode) {
    performDownKeyEvent(keyCode, KeyEvent.KEY_TYPED);
  }

  /**
   * Types the specified key. This is a convenience method for generating {@code KEY_TYPED} events.
   *
   * Unfortunately, it seems like it may take some time for the key event to propagate to its
   * target component. Therefore, you may need to call {@link SwingUtilities#invokeLater(Runnable)}
   * after calling this method, to ensure the key is handled before you check a component's state.
   *
   * TODO: We should consider having these events be generated by {@link #press(Key)}, but as the mapping
   *       between key presses and typed characters isn't straightforward in some cases (the simplest being
   *       typing capital letters and a more complex example being the generation of e.g. chinese characters
   *       using an input method) this might be a significant undertaking to do correctly.
   */
  public void type(@NotNull Key key) {
    type(key.code);
  }

  private void performDownKeyEvent(int keyCode, int event) {
    if (isPressed(keyCode)) {
      throw new IllegalStateException(String.format("Can't press key %s as it's already pressed.", KeyEvent.getKeyText(keyCode)));
    }

    // Dispatch BEFORE adding the key to our list of pressed keys. If it is a modifier key, we
    // don't want it to included in "toModifiersCode" logic called by "dispatchKeyEvent".
    dispatchKeyEvent(event, keyCode);
    if (event == KeyEvent.KEY_PRESSED) {
      myPressedKeys.add(keyCode);
    }
  }

  public int toModifiersCode() {
    int modifiers = 0;
    if (myPressedKeys.contains(KeyEvent.VK_ALT)) {
      modifiers |= InputEvent.ALT_DOWN_MASK;
    }
    if (myPressedKeys.contains(KeyEvent.VK_CONTROL)) {
      modifiers |= InputEvent.CTRL_DOWN_MASK;
    }
    if (myPressedKeys.contains(KeyEvent.VK_ESCAPE) || myPressedKeys.contains(KeyEvent.VK_SHIFT)) {
      modifiers |= InputEvent.SHIFT_DOWN_MASK;
    }
    if (myPressedKeys.contains(KeyEvent.VK_META)) {
      modifiers |= InputEvent.META_DOWN_MASK;
    }
    return modifiers;
  }

  private void dispatchKeyEvent(int eventType, int keyCode) {
    if (myFocus == null) {
      return;
    }

    //noinspection MagicConstant toModifiersCode returns correct magic number type
    KeyEvent event = new KeyEvent(myFocus, eventType, System.nanoTime(), toModifiersCode(),
                                  eventType == KeyEvent.KEY_TYPED ? KeyEvent.VK_UNDEFINED : keyCode, (char)keyCode);

    // If you use myFocus.dispatchEvent(), the event goes through a flow which gives other systems
    // a chance to handle it first. The following approach bypasses the event queue and sends the
    // event to listeners, directly.
    KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(myFocus, event);
  }

  public enum Key {
    ALT(KeyEvent.VK_ALT),
    BACKSPACE(KeyEvent.VK_BACK_SPACE),
    CTRL(KeyEvent.VK_CONTROL),
    DELETE(KeyEvent.VK_DELETE),
    ENTER(KeyEvent.VK_ENTER),
    ESC(KeyEvent.VK_ESCAPE),
    LEFT(KeyEvent.VK_LEFT),
    META(KeyEvent.VK_META),
    PAGE_DOWN(KeyEvent.VK_PAGE_DOWN),
    PAGE_UP(KeyEvent.VK_PAGE_UP),
    RIGHT(KeyEvent.VK_RIGHT),
    SHIFT(KeyEvent.VK_SHIFT),
    SPACE(KeyEvent.VK_SPACE),
    TAB(KeyEvent.VK_TAB),
    // Add more modifier keys here (alphabetically) as necessary
    A(KeyEvent.VK_A),
    D(KeyEvent.VK_D),
    S(KeyEvent.VK_S),
    W(KeyEvent.VK_W);
    // Add more simple keys here (alphabetically) as necessary

    final int code;

    Key(int code) {
      this.code = code;
    }
  }
}
