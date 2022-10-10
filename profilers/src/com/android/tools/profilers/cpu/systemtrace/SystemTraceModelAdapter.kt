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
package com.android.tools.profilers.cpu.systemtrace

import com.android.tools.adtui.model.SeriesData
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profiler.proto.Cpu
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.cpu.ThreadState
import perfetto.protos.PerfettoTrace
import java.io.Serializable
import java.util.SortedMap

/**
 * SystemTraceModelAdapter exposes a common API for accessing the raw model data from system trace
 * captures.
 *
 * This should be used in order to compute data series and nodes that we will display in the UI.
 */
interface SystemTraceModelAdapter {

  fun getCaptureStartTimestampUs(): Long
  fun getCaptureEndTimestampUs(): Long

  fun getProcessById(id: Int): ProcessModel?
  fun getProcesses(): List<ProcessModel>

  /**
   * @return a ThreadModel if we have information for a possible dangling thread with that thread id,
   * which is a thread that we don't have the information about which process they belong to.
   */
  fun getDanglingThread(tid: Int): ThreadModel?

  fun getCpuCores(): List<CpuCoreModel>

  fun getSystemTraceTechnology(): Trace.UserOptions.TraceType

  /**
   * @return true if there is potentially missing data from the capture.
   * It's hard to guarantee if data is missing or not, so this is a best guess.
   */
  fun isCapturePossibleCorrupted(): Boolean

  /**
   * @return Android frame events by layer. Supported since Android R.
   */
  fun getAndroidFrameLayers(): List<TraceProcessor.AndroidFrameEventsResult.Layer>

  /**
   * @return Android FrameTimeline events for jank detection. Supported since Android S.
   */
  fun getAndroidFrameTimelineEvents(): List<AndroidFrameTimelineEvent>
}

data class ProcessModel(
  val id: Int,
  val name: String,
  val threadById: Map<Int, ThreadModel>,
  val counterByName: Map<String, CounterModel>): Serializable {

  companion object {
    // generated by serialvar
    @JvmStatic
    val serialVersionUID = -7303268805551316288L
  }

  fun getMainThread(): ThreadModel? = threadById[id]
  fun getThreads() = threadById.values

  /**
   * Returns the best assumed name for a process.
   * If the process does not have a name it looks at the name of the main thread, but if we also
   * have no information on the main thread it returns "<PID>" instead.
   */
  fun getSafeProcessName(): String {
    if (name.isNotBlank() && !name.startsWith("<")) {
      return name
    }

    // Fallback to the main thread name
    val mainThreadName = getMainThread()?.name ?: ""
    return if (mainThreadName.isNotBlank()) {
      mainThreadName
    } else {
      "<$id>"
    }
  }
}

data class ThreadModel(
  val id: Int,
  val tgid: Int,
  val name: String,
  val traceEvents: List<TraceEventModel>,
  val schedulingEvents: List<SchedulingEventModel>): Serializable {

  companion object {
    // generated by serialvar
    @JvmStatic
    val serialVersionUID = -172242225241232844L
  }
}

data class TraceEventModel (
  val name: String,
  val startTimestampUs: Long,
  val endTimestampUs: Long,
  val cpuTimeUs: Long,
  val childrenEvents: List<TraceEventModel>): Serializable {

  companion object {
    // generated by serialvar
    @JvmStatic
    val serialVersionUID = -2496458872098350643L
  }
}

data class SchedulingEventModel(
  val state: ThreadState,
  val startTimestampUs: Long,
  val endTimestampUs: Long,
  val durationUs: Long,
  val cpuTimeUs: Long,
  val processId: Int,
  val threadId: Int,
  val core: Int): Serializable {

  companion object {
    // generated by serialvar
    @JvmStatic
    val serialVersionUID = -2824580527083393668L
  }
}

data class CounterModel(
  val name: String,
  val valuesByTimestampUs: SortedMap<Long, Double>): Serializable {

  companion object {
    // generated by serialvar
    @JvmStatic
    val serialVersionUID = -305070684614208809L
  }
}

data class CpuCoreModel(
  val id: Int,
  val schedulingEvents: List<SchedulingEventModel>,
  val countersMap: Map<String, CounterModel>) : Serializable {

  companion object {
    // generated by serialvar
    @JvmStatic
    val serialVersionUID = 8233672032802842718L
  }
}

/**
 * @param appJankType Raw data may contain multiple jank types but we only extract the app jank type, namely "App Deadline Missed",
 *                    "Buffer Stuffing" and "Unknown Jank". If no jank is present, the value is "None". If no app jank is present, the value
 *                    is "Unspecified".
 */
data class AndroidFrameTimelineEvent(
  val displayFrameToken: Long,
  val surfaceFrameToken: Long,
  val expectedStartUs: Long,
  val expectedEndUs: Long,
  val actualEndUs: Long,
  val layerName: String,
  val presentType: PerfettoTrace.FrameTimelineEvent.PresentType,
  val appJankType: PerfettoTrace.FrameTimelineEvent.JankType,
  val onTimeFinish: Boolean,
  val gpuComposition: Boolean,
  val layoutDepth: Int
) {
  val expectedDurationUs get() = expectedEndUs - expectedStartUs
  val actualDurationUs get() = actualEndUs - expectedStartUs
  val isJank get() = appJankType != PerfettoTrace.FrameTimelineEvent.JankType.JANK_NONE
  val isActionableJank get() = appJankType == PerfettoTrace.FrameTimelineEvent.JankType.JANK_APP_DEADLINE_MISSED
}

/**
 * Given a list of events X with starts and ends, return a list of padded events SeriesData<Y>,
 * where Y subsumes X and padding values.
 * @param data injects source's event type to target's event type
 * @param pad makes padding event with start and end
 */
fun<X,Y> Iterable<X>.padded(start: (X) -> Long, end: (X) -> Long, data: (X) -> Y, pad: (Long, Long) -> Y): List<SeriesData<Y>> =
  mutableListOf<SeriesData<Y>>().also { paddedEvents ->
    var lastEnd = 0L
    forEach { event ->
      val t = start(event)
      if (lastEnd < t) paddedEvents.add(SeriesData(lastEnd, pad(lastEnd, t))) // add pad if there's gap between events
      lastEnd = end(event)
      paddedEvents.add(SeriesData(t, data(event))) // add real event
    }
    // Add another padding to properly end last event.
    if (lastEnd < Long.MAX_VALUE) {
      paddedEvents.add(SeriesData(lastEnd, pad(lastEnd, Long.MAX_VALUE)))
    }
  }