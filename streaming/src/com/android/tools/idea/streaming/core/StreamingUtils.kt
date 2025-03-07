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
package com.android.tools.idea.streaming.core

import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.repository.targets.SystemImage
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionHolder
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.SameThreadExecutor
import icons.StudioIcons
import kotlinx.coroutines.cancelFutureOnCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.Icon
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Coroutine-friendly version of [ListenableFuture.get].
 */
suspend fun <T> ListenableFuture<T>.suspendingGet(): T {
  if (isDone) {
    @Suppress("BlockingMethodInNonBlockingContext")
    return get()
  }

  return suspendCancellableCoroutine { continuation ->
    continuation.cancelFutureOnCancellation(this)
    val listener = Runnable {
      val value = try {
        get()
      }
      catch (e: Throwable) {
        continuation.resumeWithException(e)
        return@Runnable
      }
      continuation.resume(value)
    }
    addListener(listener, SameThreadExecutor.INSTANCE)
  }
}

/**
 * If this [AnActionEvent] is associated with an [ActionButtonComponent], returns that component.
 * Otherwise, returns the first found component associated with the given action belonging to
 * the Running Devices tool window.
 */
internal fun AnActionEvent.findComponentForAction(action: AnAction): Component? =
    findComponentForAction(action, RUNNING_DEVICES_TOOL_WINDOW_ID)

/**
 * If this [AnActionEvent] is associated with an [ActionButtonComponent], returns that component.
 * Otherwise, returns the first found component associated with the given action belonging to
 * the given tool window.
 */
private fun AnActionEvent.findComponentForAction(action: AnAction, toolWindowId: String): Component? {
  val project = project ?: return null
  val component = inputEvent?.component
  if (component is ActionButtonComponent) {
    return component
  }
  val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId) ?: return null
  return toolWindow.component.parent?.findComponentForAction(action)
}

/**
 * Searches the AWT component tree starting from this [Component] for a component that is
 * an [AnActionHolder] and holds the given action.
 */
private fun Component.findComponentForAction(action: AnAction): Component? {
  if (this.isComponentForAction(action)) {
    return this
  }
  if (this is Container) {
    val queue = ArrayDeque<Container>().also { it.add(this) }
    while (queue.isNotEmpty()) {
      for (child in queue.removeFirst().components) {
        if (child.isComponentForAction(action)) {
          return child
        }
        if (child is Container) {
          queue.add(child)
        }
      }
    }
  }
  return null
}

private fun Component.isComponentForAction(action: AnAction): Boolean =
    this is AnActionHolder && this.action === action

internal inline fun <reified T : Component> Component.findContainingComponent(): T? {
  var component = parent
  while (component != null) {
    if (component is T) {
      return component
    }
    component = component.parent
  }
  return null
}

// TODO(b/289230363): use DeviceHandle.state.properties.icon, since it is the source of truth for device icons.
internal val AvdInfo.icon: Icon
  get() {
    return when (tag) {
      SystemImage.ANDROID_TV_TAG, SystemImage.GOOGLE_TV_TAG -> StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV
      SystemImage.AUTOMOTIVE_TAG, SystemImage.AUTOMOTIVE_PLAY_STORE_TAG -> StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_CAR
      SystemImage.WEAR_TAG -> StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR
      else -> StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE
    }
  }

/**
 * If the device is connected by WiFi, extracts the device's own serial number from the serial
 * number returned by ADB, otherwise returns the original serial number. For example, converts
 * "adb-3211105H802MQD-wG1oxA._adb-tls-connect._tcp." to "3211105H802MQD".
 */
fun normalizeDeviceSerialNumber(serialNumber: String) : String {
  val prefix = "adb-"
  if (serialNumber.startsWith(prefix)) {
    val end = serialNumber.indexOf('-', prefix.length)
    return if (end > prefix.length) serialNumber.substring(prefix.length, end) else serialNumber.substring(prefix.length)
  }
  return serialNumber
}

/**
 * Returns this integer scaled and rounded to the closest integer.
 *
 * @param scale the scale factor
 */
internal fun Int.scaled(scale: Double): Int =
  (this * scale).roundToInt()

/**
 * Returns this integer scaled and rounded down towards zero.
 *
 * @param scale the scale factor
 */
internal fun Int.scaledDown(scale: Double): Int =
  (this * scale).toInt()

/**
 * Returns this integer scaled and rounded up away from zero.
 *
 * @param scale the scale factor
 */
internal fun Int.scaledUp(scale: Double): Int =
  ceil(this * scale).roundToInt()

/**
 * Returns this [Dimension] scaled by the given factor.
 */
internal fun Dimension.scaled(scale: Double): Dimension {
  return if (scale == 1.0) this else Dimension(width.scaled(scale), height.scaled(scale))
}

/**
 * Returns this [Dimension] scaled independently along X and Y axes.
 */
internal fun Dimension.scaled(scaleX: Double, scaleY: Double): Dimension {
  return if (scaleX == 1.0 && scaleY == 1.0) this else Dimension(width.scaled(scaleX), height.scaled(scaleY))
}

/**
 * Returns this [Point] scaled by the given factor.
 */
internal fun Point.scaled(scale: Double): Point {
  return if (scale == 1.0) this else Point(x.scaled(scale), y.scaled(scale))
}

/**
 * Returns this integer scaled by multiplying by [numerator] and then dividing by [denominator].
 */
internal fun Int.scaledDown(numerator: Int, denominator: Int): Int =
  ((this.toLong() * numerator) / denominator).toInt()

/**
 * Converts this value from the `[0, fromRange-1]` interval to the `[0, toRange - 1]`interval by scaling by
 * the [toRange]/[fromRange] factor while maintaining symmetry with respect to the centers of the two intervals.
 *
 * The conversion is reversible, i.e. if `fromRange <= toRange`, then for every `i` in the `[0, fromRange-1]`
 * interval `i.scaledUnbiased(fromRange, toRange).scaledUnbiased(toRange, fromRange) = i`.
 */
internal fun Int.scaledUnbiased(fromRange: Int, toRange: Int): Int =
  ((this * 2L + 1) * toRange / (2 * fromRange)).toInt()

internal fun Point.scaledUnbiased(fromDim: Dimension, toDim: Dimension): Point =
  Point(x.scaledUnbiased(fromDim.width, toDim.width), y.scaledUnbiased(fromDim.height, toDim.height))

/**
 * Checks if the ratio between [width1] and [height1] is the same as the ratio between
 * [width2] and [height2] within the given relative [tolerance].
 */
internal fun isSameAspectRatio(width1: Int, height1: Int, width2: Int, height2: Int, tolerance: Double): Boolean {
  val a = width1.toDouble() * height2
  val b = width2.toDouble() * height1
  val d = a - b
  return abs(d) <= tolerance * abs(a + b) / 2
}

/**
 * Returns this [Dimension] rotated by [numQuadrants] quadrants.
 */
internal fun Dimension.rotatedByQuadrants(numQuadrants: Int): Dimension {
  return if (numQuadrants % 2 == 0) this else Dimension(height, width)
}

/**
 * Returns this [Point] rotated according to [rotation].
 */
internal fun Point.rotatedByQuadrants(rotation: Int): Point {
  return when (rotation and 0x3) { // True modulus
    1 -> Point(y, -x)
    2 -> Point(-x, -y)
    3 -> Point(-y, x)
    else -> this
  }
}

/**
 * Returns this Dimension if both its components are not greater than the [maximumValue], otherwise
 * returns this Dimension scaled down to satisfy this requirement while preserving the aspect ratio.
 */
internal fun Dimension.coerceAtMost(maximumValue: Dimension): Dimension {
  if (width <= maximumValue.width && height <= maximumValue.height) {
    return this
  }
  val scale = min(maximumValue.width.toDouble() / width, maximumValue.height.toDouble() / height).coerceAtMost(1.0)
  return Dimension(width.scaled(scale).coerceAtMost(maximumValue.width), height.scaled(scale).coerceAtMost(maximumValue.height))
}

internal val Container.sizeWithoutInsets: Dimension
  get() = Dimension(width - insets.left - insets.right.coerceAtLeast(0),
                    height - insets.top - insets.bottom.coerceAtLeast(0))

internal fun Point.constrainInside(d: Dimension) =
  if (this in d) this else Point(x.coerceIn(0, d.width - 1), y.coerceIn(0, d.height - 1))

internal operator fun Dimension.contains(p: Point) = p.x in 0 until width && p.y in 0 until height

internal val Rectangle.right: Int
  get() = x + width

internal val Rectangle.bottom: Int
  get() = y + height

internal val MouseEvent.location: Point
  get() = Point(x, y)

/** Wraps the string with &lt;font color=...>, &lt;/font> tags. */
internal fun String.htmlColored(color: Color): String =
    "<font color=${(color.rgb and 0xFFFFFF).toString(16)}>$this</font>"
