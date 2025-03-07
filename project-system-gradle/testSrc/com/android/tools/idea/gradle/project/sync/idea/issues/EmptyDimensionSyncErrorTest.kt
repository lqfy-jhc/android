/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.issues

import com.android.SdkConstants
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.build.events.BuildEvent
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EmptyDimensionSyncErrorTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(usageTracker)
  }

  @After
  fun cleanUp() {
    usageTracker.close()
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun testSyncErrorOnEmptyFavorDimension() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    val buildFile = preparedProject.root.resolve("app").resolve(SdkConstants.FN_BUILD_GRADLE)
    buildFile.appendText(
      """
        
        android {
          flavorDimensions 'flv_dim1', 'flv_dim3'
          productFlavors {
            flv1 {
                dimension 'flv_dim1'
            }
          }
        }
      """.trimIndent()
    )

    var capturedException: Exception? = null
    val buildEvents = mutableListOf<BuildEvent>()
    preparedProject.open(
      updateOptions = {
        it.copy(
          verifyOpened = { project ->
            Truth.assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult())
              .isEqualTo(ProjectSystemSyncManager.SyncResult.FAILURE)
          },
          syncExceptionHandler = { e: Exception ->
            capturedException = e
          },
          syncViewEventHandler = { buildEvent -> buildEvents.add(buildEvent) }
        )
      }
    ) {}

    assertThat(capturedException?.message).startsWith("No variants found for ':app'. Check ${buildFile.absolutePath} to ensure at least one variant exists and address any sync warnings and errors.")

    val failureEvent = usageTracker.usages
      .single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS }
    assertThat(failureEvent.studioEvent.gradleSyncFailure).isEqualTo(AndroidStudioEvent.GradleSyncFailure.ANDROID_SYNC_NO_VARIANTS_FOUND)

    //TODO(b/292231180): Fix this. This is not working because on since there is still no modules, and logic breaks
    // in AndroidGradleProjectResolver.getUserFriendlyError where reporting of this is triggered.
    // It can only be reported on second sync now.
    //val issuesEvent = usageTracker.usages
    //  .single { it.studioEvent.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_ISSUES }
    //assertThat(issuesEvent.studioEvent.gradleSyncIssuesList.map { it.type }).containsExactly(GradleSyncIssueType.TYPE_EMPTY_FLAVOR_DIMENSION)
  }
}