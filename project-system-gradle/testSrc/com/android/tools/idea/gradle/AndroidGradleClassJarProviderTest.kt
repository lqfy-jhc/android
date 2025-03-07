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
package com.android.tools.idea.gradle

import com.android.ide.common.gradle.Dependency
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AndroidGradleClassJarProviderTest {
  @JvmField
  @Rule
  val gradleProjectRule = AndroidGradleProjectRule()

  // Regression test for b/144018886 checking that runtime aar gradle dependencies are added to the  returned classpath by
  // AndroidGradleClassJarProvider
  @Test
  fun testRuntimeDependencies() {
    gradleProjectRule.load(TestProjectPaths.SIMPLE_APPLICATION)

    // We use firebase-common because it includes a number of runtime aar dependencies that help us testing that they are correctly
    // included in the returned classpath.
    val mockitoDependency = Dependency.parse("com.google.firebase:firebase-common:12.0.1")
    val module = gradleProjectRule.project.findAppModule()

    val dependencyManager = GradleDependencyManager.getInstance(gradleProjectRule.project)
    assertTrue(dependencyManager.addDependenciesAndSync(module, listOf(mockitoDependency)))

    fun IdeDependencies.all() =
      (this.androidLibraries.flatMap { it.target.runtimeJarFiles } + this.javaLibraries.map { it.target.artifact })

    val model = GradleAndroidModel.get(module)!!
    val runtimeDependencies = model.selectedMainRuntimeDependencies.all().toSet() - model.selectedMainCompileDependencies.all().toSet()
    assertTrue(runtimeDependencies.isNotEmpty())

    val classJarProvider = AndroidGradleClassJarProvider()
    assertTrue(classJarProvider.getModuleExternalLibraries(module).containsAll(runtimeDependencies))
  }
}