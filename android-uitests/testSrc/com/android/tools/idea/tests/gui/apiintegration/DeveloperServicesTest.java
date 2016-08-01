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
package com.android.tools.idea.tests.gui.apiintegration;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.tests.gui.GuiSanityTestSuite;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.net.NetUtils;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.COMPILE;
import static com.google.common.truth.Truth.assertThat;
import static java.util.regex.Pattern.DOTALL;

@RunWith(GuiTestRunner.class)
public class DeveloperServicesTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @NotNull
  private VirtualFile getBuildGradleFile() {
    return guiTest.ideFrame().getEditor().open("app/build.gradle").getCurrentFile();
  }

  @NotNull
  private String getBuildGradleFileContents() {
    return guiTest.ideFrame().getEditor().open("app/build.gradle").getCurrentFileContents();
  }

  private boolean dependencyIsPresent(@NotNull GradleBuildModel buildModel, @NotNull String dependencyToFind) {
    DependenciesModel dependenciesModel = buildModel.dependencies();
    if (dependenciesModel != null) {
      for (ArtifactDependencyModel dependency : dependenciesModel.artifacts(COMPILE)) {
        String notation = dependency.compactNotation().value();
        if (notation.contains(dependencyToFind)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Verifies that Developer Services dependencies can be added and removed in app build gradle file.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14581655
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import a project.
   *   2. Open File > Project Structure
   *   3. Developer Services option is listed and contains the following options: Ads, Authentication, Notifications.
   *   4. Enable all services.
   *   5. Click OK button.
   *   6. Open app/build.gradle
   *   7. Dependencies are added for each of the enabled services.
   *   8. Open File > Structure
   *   9. Disable services for Ads, Authentication, Notification.
   *   10. Click OK button.
   *   11. Open app/build.gradle
   *   12. Dependencies are removed for the services.
   *   Verify:
   *   Toggling Developer Services dependencies reflects in app build gradle file.
   *   </pre>
   * <p>
   *   The test checks both the presence of the string and that the build.gradle file is written correctly by parsing it.
   */
  @Category(GuiSanityTestSuite.class)
  @Test
  public void toggleDevServicesDependencies() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();

    ProjectStructureDialogFixture projectStructureDialog =
      ideFrameFixture.openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...");

    projectStructureDialog
      .setServiceEnabled("Ads", true)
      .setServiceEnabled("Authentication", true)
      .setServiceEnabled("Notifications", true)
      .clickOk();

    String gradleFileContents = getBuildGradleFileContents();
    assertThat(gradleFileContents).contains("compile 'com.google.android.gms:play-services-ads:");
    assertThat(gradleFileContents).contains("compile 'com.google.android.gms:play-services-auth:");
    assertThat(gradleFileContents).contains("compile 'com.google.android.gms:play-services-gcm:");

    // Parsing of the Gradle file needs to be done in a ReadAction.
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        GradleBuildModel buildModel = GradleBuildModel.parseBuildFile(getBuildGradleFile(), ideFrameFixture.getProject());

        assertThat(dependencyIsPresent(buildModel, "play-services-ads")).isTrue();
        assertThat(dependencyIsPresent(buildModel, "play-services-auth")).isTrue();
        assertThat(dependencyIsPresent(buildModel, "play-services-gcm")).isTrue();
      }
    });

    // Disable the services.
    ideFrameFixture.openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...")
      .setServiceEnabled("Ads", false)
      .setServiceEnabled("Authentication", false)
      .setServiceEnabled("Notifications", false)
      .clickOk();

    gradleFileContents = getBuildGradleFileContents();
    assertThat(gradleFileContents).doesNotContain("compile 'com.google.android.gms:play-services-ads:");
    assertThat(gradleFileContents).doesNotContain("compile 'com.google.android.gms:play-services-auth:");
    assertThat(gradleFileContents).doesNotContain("compile 'com.google.android.gms:play-services-gcm:");

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        GradleBuildModel buildModel = GradleBuildModel.parseBuildFile(getBuildGradleFile(), ideFrameFixture.getProject());

        assertThat(dependencyIsPresent(buildModel, "play-services-ads")).isFalse();
        assertThat(dependencyIsPresent(buildModel, "play-services-auth")).isFalse();
        assertThat(dependencyIsPresent(buildModel, "play-services-gcm")).isFalse();
      }
    });
  }

  /**
   * Verifies that App Engine Java Servlet Module backend is added to Android Studio and the backend starts successfully through localhost.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14583297
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. File > New > New Module
   *   2. Select "Google Cloud Module"
   *   3. For Module Type, select "App Engine Java Servlet Module"
   *   4. For Module Name, enter in "backend"
   *   5. For Package Name, enter in "com.google.sampleapp.backend"
   *   6. For Client Module, select "app (google.simpleapplication)"
   *   7. Click "Finish" button. (Verify A)
   *   8. Edit run configurations to select "backend" (Verify B)
   *   Verify:
   *   A: The values entered into Module Name and Package Name fields are used in the class.
   *      At the root of the Project Navigator, there is a module called "backend"
   *   B: Backend starts successfully through telnet localhost 8080.
   *      The test gets a fixture for the output window of the Run tool and waits for the text "Dev App Server is now running" to appear,
   *      which shows that the server is running. After that it verifies the test can connect to port 8080 on localhost.
   *   </pre>
   */
  @Category(GuiSanityTestSuite.class)
  @Test
  public void createAppEngineJavaServletModule() throws Exception {
    final String MODULE_NAME = "backend";

    IdeFrameFixture ideFrame = guiTest.importSimpleApplication();

    ideFrame.openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Google Cloud Module")
      .clickNextToStep("New Google Cloud Module")
      .chooseModuleSubtype("App Engine Java Servlet Module")
      .setModuleName(MODULE_NAME)
      .setPackageName("com.google.sampleapp.backend")
      .chooseClientModule("app (google.simpleapplication)")
      .clickFinish();

    ProjectViewFixture.PaneFixture pane = ideFrame.getProjectView().selectAndroidPane();
    ideFrame.waitForGradleProjectSyncToFinish();
    assertThat(pane.hasModuleRootNode(MODULE_NAME)).isTrue();

    String fileContents = guiTest.ideFrame()
      .getEditor()
      .open("backend/src/main/java/com/google/sampleapp/backend/MyServlet.java")
      .getCurrentFileContents();

    assertThat(fileContents).contains("package com.google.sampleapp.backend;");

    ideFrame.runNonAndroidApp(MODULE_NAME);
    ExecutionToolWindowFixture runToolWindow = ideFrame.getRunToolWindow();
    runToolWindow
      .findContent(MODULE_NAME)
      .waitForOutput(new PatternTextMatcher(Pattern.compile(".*Dev App Server is now running.*", DOTALL)), 120);

    assertThat(NetUtils.canConnectToRemoteSocket(NetUtils.getLocalHostString(), 8080)).isTrue();
    runToolWindow.findContent(MODULE_NAME).stop();
  }
}
