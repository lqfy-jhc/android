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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.LayoutScannerControl
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.validator.LayoutValidator
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorHierarchy
import com.android.tools.idea.validator.ValidatorResult
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

/**
 * Validator for [NlDesignSurface].
 * It retrieves validation results from the [RenderResult] and update the lint accordingly.
 */
class NlLayoutScanner(private val surface: NlDesignSurface, parent: Disposable): Disposable, LayoutScannerControl {

  constructor(surface: NlDesignSurface) : this(surface, surface)

  interface Listener {
    fun lintUpdated(result: ValidatorResult?)
  }

  /** Parses the layout and store all metadata required for linking issues to source [NlComponent] */
  private val layoutParser = NlScannerLayoutParser()
  /** Helper class for displaying output to lint system */
  private val lintIntegrator = AccessibilityLintIntegrator(surface.issueModel)

  /** Returns list of issues generated by linter that are specific to the layout. */
  override val issues get() = lintIntegrator.issues

  @VisibleForTesting
  val listeners = HashSet<Listener>()

  init {
    Disposer.register(parent, this)
  }

  override fun pause() {
    LayoutValidator.setPaused(true)
  }

  override fun resume() {
    LayoutValidator.setPaused(false)
  }

  /**
   * Validate the layout and update the lint accordingly.
   */
  override
  fun validateAndUpdateLint(renderResult: RenderResult, model: NlModel) {
    when (val validatorResult = renderResult.validatorResult) {
      is ValidatorHierarchy -> {
        if (!validatorResult.isHierarchyBuilt) {
          // Result not available
          listeners.forEach { it.lintUpdated(null) }
          return
        }
        validateAndUpdateLint(renderResult, LayoutValidator.validate(validatorResult), model, surface)
      }
      is ValidatorResult -> {
        validateAndUpdateLint(renderResult, validatorResult, model, surface)
      }
      else -> {
        // Result not available.
        listeners.forEach { it.lintUpdated(null) }
      }
    }
  }

  private fun validateAndUpdateLint(
      renderResult: RenderResult,
      validatorResult: ValidatorResult,
      model: NlModel,
      surface: NlDesignSurface) {
    lintIntegrator.clear()
    layoutParser.clear()

    var result: ValidatorResult? = null
    try {
      val components = model.components
      if (components.isEmpty()) {
        // Result not available.
        return
      }

      var issuesWithoutSources = 0
      val root = components[0]
      layoutParser.buildViewToComponentMap(root)
      validatorResult.issues.forEach {
        if ((it.mLevel == ValidatorData.Level.ERROR || it.mLevel == ValidatorData.Level.WARNING) &&
            it.mType == ValidatorData.Type.ACCESSIBILITY) {
          val component = layoutParser.findComponent(it, validatorResult.srcMap)
          if (component == null) {
            issuesWithoutSources++
          } else {
            lintIntegrator.createIssue(it, component)
          }
        }
        // TODO: b/180069618 revisit metrics. Should log each issue.
      }

      if (issuesWithoutSources > 0) {
        if (layoutParser.includeComponents.isNotEmpty()) {
          // Some issues found without source. Handle them accordingly.
          lintIntegrator.handleInclude(layoutParser, surface)
        }
      }

      lintIntegrator.populateLints()
      result = validatorResult
    } finally {
      layoutParser.clear()
      // TODO: b/180069618 revisit metrics. Should log render result here.
      listeners.forEach { it.lintUpdated(result) }
    }
  }

  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }

  override fun dispose() {
    layoutParser.clear()
    listeners.clear()
    lintIntegrator.clear()
  }

  /** Returns true if [NlScannerLayoutParser] has been cleaned. False otherwise. */
  @VisibleForTesting
  fun isParserCleaned(): Boolean {
    return layoutParser.isEmpty()
  }
}

// For debugging
fun ValidatorResult.toDetailedString(): String? {
  val builder: StringBuilder = StringBuilder().append("Result containing ").append(issues.size).append(
    " issues:\n")
  val var2: Iterator<*> = this.issues.iterator()
  while (var2.hasNext()) {
    val issue = var2.next() as ValidatorData.Issue
    if (issue.mLevel == ValidatorData.Level.ERROR) {
      builder.append(" - [E::").append(issue.mLevel.name).append("] ").append(issue.mMsg).append("\n")
    }
    else {
      builder.append(" - [W::").append(issue.mLevel.name).append("] ").append(issue.mMsg).append("\n")
    }
  }
  return builder.toString()
}