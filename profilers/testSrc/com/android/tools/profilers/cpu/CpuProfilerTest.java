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
package com.android.tools.profilers.cpu;

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.TransportService;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.idea.transport.faketransport.TransportServiceTestImpl;
import com.android.tools.idea.transport.faketransport.commands.StopTrace;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.sessions.SessionsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.testFramework.ServiceContainerUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class CpuProfilerTest {
  private static final int FAKE_PID = 1234;
  private static final Common.Session FAKE_SESSION = Common.Session.newBuilder().setSessionId(4321).setPid(FAKE_PID).build();

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myTransportService = new FakeTransportService(myTimer);

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("CpuProfilerTest", myTransportService);

  @Rule public final ExpectedException myExpectedException = ExpectedException.none();
  @Rule public final ApplicationRule myApplicationRule = new ApplicationRule();

  private CpuProfiler myCpuProfiler;

  private StudioProfilers myProfilers;

  @Before
  public void setUp() {
    ServiceContainerUtil.registerServiceInstance(ApplicationManager.getApplication(), TransportService.class,
                                                 new TransportServiceTestImpl(myTransportService));
    FakeIdeProfilerServices ideServices = new FakeIdeProfilerServices();
    myProfilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), ideServices, myTimer);
  }

  @Test
  public void stopMonitoringStopsOngoingTraces() {
    myCpuProfiler = new CpuProfiler(myProfilers);

    myCpuProfiler.stopProfiling(FAKE_SESSION);
    StopTrace stopCpuTrace = (StopTrace)myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_TRACE);
    assertThat(stopCpuTrace.getLastTraceInfo()).isEqualTo(Trace.TraceInfo.getDefaultInstance());

    myTransportService.addEventToStream(
      FAKE_SESSION.getStreamId(),
      Common.Event.newBuilder()
        .setTimestamp(1).setGroupId(1).setPid(FAKE_SESSION.getPid()).setKind(Common.Event.Kind.CPU_TRACE)
        .setTraceData(Trace.TraceData.newBuilder().setTraceStarted(
          Trace.TraceData.TraceStarted.newBuilder().setTraceInfo(Trace.TraceInfo.newBuilder().setTraceId(1).setToTimestamp(-1))))
        .build());
    myCpuProfiler.stopProfiling(FAKE_SESSION);
    assertThat(stopCpuTrace.getLastTraceInfo()).isNotEqualTo(Trace.TraceInfo.getDefaultInstance());
  }

  @Test
  public void importedSessionListenerShouldBeRegistered() {
    myCpuProfiler = new CpuProfiler(myProfilers);
    File trace = CpuProfilerTestUtils.getTraceFile("valid_trace.trace");
    SessionsManager sessionsManager = myProfilers.getSessionsManager();

    // Importing a session from a trace file should select a Common.SessionMetaData.SessionType.CPU_CAPTURE session
    assertThat(sessionsManager.importSessionFromFile(trace)).isTrue();

    // Verify that CpuProfilerStage is open in Import trace mode
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myProfilers.getStage()).isInstanceOf(CpuCaptureStage.class);
  }

  @Test
  public void referenceToTraceFilesAreSavedPerSession() throws IOException {
    myCpuProfiler = new CpuProfiler(myProfilers);
    File trace1 = CpuProfilerTestUtils.getTraceFile("valid_trace.trace");
    SessionsManager sessionsManager = myProfilers.getSessionsManager();
    sessionsManager.importSessionFromFile(trace1);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Common.Session session1 = sessionsManager.getSelectedSession();

    File trace2 = CpuProfilerTestUtils.getTraceFile("art_non_streaming.trace");
    sessionsManager.importSessionFromFile(trace2);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Common.Session session2 = sessionsManager.getSelectedSession();

    // Session 1 references trace 1.
    ByteString session1Bytes = myProfilers.getClient().getTransportClient().getBytes(
      Transport.BytesRequest.newBuilder()
        .setStreamId(session1.getStreamId())
        .setId(String.valueOf(session1.getStartTimestamp()))
        .build()
    ).getContents();
    ByteString trace1Bytes = ByteString.copyFrom(Files.readAllBytes(trace1.toPath()));
    assertThat(session1Bytes).isEqualTo(trace1Bytes);

    // Session 2 references trace 2.
    ByteString session2Bytes = myProfilers.getClient().getTransportClient().getBytes(
      Transport.BytesRequest.newBuilder()
        .setStreamId(session2.getStreamId())
        .setId(String.valueOf(session2.getStartTimestamp()))
        .build()
    ).getContents();
    ByteString trace2Bytes = ByteString.copyFrom(Files.readAllBytes(trace2.toPath()));
    assertThat(session2Bytes).isEqualTo(trace2Bytes);
  }

  @Test
  public void testGetTraceInfoFromSession() {
    Common.Session session = myProfilers.getSession();

    // Insert a completed CpuTraceInfo.
    Trace.TraceInfo info1 = Trace.TraceInfo.newBuilder()
      .setTraceId(1).setFromTimestamp(1).setToTimestamp(2)
      .setStartStatus(Trace.TraceStartStatus.newBuilder().setStatus(Trace.TraceStartStatus.Status.SUCCESS))
      .setStopStatus(Trace.TraceStopStatus.newBuilder().setStatus(Trace.TraceStopStatus.Status.SUCCESS))
      .build();
    myTransportService.addEventToStream(
      session.getStreamId(),
      Common.Event.newBuilder().setGroupId(1).setPid(session.getPid())
        .setIsEnded(true).setKind(Common.Event.Kind.CPU_TRACE).setTimestamp(1)
        .setTraceData(Trace.TraceData.newBuilder().setTraceEnded(Trace.TraceData.TraceEnded.newBuilder().setTraceInfo(info1))).build());

    List<Trace.TraceInfo> infos = CpuProfiler.getTraceInfoFromSession(myProfilers.getClient(), session);
    assertThat(infos).containsExactly(info1);

    // Insert a not yet completed info followed up by a generic end event.
    Trace.TraceInfo info2 = Trace.TraceInfo.newBuilder()
      .setTraceId(5).setFromTimestamp(5).setToTimestamp(-1)
      .setStartStatus(Trace.TraceStartStatus.newBuilder().setStatus(Trace.TraceStartStatus.Status.SUCCESS))
      .build();
    myTransportService.addEventToStream(
      session.getStreamId(),
      Common.Event.newBuilder().setGroupId(5).setPid(session.getPid()).setKind(Common.Event.Kind.CPU_TRACE).setTimestamp(5)
        .setTraceData(Trace.TraceData.newBuilder().setTraceStarted(Trace.TraceData.TraceStarted.newBuilder().setTraceInfo(info2)))
        .build());
    myTransportService.addEventToStream(
      session.getStreamId(),
      Common.Event.newBuilder()
        .setTimestamp(10).setGroupId(5).setKind(Common.Event.Kind.CPU_TRACE).setPid(session.getPid()).setIsEnded(true).build());
    infos = CpuProfiler.getTraceInfoFromSession(myProfilers.getClient(), session);
    assertThat(infos)
      .containsExactly(info1, info2.toBuilder().setToTimestamp(session.getEndTimestamp())
        .setStopStatus(Trace.TraceStopStatus.newBuilder().setStatus(Trace.TraceStopStatus.Status.APP_PROCESS_DIED)).build());
  }

  @Test
  public void testGetTraceStatusEventFromId() {
    Common.Session session = myProfilers.getSession();
    int TRACE_ID = 123;

    // Insert a start status.
    Trace.TraceStatusData status1 = Trace.TraceStatusData.newBuilder()
      .setTraceStartStatus(Trace.TraceStartStatus.newBuilder().setStatus(Trace.TraceStartStatus.Status.SUCCESS))
      .build();
    Common.Event event1 =
      Common.Event.newBuilder().setGroupId(TRACE_ID).setPid(session.getPid()).setKind(Common.Event.Kind.TRACE_STATUS).setTimestamp(1)
        .setTraceStatus(status1).build();
    myTransportService.addEventToStream(session.getStreamId(), event1);

    Common.Event event = CpuProfiler.getTraceStatusEventFromId(myProfilers, TRACE_ID);
    assertThat(event).isEqualTo(event1);

    // Insert a stop status.
    Trace.TraceStatusData status2 = Trace.TraceStatusData.newBuilder()
      .setTraceStopStatus(Trace.TraceStopStatus.newBuilder().setStatus(Trace.TraceStopStatus.Status.WAIT_TIMEOUT).setErrorMessage("error"))
      .build();
    Common.Event event2 =
      Common.Event.newBuilder().setGroupId(TRACE_ID).setPid(session.getPid()).setKind(Common.Event.Kind.TRACE_STATUS).setTimestamp(5)
        .setTraceStatus(status2).build();
    myTransportService.addEventToStream(session.getStreamId(), event2);
    // Insert an event from another TRACE_ID.
    Common.Event event3 = event2.toBuilder().setGroupId(TRACE_ID + 100).build();
    myTransportService.addEventToStream(session.getStreamId(), event3);

    event = CpuProfiler.getTraceStatusEventFromId(myProfilers, TRACE_ID);
    assertThat(event).isEqualTo(event2);
    assertThat(event).isNotEqualTo(event3);
  }

  @Test
  public void reimportTraceShouldSelectSameSession() {
    myCpuProfiler = new CpuProfiler(myProfilers);
    File trace = CpuProfilerTestUtils.getTraceFile("valid_trace.trace");

    SessionsManager sessionsManager = myProfilers.getSessionsManager();
    // Imported session's start time should be equal to the imported trace file creation time
    sessionsManager.importSessionFromFile(trace);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    Common.Session session = sessionsManager.getSelectedSession();

    // Create and select a different session.
    sessionsManager.beginSession(123, FAKE_DEVICE, FAKE_PROCESS);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(sessionsManager.getSelectedSession()).isNotEqualTo(session);

    // Reimport again should select the existing session.
    sessionsManager.importSessionFromFile(trace);
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(sessionsManager.getSelectedSession()).isEqualTo(session);
  }
}
