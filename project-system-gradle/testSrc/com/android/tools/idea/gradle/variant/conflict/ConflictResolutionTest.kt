/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.variant.conflict

import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.SwitchVariantRequest
import com.android.tools.idea.gradle.project.sync.getModuleIdForSyncRequest
import com.android.tools.idea.testing.JavaModuleModelBuilder.Companion.rootModuleBuilder
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.readAndClearLastSyncRequest
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.android.tools.idea.testing.switchTestProjectVariantsFromAndroidModel
import com.google.common.truth.Truth.assertThat
import java.io.File

class ConflictResolutionTest : ConflictsTestCase() {
  fun testSolveSelectionConflict() {
    setupTestProjectFromAndroidModel(
      project,
      File(myFixture.tempDirPath),
      setupAllVariants = true,
      rootModuleBuilder,
      appModuleBuilder(appPath = ":app", selectedVariant = "release", dependOnVariant = "release"),
      libModuleBuilder()
    )

    val appModule = project.findAppModule()
    val libModule = project.findModule("lib")

    assertThat(GradleAndroidModel.get(appModule)!!.selectedVariant.name).isEqualTo("release")
    assertThat(GradleAndroidModel.get(libModule)!!.selectedVariant.name).isEqualTo("debug")

    var conflicts = ConflictSet.findConflicts(project).selectionConflicts
    assertThat(conflicts).hasSize(1)

    // Source is the :lib module, which has "debug".
    val conflict = conflicts[0]
    assertThat(conflict.source).isSameAs(libModule)
    assertThat(conflict.selectedVariant).isEqualTo("debug")

    val affectedModules = conflict.affectedModules
    assertThat(affectedModules).hasSize(1)

    // Affected is the :app module, which has "release".
    val affectedModule = affectedModules[0]
    assertThat(affectedModule.target).isSameAs(appModule)
    assertThat(affectedModule.expectedVariant).isEqualTo("release")

    assertThat(ConflictResolution.solveSelectionConflict(conflict)).isTrue()

    assertThat(project.readAndClearLastSyncRequest()?.requestedVariantChange).isEqualTo(
      SwitchVariantRequest(
        libModule.getModuleIdForSyncRequest()!!,
        "release",
        null
      )
    )

    switchTestProjectVariantsFromAndroidModel(
      project,
      File(myFixture.tempDirPath),
      rootModuleBuilder,
      appModuleBuilder(appPath = ":app", selectedVariant = "release", dependOnVariant = "release"),
      libModuleBuilder().copy(selectedBuildVariant = "release")

    )

    // After fixing the conflict, the selected variants match.
    assertThat(GradleAndroidModel.get(appModule)!!.selectedVariant.name).isEqualTo("release")
    assertThat(GradleAndroidModel.get(libModule)!!.selectedVariant.name).isEqualTo("release")

    // After fixing the conflict, there are no more conflicts left.
    conflicts = ConflictSet.findConflicts(project).selectionConflicts
    assertThat(conflicts).hasSize(0)
  }

  fun testSolveMultiAppSelectionConflict() {
    setupTestProjectFromAndroidModel(
      project,
      File(myFixture.tempDirPath),
      setupAllVariants = true,
      rootModuleBuilder,
      appModuleBuilder(appPath = ":app", selectedVariant = "release", dependOnVariant = "release"),
      appModuleBuilder(appPath = ":app2", selectedVariant = "debug", dependOnVariant = "debug"),
      libModuleBuilder()
    )

    val appModule = project.findAppModule()
    val libModule = project.findModule("lib")

    assertThat(GradleAndroidModel.get(appModule)!!.selectedVariant.name).isEqualTo("release")
    assertThat(GradleAndroidModel.get(libModule)!!.selectedVariant.name).isEqualTo("debug")

    var conflicts = ConflictSet.findConflicts(project).selectionConflicts
    assertThat(conflicts).hasSize(1)

    // Source is the :lib module, which has "debug".
    val conflict = conflicts[0]
    assertThat(conflict.source).isSameAs(libModule)
    assertThat(conflict.selectedVariant).isEqualTo("debug")

    val affectedModules = conflict.affectedModules
    assertThat(affectedModules).hasSize(1)

    // Affected is the :app module, which has "release".
    val affectedModule = affectedModules[0]
    assertThat(affectedModule.target).isSameAs(appModule)
    assertThat(affectedModule.expectedVariant).isEqualTo("release")

    assertThat(ConflictResolution.solveSelectionConflict(conflict)).isTrue()

    assertThat(project.readAndClearLastSyncRequest()?.requestedVariantChange).isEqualTo(
      SwitchVariantRequest(
        libModule.getModuleIdForSyncRequest()!!,
        "release",
        null
      )
    )

    switchTestProjectVariantsFromAndroidModel(
      project,
      File(myFixture.tempDirPath),
      rootModuleBuilder,
      appModuleBuilder(appPath = ":app", selectedVariant = "release", dependOnVariant = "release"),
      appModuleBuilder(appPath = ":app2", selectedVariant = "debug", dependOnVariant = "debug"),
      libModuleBuilder().copy(selectedBuildVariant = "release")

    )

    // After fixing the conflict, the selected variants match.
    assertThat(GradleAndroidModel.get(appModule)!!.selectedVariant.name).isEqualTo("release")
    assertThat(GradleAndroidModel.get(libModule)!!.selectedVariant.name).isEqualTo("release")

    // After fixing the conflict, there are no more conflicts left.
    conflicts = ConflictSet.findConflicts(project).selectionConflicts
    // TODO(b/201824664): In multi-app conflicts we should fix the conflict my selecting the same variant in all apps. 
    // TODO(b/201824664): assertThat(conflicts).hasSize(0)
  }

  fun testSolveSelectionConflictWithABIs() {
    // TODO: Add tests for modules with NdkModuleModels.
  }
}
