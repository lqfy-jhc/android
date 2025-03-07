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
package com.android.tools.asdriver.inject;

import com.android.tools.asdriver.proto.ASDriver;
import com.android.tools.asdriver.proto.AndroidStudioGrpc;
import com.android.tools.idea.bleak.BleakCheck;
import com.android.tools.idea.bleak.BleakOptions;
import com.android.tools.idea.bleak.BleakResult;
import com.android.tools.idea.bleak.StudioBleakOptions;
import com.android.tools.idea.io.grpc.Server;
import com.android.tools.idea.io.grpc.ServerBuilder;
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import com.google.common.base.Objects;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.UIUtil;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

public class AndroidStudioService extends AndroidStudioGrpc.AndroidStudioImplBase {

  private static BleakOptions bleakOptions = StudioBleakOptions.getDefaults();

  static public void start() {
    ServerBuilder<?> builder = ServerBuilder.forPort(0);
    builder.addService(new AndroidStudioService());
    Server server = builder.build();

    new Thread(() -> {
      try {
        server.start();
        long pid = ProcessHandle.current().pid();
        System.out.println("as-driver started on pid: " + pid);
        System.out.println("as-driver server listening at: " + server.getPort());
      }
      catch (Throwable t) {
        t.printStackTrace();
      }
    }).start();
  }

  @Override
  public void getVersion(ASDriver.GetVersionRequest request, StreamObserver<ASDriver.GetVersionResponse> responseObserver) {
    ApplicationInfo info = ApplicationInfo.getInstance();
    String version = info.getFullApplicationName() + " @ " + info.getBuild();
    responseObserver.onNext(ASDriver.GetVersionResponse.newBuilder().setVersion(version).build());
    responseObserver.onCompleted();
  }

  @Override
  public void getSystemProperty(ASDriver.GetSystemPropertyRequest request, StreamObserver<ASDriver.GetSystemPropertyResponse> responseObserver) {
    String value = System.getProperty(request.getSystemProperty());
    ASDriver.GetSystemPropertyResponse.Builder builder = ASDriver.GetSystemPropertyResponse.newBuilder();
    if (value != null) {
      builder.setValue(value);
    }

    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void quit(ASDriver.QuitRequest request, StreamObserver<ASDriver.QuitResponse> responseObserver) {
    if (request.getForce()) {
      System.exit(0);
    }
    else {
      ((ApplicationEx)ApplicationManager.getApplication()).exit(true, true);
    }
  }

  @Override
  public void executeAction(ASDriver.ExecuteActionRequest request, StreamObserver<ASDriver.ExecuteActionResponse> responseObserver) {
    ASDriver.ExecuteActionResponse.Builder builder = ASDriver.ExecuteActionResponse.newBuilder();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      AnAction action = ActionManager.getInstance().getAction(request.getActionId());
      if (action == null) {
        builder.setResult(ASDriver.ExecuteActionResponse.Result.ACTION_NOT_FOUND);
        return;
      }

      String projectName = request.hasProjectName() ? request.getProjectName() : null;
      DataContext dataContext = getDataContext(projectName, request.getDataContextSource());
      if (dataContext == null) {
        String errorMessage = "Could not get a DataContext for executeAction.";
        System.err.println(errorMessage);
        builder.setErrorMessage(errorMessage);
        builder.setResult(ASDriver.ExecuteActionResponse.Result.ERROR);
      }
      else {
        AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext);
        ActionUtil.performActionDumbAwareWithCallbacks(action, event);
        builder.setResult(ASDriver.ExecuteActionResponse.Result.OK);
      }
    });
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  /**
   * Gets a {@code DataContext} while taking into account that a project may be open.
   *
   * This is important for {@link AndroidStudioService#executeAction} in particular; actions that
   * are project-specific like MakeGradleProject will silently fail if the {@code DataContext} has
   * no associated project, which is possible in cases where Android Studio never got the focus.
   *
   * For more information, see b/238922776.
   * @param projectName optional project name.
   */
  private DataContext getDataContext(String projectName, ASDriver.ExecuteActionRequest.DataContextSource dataContextSource) {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    int numProjects = projects.length;
    if (numProjects == 0) {
      if (projectName != null) {
        System.err.printf("No projects are open, but a request was received for \"%s\"%n", projectName);
        return null;
      }
      try {
        return DataManager.getInstance().getDataContextFromFocusAsync().blockingGet(30000);
      }
      catch (TimeoutException | ExecutionException e) {
        e.printStackTrace();
        return null;
      }
    }

    Project projectForContext;

    if (numProjects == 1) {
      projectForContext = projects[0];
      // If a projectName was specified, then make sure it matches the project we're about to use.
      if (projectName != null && !Objects.equal(projectForContext.getName(), projectName)) {
        System.err.printf("The only project open is one named \"%s\", but a project name of \"%s\" was requested%n",
                          projectForContext.getName(), projectName);
        return null;
      }
    }
    else {
      // There are two or more projects, so we have to use projectName to find the one to use.
      if (projectName == null) {
        System.err.printf("%d projects are open, but no project name was specified to be able to determine which to use%n", numProjects);
        return null;
      }

      projectForContext = findProjectByName(projectName);
    }

    Editor selectedTextEditor = FileEditorManager.getInstance(projectForContext).getSelectedTextEditor();

    switch (dataContextSource) {
      case SELECTED_TEXT_EDITOR -> {
        if (selectedTextEditor == null) {
          System.err.print("Editor was specified as DataContextSource, but no currently selected text editor was found.");
          return null;
        }
        return DataManager.getInstance().getDataContext(selectedTextEditor.getComponent());
      }
      case DEFAULT -> {
        // Attempting to create a DataContext via DataManager.getInstance.getDataContext(Component c)
        // causes all sorts of strange issues depending on which component is used. If it's a project,
        // then editor-specific actions like ToggleLineBreakpoint won't work. If it's an editor, then
        // the editor has to be showing or else performDumbAwareWithCallbacks will suppress the action.
        //
        // ...so by default, we create our own DataContext rather than getting one from a component.
        MapDataContext dataContext = new MapDataContext();
        dataContext.put(CommonDataKeys.PROJECT, projectForContext);
        dataContext.put(CommonDataKeys.EDITOR, selectedTextEditor);
        return dataContext;
      }
      default -> throw new IllegalArgumentException("Invalid DataContextSource provided with ExecuteActionRequest.");
    }
  }

  @Override
  public void showToolWindow(ASDriver.ShowToolWindowRequest request, StreamObserver<ASDriver.ShowToolWindowResponse> responseObserver) {
    ASDriver.ShowToolWindowResponse.Builder builder = ASDriver.ShowToolWindowResponse.newBuilder();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      boolean found = false;
      for (Project p : ProjectManager.getInstance().getOpenProjects()) {
        found = true;
        ToolWindow window = ToolWindowManagerEx.getInstanceEx(p).getToolWindow(request.getToolWindowId());
        if (window != null) {
          window.show();
          builder.setResult(ASDriver.ShowToolWindowResponse.Result.OK);
        } else {
          builder.setResult(ASDriver.ShowToolWindowResponse.Result.TOOL_WINDOW_NOT_FOUND);
        }
        break;
      }
      if (!found) {
        builder.setResult(ASDriver.ShowToolWindowResponse.Result.PROJECT_NOT_FOUND);
      }
    });
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void invokeComponent(ASDriver.InvokeComponentRequest request, StreamObserver<ASDriver.InvokeComponentResponse> responseObserver) {
    ASDriver.InvokeComponentResponse.Builder builder = ASDriver.InvokeComponentResponse.newBuilder();
    try {
      StudioInteractionService studioInteractionService = new StudioInteractionService();
      studioInteractionService.findAndInvokeComponent(request.getMatchersList());
      builder.setResult(ASDriver.InvokeComponentResponse.Result.OK);
    }
    catch (Exception e) {
      e.printStackTrace();
      builder.setResult(ASDriver.InvokeComponentResponse.Result.ERROR);
      if (!StringUtil.isEmpty(e.getMessage())) {
        builder.setErrorMessage(e.getMessage());
      }
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void waitForIndex(ASDriver.WaitForIndexRequest request, StreamObserver<ASDriver.WaitForIndexResponse> responseObserver) {
    try {
      Project[] projects = ProjectManager.getInstance().getOpenProjects();
      CountDownLatch latch = new CountDownLatch(projects.length);
      for (Project p : projects) {
        DumbService.getInstance(p).smartInvokeLater(latch::countDown);
      }
      latch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    responseObserver.onNext(ASDriver.WaitForIndexResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  private Project findProjectByName(String projectName) {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    Optional<Project> foundProject = Arrays.stream(projects).filter((p) -> Objects.equal(p.getName(), projectName)).findFirst();
    if (foundProject.isEmpty()) {
      throw new NoSuchElementException(
        String.format("No project found by the name \"%s\". Open projects: %s", projectName, Arrays.toString(projects)));
    }
    return foundProject.get();
  }

  @Override
  public void openFile(ASDriver.OpenFileRequest request, StreamObserver<ASDriver.OpenFileResponse> responseObserver) {
    ASDriver.OpenFileResponse.Builder builder = ASDriver.OpenFileResponse.newBuilder();
    builder.setResult(ASDriver.OpenFileResponse.Result.ERROR);
    String projectName = request.getProject();
    String fileName = request.getFile();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        Project project = findProjectByName(projectName);
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(fileName));
        if (virtualFile == null) {
          // Fall back to interpreting the file name as project-relative.
          String basePath = project.getBasePath();
          if (basePath == null) {
            System.err.println("File does not exist on filesystem with path: " + fileName +
                               ". Base path for project \"" + projectName + "\" not found.");
            return;
          }
          File relativeFile = Path.of(basePath, fileName).toFile().getCanonicalFile();
          virtualFile = LocalFileSystem.getInstance().findFileByIoFile(relativeFile);
          if (virtualFile == null) {
            System.err.println("File does not exist on filesystem with any path in: [" + fileName + ", " + relativeFile.getPath() + "]");
            return;
          }
        }

        FileEditorManager manager = FileEditorManager.getInstance(project);
        manager.openTextEditor(new OpenFileDescriptor(project, virtualFile), true);
        goToLine(request.hasLine() ? request.getLine() : null, request.hasColumn() ? request.getColumn() : null);

        builder.setResult(ASDriver.OpenFileResponse.Result.OK);
      }
      catch (Exception e) {
        e.printStackTrace();
        if (!StringUtil.isEmpty(e.getMessage())) {
          builder.setErrorMessage(e.getMessage());
        }
      }
    });

    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void analyzeFile(ASDriver.AnalyzeFileRequest request, StreamObserver<ASDriver.AnalyzeFileResponse> responseObserver) {
    ASDriver.AnalyzeFileResponse.Builder builder = ASDriver.AnalyzeFileResponse.newBuilder();
    builder.setStatus(ASDriver.AnalyzeFileResponse.Status.ERROR);
    String fileNameOnly = request.getFile();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        Project project = getSingleProject();
        File filePath = Path.of(project.getBasePath(), fileNameOnly).toFile().getCanonicalFile();
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(filePath);
        if (virtualFile == null) {
          System.err.println("File does not exist on filesystem with path: " + filePath);
          return;
        }

        FileEditorManager manager = FileEditorManager.getInstance(project);
        Editor editor = manager.openTextEditor(new OpenFileDescriptor(project, virtualFile), true);
        if (editor == null) {
          System.err.println("Could not open an editor with file: " + filePath);
          return;
        }
        Document document = editor.getDocument();

        System.out.println("Creating a TrafficLightRenderer to determine analysis issues of " + filePath);
        Collection<HighlightInfo> highlightInfoList = analyzeViaTrafficLightRenderer(project, document);

        System.out.printf("Found %d analysis result(s)%n", highlightInfoList.size());
        processHighlightInfo(builder, document, highlightInfoList);

        builder.setStatus(ASDriver.AnalyzeFileResponse.Status.OK);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    });

    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  /**
   * Analyzes a file using a {@code TrafficLightRenderer}. This returns more than just issues since
   * {@code HighlightInfo} can be informational as well; it's up to the caller to determine which
   * ones they're interested in.
   */
  private static Collection<HighlightInfo> analyzeViaTrafficLightRenderer(Project project, Document document)
    throws ExecutionException, InterruptedException {
    TrafficLightRenderer renderer = ReadAction.nonBlocking(() -> new TrafficLightRenderer(project, document)).submit(
      AppExecutorUtil.getAppExecutorService()).get();
    while (true) {
      TrafficLightRenderer.DaemonCodeAnalyzerStatus status = renderer.getDaemonCodeAnalyzerStatus();
      if (status.reasonWhyDisabled != null) {
        // One reason I've seen for why it can be disabled is loading a file through the
        // "wrong" path, e.g. loading through the "/var" symlink on macOS will have
        // highlighting disabled.
        System.err.println("Highlighting is disabled: " + status.reasonWhyDisabled);
      }
      if (status.errorAnalyzingFinished) {
        // TODO(b/280811482): replace this horrible hack.
        Thread.sleep(5000);
        break;
      }
      UIUtil.dispatchAllInvocationEvents();
    }
    UIUtil.dispatchAllInvocationEvents();
    List<HighlightInfo> highlightInfoList = DaemonCodeAnalyzerImpl.getHighlights(document, null, project);
    renderer.dispose();
    return highlightInfoList;
  }

  /**
   * Converts a list of {@code HighlightInfo} instances into the expected proto form.
   */
  private static void processHighlightInfo(ASDriver.AnalyzeFileResponse.Builder builder,
                                Document document,
                                Collection<HighlightInfo> highlightInfoList) {
    for (HighlightInfo info : highlightInfoList) {
      ASDriver.AnalysisResult.HighlightSeverity severity = ASDriver.AnalysisResult.HighlightSeverity.newBuilder()
        .setName(info.getSeverity().getName())
        .setValue(info.getSeverity().myVal)
        .build();

      ASDriver.AnalysisResult.Builder resultBuilder = ASDriver.AnalysisResult.newBuilder()
        .setSeverity(severity)
        .setText(info.getText())
        .setLineNumber(document.getLineNumber(info.getStartOffset()));

      String toolId = info.getInspectionToolId();
      if (toolId != null) {
        resultBuilder.setToolId(toolId);
      }
      String description = info.getDescription();
      if (description != null) {
        resultBuilder.setDescription(description);
      }

      builder.addAnalysisResults(resultBuilder.build());
    }
  }

  /**
   * Tracks a replacement to be made by {@link AndroidStudioService#editFile}.
   */
  private static class Replacement {
    public final int start;
    public final int end;
    public final String text;

    public Replacement(int start, int end, String text) {
      this.start = start;
      this.end = end;
      this.text = text;
    }
  }

  @Override
  public void editFile(ASDriver.EditFileRequest request, StreamObserver<ASDriver.EditFileResponse> responseObserver) {
    ASDriver.EditFileResponse.Builder builder = ASDriver.EditFileResponse.newBuilder();
    builder.setResult(ASDriver.EditFileResponse.Result.ERROR);
    String fileName = request.getFile();
    String searchRegex = request.getSearchRegex();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      @Nullable String errorMessage = null;
      try {
        Project project = getSingleProject();
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(fileName));
        if (virtualFile == null) {
          errorMessage = "File does not exist on filesystem with path: " + fileName;
          return;
        }

        FileEditorManager manager = FileEditorManager.getInstance(project);
        Editor editor = manager.openTextEditor(new OpenFileDescriptor(project, virtualFile), true);
        if (editor == null) {
          errorMessage = "Could not open an editor with file: " + fileName;
          return;
        }
        Document document = editor.getDocument();

        String contents = editor.getDocument().getText();
        Pattern pattern = Pattern.compile(searchRegex);
        Matcher matcher = pattern.matcher(contents);

        // Compute the replacements ahead of time so that we can apply them in reverse order
        List<Replacement> replacements = new ArrayList<>();
        while (matcher.find()) {
          String replacement = request.getReplacement();
          int startOffset = matcher.start();
          int endOffset = matcher.end();

          // If there were capturing groups, then we have to substitute the "$1", "$2", etc.
          // tokens. This is done in reverse so that we don't try replacing "$1" before tokens like
          // "$10" or "$11".
          if (matcher.groupCount() > 0) {
            for (int i = matcher.groupCount(); i >= 1; i--) {
              replacement = replacement.replaceAll("\\$" + i, matcher.group(i));
            }
          }

          replacements.add(new Replacement(startOffset, endOffset, replacement));
        }

        if (replacements.isEmpty()) {
          errorMessage = "Could not find a match with these file contents: " + contents;
          return;
        }

        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
        // Run replacements in reverse order so that the offsets of subsequent replacements are unaffected
        for (int i = replacements.size() - 1; i >= 0; i--) {
          Replacement r = replacements.get(i);
          WriteCommandAction.writeCommandAction(project, file).run(() -> {
            document.replaceString(r.start, r.end, r.text);
          });
        }

        builder.setResult(ASDriver.EditFileResponse.Result.OK);
      }
      catch (Exception e) {
        e.printStackTrace();
        if (!StringUtil.isEmpty(e.getMessage())) {
          errorMessage = e.getMessage();
        }
      }
      finally {
        if (!StringUtil.isEmpty(errorMessage)) {
          System.err.println(errorMessage);
          builder.setErrorMessage(errorMessage);
        }
      }
    });

    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void waitForComponent(ASDriver.WaitForComponentRequest request, StreamObserver<ASDriver.WaitForComponentResponse> responseObserver) {
    ASDriver.WaitForComponentResponse.Builder builder = ASDriver.WaitForComponentResponse.newBuilder();
    try {
      StudioInteractionService studioInteractionService = new StudioInteractionService();
      studioInteractionService.waitForComponent(request.getMatchersList(), request.getWaitForEnabled());
      builder.setResult(ASDriver.WaitForComponentResponse.Result.OK);
    }
    catch (Exception e) {
      e.printStackTrace();
      builder.setResult(ASDriver.WaitForComponentResponse.Result.ERROR);
      if (!StringUtil.isEmpty(e.getMessage())) {
        builder.setErrorMessage(e.getMessage());
      }
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  /**
   * Returns the only open project, or errors if there is anything other than a single project
   * open. This is good for APIs that assume that only one project will ever be open, that way they
   * don't have to manifest a "projectName" parameter.
   */
  private Project getSingleProject() {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    int numProjects = projects.length;
    if (numProjects != 1) {
      throw new IllegalStateException(String.format("Expected exactly one open project, but found %d. If you have a valid test case " +
                                                    "where >1 project is expected to be open, then this framework can be changed to " +
                                                    "allow for project selection.%n", numProjects));
    }
    return projects[0];
  }

  /**
   * @param line 0-indexed line number. If null, use the current line. This default (and the
   *             column's default) match {@link com.intellij.ide.util.GotoLineNumberDialog}'s
   *             defaults.
   * @param column 0-indexed column number. If null, use column 0.
   */
  private void goToLine(Integer line, Integer column) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        Project firstProject = getSingleProject();
        Editor editor = FileEditorManager.getInstance(firstProject).getSelectedTextEditor();
        CaretModel caretModel = editor.getCaretModel();
        int lineToUse = line == null ? caretModel.getLogicalPosition().line : line;
        int columnToUse = column == null ? 0 : column;

        LogicalPosition position = new LogicalPosition(lineToUse, columnToUse);
        caretModel.removeSecondaryCarets();
        caretModel.moveToLogicalPosition(position);
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
        editor.getSelectionModel().removeSelection();
        IdeFocusManager.getGlobalInstance().requestFocus(editor.getContentComponent(), true);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  /**
   * Do not call directly: this method is only intended to be used internally by {@code AndroidStudio.runWithBleak}
   */
  @Override
  public void takeBleakSnapshot(ASDriver.TakeBleakSnapshotRequest request, StreamObserver<ASDriver.TakeBleakSnapshotResponse> responseObserver) {
    try {
      if (request.getCurrentIteration() == 0) {
        for (BleakCheck<?, ?> check : bleakOptions.getChecks()) {
          check.firstIterationFinished();
        }
      } else if (request.getCurrentIteration() == request.getLastIteration()) {
        for (BleakCheck<?, ?> check : bleakOptions.getChecks()) {
          check.lastIterationFinished();
        }
        String report = new BleakResult(bleakOptions.getChecks()).getErrorMessage();
        if (!report.isEmpty()) {
          responseObserver.onNext(
            ASDriver.TakeBleakSnapshotResponse.newBuilder().setResult(ASDriver.TakeBleakSnapshotResponse.Result.LEAK_DETECTED)
              .setLeakInfo(report).build());
          return;
        }
      } else {
        for (BleakCheck<?, ?> check : bleakOptions.getChecks()) {
          check.middleIterationFinished();
        }
      }
      responseObserver.onNext(ASDriver.TakeBleakSnapshotResponse.newBuilder().setResult(ASDriver.TakeBleakSnapshotResponse.Result.OK).build());
    } catch (Exception e) {
      ASDriver.TakeBleakSnapshotResponse.Builder builder =
        ASDriver.TakeBleakSnapshotResponse.newBuilder().setResult(ASDriver.TakeBleakSnapshotResponse.Result.ERROR);
      if (!StringUtil.isEmpty(e.getMessage())) {
        builder.setErrorMessage(e.getMessage());
      }
      responseObserver.onNext(builder.build());
      e.printStackTrace();
    } finally {
      responseObserver.onCompleted();
    }
  }
}
