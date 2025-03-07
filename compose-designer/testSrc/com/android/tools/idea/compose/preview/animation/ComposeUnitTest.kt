/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import androidx.compose.animation.tooling.ComposeAnimatedProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import kotlin.test.assertNotNull

class ComposeUnitTest {

  @Test
  fun parseInt() {
    val composeUnit = ComposeUnit.parse(ComposeAnimatedProperty("", 1))
    assertNotNull(composeUnit)
    composeUnit as ComposeUnit.Unit1D<*>
    assertEquals(1, composeUnit.component1)
    assertEquals(listOf(1), composeUnit.components)
    assertEquals("1", composeUnit.toString(0))
    assertEquals("1", composeUnit.toString())
    assertFalse(composeUnit is ComposeUnit.Unit2D<*>)
  }

  @Test
  fun parseIntUnit() {
    val parsed = ComposeUnit.IntUnit(1).parseUnit { "2" }!!
    assertEquals(listOf(2), parsed.components)
  }

  @Test
  fun parseInvalidIntUnit() {
    assertNull(ComposeUnit.IntUnit(1).parseUnit { "2.3" })
    assertNull(ComposeUnit.IntUnit(1).parseUnit { "hello" })
    assertNull(ComposeUnit.IntUnit(1).parseUnit { "true" })
    assertNull(ComposeUnit.IntUnit(1).parseUnit { "2." })
    assertNull(ComposeUnit.IntUnit(1).parseUnit { "2f" })
  }

  @Test
  fun parseDouble() {
    val composeUnit = ComposeUnit.parse(ComposeAnimatedProperty("", 1.2345))
    assertNotNull(composeUnit)
    composeUnit as ComposeUnit.Unit1D<*>
    assertEquals(1.2345, composeUnit.component1)
    assertEquals(listOf(1.2345), composeUnit.components)
    assertEquals("1.2345", composeUnit.toString(0))
    assertEquals("1.2345", composeUnit.toString())
    assertFalse(composeUnit is ComposeUnit.Unit2D<*>)
  }

  @Test
  fun parseDoubleUnit() {
    val parsed = ComposeUnit.DoubleUnit(1.0).parseUnit { "2" }!!
    assertEquals(listOf(2.0), parsed.components)
  }

  @Test
  fun parseInvalidDoubleUnit() {
    assertNull(ComposeUnit.DoubleUnit(1.0).parseUnit { "2L" })
    assertNull(ComposeUnit.DoubleUnit(1.0).parseUnit { "hello" })
    assertNull(ComposeUnit.DoubleUnit(1.0).parseUnit { "true" })
  }

  @Test
  fun parseFloat() {
    val composeUnit = ComposeUnit.parse(ComposeAnimatedProperty("", 1.2345f))
    assertNotNull(composeUnit)
    composeUnit as ComposeUnit.Unit1D<*>
    assertEquals(1.2345f, composeUnit.component1)
    assertEquals(listOf(1.2345f), composeUnit.components)
    assertEquals("1.2345", composeUnit.toString(0))
    assertEquals("1.2345", composeUnit.toString())
    assertFalse(composeUnit is ComposeUnit.Unit2D<*>)
  }

  @Test
  fun parseFloatUnit() {
    val parsed = ComposeUnit.FloatUnit(1f).parseUnit { "2" }!!
    assertEquals(listOf(2f), parsed.components)
  }

  @Test
  fun parseInvalidFloatUnit() {
    assertNull(ComposeUnit.FloatUnit(1f).parseUnit { "2L" })
    assertNull(ComposeUnit.FloatUnit(1f).parseUnit { "hello" })
    assertNull(ComposeUnit.FloatUnit(1f).parseUnit { "true" })
  }

  @Test
  fun parseDp() {
    @Suppress("unused") // Methods are called via reflection by tests.
    class Dp {
      fun getValue() = 1.2345f
    }

    val composeUnit = ComposeUnit.Dp.create(Dp())
    assertNotNull(composeUnit)
    assertEquals(1.2345f, composeUnit.component1)
    assertEquals("1.2345dp", composeUnit.toString(0))
    assertEquals("1.2345dp", composeUnit.toString())
    assertEquals(listOf(1.2345f), composeUnit.components)
  }

  @Test
  fun parseDpUnit() {
    val parsed = ComposeUnit.Dp(1f).parseUnit { "2" }!!
    assertEquals(listOf(2f), parsed.components)
  }

  @Test
  fun parseInvalidDpUnit() {
    assertNull(ComposeUnit.Dp(1f).parseUnit { "2L" })
    assertNull(ComposeUnit.Dp(1f).parseUnit { "hello" })
    assertNull(ComposeUnit.Dp(1f).parseUnit { "true" })
  }

  @Suppress("unused") // Methods are called via reflection by tests.
  class ValidIntOffset {
    fun `unbox-impl`() = 0L

    companion object {
      @JvmStatic fun `getX-impl`(impl: Long) = 1

      @JvmStatic fun `getY-impl`(impl: Long) = 2
    }
  }

  @Test
  fun parseIntOffset() {
    val composeUnit = ComposeUnit.IntOffset.create(ValidIntOffset())
    assertNotNull(composeUnit)
    assertEquals(1, composeUnit.component1)
    assertEquals(2, composeUnit.component2)
    assertEquals("x ( 1 , _ )", composeUnit.toString(0))
    assertEquals("y ( _ , 2 )", composeUnit.toString(1))
    assertEquals("( 1 , 2 )", composeUnit.toString())
    assertEquals(listOf(1, 2), composeUnit.components)
  }

  @Test
  fun parseIntOffsetUnit() {
    val getValue: (Int) -> String = {
      when (it) {
        0 -> "23"
        1 -> "-23"
        else -> "-100"
      }
    }
    val parsed = ComposeUnit.IntOffset(1, 1).parseUnit(getValue)!!
    assertEquals(listOf(23, -23), parsed.components)
  }

  @Test
  fun parseInvalidIntOffsetUnit() {
    assertNull(ComposeUnit.IntOffset(1, 1).parseUnit { "2.3" })
    assertNull(ComposeUnit.IntOffset(1, 1).parseUnit { "hello" })
    assertNull(ComposeUnit.IntOffset(1, 1).parseUnit { "true" })
    assertNull(ComposeUnit.IntOffset(1, 1).parseUnit { "2." })
    assertNull(ComposeUnit.IntOffset(1, 1).parseUnit { "2f" })
  }

  @Suppress("unused") // Methods are called via reflection by tests.
  class ValidIntSize {
    fun `unbox-impl`() = 0L

    companion object {
      @JvmStatic fun `getWidth-impl`(impl: Long) = 1

      @JvmStatic fun `getHeight-impl`(impl: Long) = 2
    }
  }

  @Test
  fun parseIntSize() {
    val composeUnit = ComposeUnit.IntSize.create(ValidIntSize())
    assertNotNull(composeUnit)
    assertEquals(1, composeUnit.component1)
    assertEquals(2, composeUnit.component2)
    assertEquals("width ( 1 , _ )", composeUnit.toString(0))
    assertEquals("height ( _ , 2 )", composeUnit.toString(1))
    assertEquals("( 1 , 2 )", composeUnit.toString())
    assertEquals(listOf(1, 2), composeUnit.components)
  }

  @Test
  fun parseIntSizeUnit() {
    val getValue: (Int) -> String = {
      when (it) {
        0 -> "23"
        1 -> "-23"
        else -> "-100"
      }
    }
    val parsed = ComposeUnit.IntSize(1, 1).parseUnit(getValue)!!
    assertEquals(listOf(23, -23), parsed.components)
  }

  @Test
  fun parseInvalidIntSizeUnit() {
    assertNull(ComposeUnit.IntSize(1, 1).parseUnit { "2.3" })
    assertNull(ComposeUnit.IntSize(1, 1).parseUnit { "hello" })
    assertNull(ComposeUnit.IntSize(1, 1).parseUnit { "true" })
    assertNull(ComposeUnit.IntSize(1, 1).parseUnit { "2." })
    assertNull(ComposeUnit.IntSize(1, 1).parseUnit { "2f" })
  }

  @Suppress("unused") // Methods are called via reflection by tests.
  class ValidSize {
    fun `unbox-impl`() = 0L

    companion object {
      @JvmStatic fun `getWidth-impl`(impl: Long) = 1.1f

      @JvmStatic fun `getHeight-impl`(impl: Long) = 2.2f
    }
  }

  @Test
  fun parseSize() {
    val composeUnit = ComposeUnit.Size.create(ValidSize())
    assertNotNull(composeUnit)
    assertEquals(1.1f, composeUnit.component1)
    assertEquals(2.2f, composeUnit.component2)
    assertEquals("width ( 1.1 , _ )", composeUnit.toString(0))
    assertEquals("height ( _ , 2.2 )", composeUnit.toString(1))
    assertEquals("( 1.1 , 2.2 )", composeUnit.toString())
    assertEquals(listOf(1.1f, 2.2f), composeUnit.components)
  }

  @Test
  fun parseSizeUnit() {
    val getValue: (Int) -> String = {
      when (it) {
        0 -> "2.3f"
        1 -> "-2.34"
        else -> "-100"
      }
    }
    val parsed = ComposeUnit.Size(1f, 1f).parseUnit(getValue)!!
    assertEquals(listOf(2.3f, -2.34f), parsed.components)
  }

  @Test
  fun parseInvalidSizeUnit() {
    assertNull(ComposeUnit.Size(1f, 1f).parseUnit { "2L" })
    assertNull(ComposeUnit.Size(1f, 1f).parseUnit { "hello" })
    assertNull(ComposeUnit.Size(1f, 1f).parseUnit { "true" })
  }

  @Suppress("unused") // Methods are called via reflection by tests.
  class ValidOffset {
    fun `unbox-impl`() = 0L

    companion object {
      @JvmStatic fun `getX-impl`(impl: Long) = 1.1f

      @JvmStatic fun `getY-impl`(impl: Long) = 2.2f
    }
  }

  @Test
  fun parseOffset() {
    val composeUnit = ComposeUnit.Offset.create(ValidOffset())
    assertNotNull(composeUnit)
    assertEquals(1.1f, composeUnit.component1)
    assertEquals(2.2f, composeUnit.component2)
    assertEquals("x ( 1.1 , _ )", composeUnit.toString(0))
    assertEquals("y ( _ , 2.2 )", composeUnit.toString(1))
    assertEquals("( 1.1 , 2.2 )", composeUnit.toString())
    assertEquals(listOf(1.1f, 2.2f), composeUnit.components)
  }

  @Test
  fun parseOffsetUnit() {
    val getValue: (Int) -> String = {
      when (it) {
        0 -> "2.3f"
        1 -> "-2.34"
        else -> "-100"
      }
    }
    val parsed = ComposeUnit.Offset(1f, 1f).parseUnit(getValue)!!
    assertEquals(listOf(2.3f, -2.34f), parsed.components)
  }

  @Test
  fun parseInvalidOffsetUnit() {
    assertNull(ComposeUnit.Offset(1f, 1f).parseUnit { "2L" })
    assertNull(ComposeUnit.Offset(1f, 1f).parseUnit { "hello" })
    assertNull(ComposeUnit.Offset(1f, 1f).parseUnit { "true" })
  }

  @Suppress("unused") // Methods are called via reflection by tests.
  class ValidColor {
    fun `unbox-impl`() = 0L

    companion object {
      @JvmStatic fun `getRed-impl`(impl: Long) = 0.1f

      @JvmStatic fun `getGreen-impl`(impl: Long) = 0.2f

      @JvmStatic fun `getBlue-impl`(impl: Long) = 0.3f

      @JvmStatic fun `getAlpha-impl`(impl: Long) = 0.4f
    }
  }

  @Test
  fun parseColor() {
    val composeUnit = ComposeUnit.Color.create(ValidColor())
    assertNotNull(composeUnit)
    assertEquals(0.1f, composeUnit.component1)
    assertEquals(0.2f, composeUnit.component2)
    assertEquals(0.3f, composeUnit.component3)
    assertEquals(0.4f, composeUnit.component4)
    assertEquals("red ( 0.1 , _ , _ , _ )", composeUnit.toString(0))
    assertEquals("green ( _ , 0.2 , _ , _ )", composeUnit.toString(1))
    assertEquals("blue ( _ , _ , 0.3 , _ )", composeUnit.toString(2))
    assertEquals("alpha ( _ , _ , _ , 0.4 )", composeUnit.toString(3))
    assertEquals("0x1A334D66", composeUnit.toString())
    assertEquals(listOf(0.1f, 0.2f, 0.3f, 0.4f), composeUnit.components)
    assertNotNull(composeUnit.color)
  }

  @Test
  fun parseColorUnit() {
    val getValue: (Int) -> String = {
      when (it) {
        0 -> "0.1"
        1 -> ".2"
        2 -> "0.3f"
        3 -> "1.f"
        else -> "-100"
      }
    }

    val parsed = ComposeUnit.Color(1f, 1f, 1f, 1f).parseUnit(getValue)!!
    assertEquals(listOf(0.1f, 0.2f, 0.3f, 1f), parsed.components)
  }

  @Test
  fun parseInvalidColorUnit() {
    assertNull(ComposeUnit.Color(1f, 1f, 1f, 1f).parseUnit { "2L" })
    assertNull(ComposeUnit.Color(1f, 1f, 1f, 1f).parseUnit { "-0.1f" })
    assertNull(ComposeUnit.Color(1f, 1f, 1f, 1f).parseUnit { "hello" })
    assertNull(ComposeUnit.Color(1f, 1f, 1f, 1f).parseUnit { "true" })
    assertNull(ComposeUnit.Color(1f, 1f, 1f, 1f).parseUnit { "2." })
    assertNull(ComposeUnit.Color(1f, 1f, 1f, 1f).parseUnit { "2f" })
  }

  @Test
  fun parseInvalidDp() {
    @Suppress("unused") // Methods are called via reflection by tests.
    class Dp {
      fun getValue() = 1 // Not a float.
    }

    val composeUnit = ComposeUnit.Dp.create(Dp())
    assertNull(composeUnit)
  }

  @Test
  fun parseInvalidDpWithoutMethod() {
    class Dp

    val composeUnit = ComposeUnit.Dp.create(Dp())
    assertNull(composeUnit)
  }

  @Test
  fun parseRect() {
    @Suppress("unused") // Methods are called via reflection by tests.
    class Rect {
      fun getLeft() = 1.222f
      fun getTop() = 2.222f
      fun getRight() = 3.222f
      fun getBottom() = 4.222f
    }

    val composeUnit = ComposeUnit.Rect.create(Rect())
    assertNotNull(composeUnit)
    assertEquals(1.222f, composeUnit.component1)
    assertEquals(2.222f, composeUnit.component2)
    assertEquals(3.222f, composeUnit.component3)
    assertEquals(4.222f, composeUnit.component4)
    assertEquals("left ( 1.222 , _ , _ , _ )", composeUnit.toString(0))
    assertEquals("top ( _ , 2.222 , _ , _ )", composeUnit.toString(1))
    assertEquals("right ( _ , _ , 3.222 , _ )", composeUnit.toString(2))
    assertEquals("bottom ( _ , _ , _ , 4.222 )", composeUnit.toString(3))
    assertEquals("( 1.222 , 2.222 , 3.222 , 4.222 )", composeUnit.toString())
    assertEquals(listOf(1.222f, 2.222f, 3.222f, 4.222f), composeUnit.components)
  }

  @Test
  fun parseRectUnit() {
    val getValue: (Int) -> String = {
      when (it) {
        0 -> "0.1"
        1 -> "-0.1"
        2 -> "2f"
        3 -> "-3.34f"
        else -> "-100"
      }
    }
    val parsed = ComposeUnit.Rect(1f, 1f, 1f, 1f).parseUnit(getValue)!!
    assertEquals(listOf(0.1f, -0.1f, 2f, -3.34f), parsed.components)
  }

  @Test
  fun parseInvalidRectUnit() {
    assertNull(ComposeUnit.Rect(1f, 1f, 1f, 1f).parseUnit { "2L" })
    assertNull(ComposeUnit.Rect(1f, 1f, 1f, 1f).parseUnit { "hello" })
    assertNull(ComposeUnit.Rect(1f, 1f, 1f, 1f).parseUnit { "true" })
  }

  @Test
  fun parseInvalidRect() {
    @Suppress("unused") // Methods are called via reflection by tests.
    class Rect {
      // Not float values.
      fun getLeft() = 1
      fun getTop() = 2
      fun getRight() = 3
      fun getBottom() = 4
    }

    val composeUnit = ComposeUnit.Rect.create(Rect())
    assertNull(composeUnit)
  }

  @Test
  fun parseInvalidRectWithoutMethods() {
    @Suppress("unused") // Methods are called via reflection by tests.
    class Rect {
      fun getLeft() = 1f
      fun getTop() = 2f
      // No getRight() and getBottom() methods.
    }

    val composeUnit = ComposeUnit.Rect.create(Rect())
    assertNull(composeUnit)
  }

  @Test
  fun parseInvalidIntSize() {
    class IntSize

    val composeUnit = ComposeUnit.IntSize.create(IntSize())
    assertNull(composeUnit)
  }

  @Test
  fun parseInvalidIntOffset() {
    class IntOffset

    val composeUnit = ComposeUnit.IntSize.create(IntOffset())
    assertNull(composeUnit)
  }

  @Test
  fun parseInvalidSize() {
    class Size

    val composeUnit = ComposeUnit.Size.create(Size())
    assertNull(composeUnit)
  }

  @Test
  fun parseInvalidOffset() {
    class Offset

    val composeUnit = ComposeUnit.Offset.create(Offset())
    assertNull(composeUnit)
  }

  @Test
  fun parseInvalidColor() {
    class Color

    val composeUnit = ComposeUnit.Color.create(Color())
    assertNull(composeUnit)
  }

  @Test
  fun parseUnknownProperty() {
    val composeUnit = ComposeUnit.parse(ComposeAnimatedProperty("", "Unknown"))
    assertNotNull(composeUnit)
    assertEquals("Unknown", composeUnit.toString())
    assertEquals("Unknown", composeUnit.toString(1))
    assertEquals(1, composeUnit.components.size)
  }

  @Test
  fun parseString() {
    val unit = ComposeUnit.StringUnit("hello").parseUnit { "summer" }
    assertNotNull(unit)
    assertEquals("summer", unit.component1)
  }

  @Test
  fun parseInvalidString() {
    val unit = ComposeUnit.StringUnit("hello").parseUnit { null }
    assertNull(unit)
  }

  @Test
  fun parseStringUnit() {
    val unit = ComposeUnit.parseStateUnit("winter")
    assertNotNull(unit)
    assertTrue(unit is ComposeUnit.StringUnit)
    assertEquals(listOf("winter"), unit.components)
  }

  @Test
  fun parseUnknown() {
    val unit = ComposeUnit.parseStateUnit(Any())
    assertTrue(unit is ComposeUnit.UnitUnknown)
  }

  @Test
  fun parseUnknownNumber() {
    val unit = ComposeUnit.parseNumberUnit(Any())
    assertTrue(unit is ComposeUnit.UnknownNumberUnit)
  }

  @Test
  fun parseInvalidBooleanUnit() {
    assertNull(ComposeUnit.UnitUnknown(true).parseUnit { "true" })
    assertNull(ComposeUnit.UnitUnknown(true).parseUnit { "1.0" })
    assertNull(ComposeUnit.UnitUnknown(true).parseUnit { "hey" })
  }

  @Test
  fun createValidColor() {
    val composeUnit = ComposeUnit.Color(0.1f, 0.1f, 0.1f, 0.1f)
    assertNotNull(composeUnit)
    assertNotNull(composeUnit.color)
  }

  @Test
  fun createUnitFromColor() {
    val composeUnit = ComposeUnit.Color.create(Color.cyan)
    assertEquals(Color.cyan, composeUnit.color)
  }

  @Test
  fun createInvalidColor() {
    val composeUnit = ComposeUnit.Color(10f, 10f, 10f, 10f)
    assertNotNull(composeUnit)
    assertNull(composeUnit.color)
  }

  @Test
  fun parseNull() {
    val result = ComposeUnit.parseNumberUnit(null)
    assertNull(result)
  }
}
