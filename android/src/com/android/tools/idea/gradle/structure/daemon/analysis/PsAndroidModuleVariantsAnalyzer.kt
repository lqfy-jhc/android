/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.daemon.analysis

import com.android.tools.idea.gradle.structure.configurables.PsPathRenderer
import com.android.tools.idea.gradle.structure.model.PsGeneralIssue
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsIssueType
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsDeclaredModuleAndroidDependency
import com.android.tools.idea.gradle.structure.model.meta.maybeValue

fun analyzeModuleDependencies(androidModule: PsAndroidModule, pathRenderer: PsPathRenderer): Sequence<PsIssue> =
  androidModule.dependencies.modules.asSequence().flatMap { analyzeModuleDependency(it, pathRenderer) }

fun analyzeModuleDependency(dependency: PsDeclaredModuleAndroidDependency, pathRenderer: PsPathRenderer): Sequence<PsIssue> =
  with(pathRenderer) {
    val sourceModule = dependency.parent
    val targetModule = dependency.parent.parent.findModuleByGradlePath(dependency.gradlePath) as? PsAndroidModule ?: return emptySequence()

    fun analyzeBuildTypes(): Sequence<PsGeneralIssue> {
      fun targetBuildTypeExists(buildTypeName: String) = buildTypeName == "debug" || targetModule.findBuildType(buildTypeName) != null

      return sourceModule.buildTypes.items.asSequence().mapNotNull { sourceBuildType ->
        if (targetBuildTypeExists(sourceBuildType.name)) return@mapNotNull null
        if (sourceBuildType.matchingFallbacks.maybeValue?.any { fallback ->
            targetBuildTypeExists(fallback)
          } == true) return@mapNotNull null

        PsGeneralIssue(
          "No build type in module '${targetModule.path.renderNavigation { buildTypesPath }}' " +
          "matches build type '${sourceBuildType.path.renderNavigation()}'.",
          dependency.path,
          PsIssueType.PROJECT_ANALYSIS,
          PsIssue.Severity.ERROR,
          null
        )
      }
    }

    fun analyzeProductFlavors(): Sequence<PsIssue> {
      return sourceModule.flavorDimensions.asSequence().flatMap forEachDimension@{ sourceDimension ->
        if (targetModule.findFlavorDimension(sourceDimension.name) == null) return@forEachDimension emptySequence<PsIssue>()

        sourceModule.productFlavors.items.asSequence()
          .filter { it.effectiveDimension == sourceDimension.name }
          .mapNotNull forEachFlavor@{ sourceProductFlavor ->
            if (targetModule.findProductFlavor(sourceDimension.name, sourceProductFlavor.name) != null) return@forEachFlavor null
            if (sourceProductFlavor.matchingFallbacks.maybeValue?.any { fallback ->
                (targetModule.findProductFlavor(sourceDimension.name, fallback) != null)
              } == true) return@forEachFlavor null

            PsGeneralIssue(
              "No product flavor in module '${targetModule.path.renderNavigation { productFlavorsPath }}' " +
              "matches product flavor '${sourceProductFlavor.path.renderNavigation()}' " +
              "in dimension '${sourceDimension.path.renderNavigation()}'.",
              dependency.path,
              PsIssueType.PROJECT_ANALYSIS,
              PsIssue.Severity.ERROR,
              null
            )
          }
      }
    }

    fun analyzeFlavorDimensions(): Sequence<PsGeneralIssue> {
      return targetModule.flavorDimensions.items.asSequence()
        .filter { targetDimension -> targetModule.productFlavors.items.count { it.effectiveDimension == targetDimension.name } > 1 }
        .filter { targetDimension ->
          sourceModule.findFlavorDimension(targetDimension.name) == null
          && (sourceModule.parsedModel?.android()?.defaultConfig()?.missingDimensionStrategies().orEmpty()
            .all { strategy -> strategy.toList()?.firstOrNull()?.toString() != targetDimension.name })
        }
        .map { targetDimension ->
          PsGeneralIssue(
            "No flavor dimension in module '${sourceModule.path.renderNavigation { productFlavorsPath }}' matches " +
            "dimension '${targetDimension.path.renderNavigation()}' " +
            "from module ${targetModule.path.renderNavigation { productFlavorsPath }} on which " +
            "module '${sourceModule.path.renderNavigation(specificPlace = dependency.path)}' depends.",
            dependency.path,
            PsIssueType.PROJECT_ANALYSIS,
            PsIssue.Severity.ERROR,
            null
          )
        }
    }

    analyzeBuildTypes() + analyzeProductFlavors() + analyzeFlavorDimensions()
  }