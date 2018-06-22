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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.android.builder.model.SyncIssue.SEVERITY_ERROR;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;

public class SyncIssuesReporter {
  @NotNull private final Map<Integer, BaseSyncIssuesReporter> myStrategies = new HashMap<>(6);
  @NotNull private final BaseSyncIssuesReporter myDefaultMessageFactory;

  @NotNull
  public static SyncIssuesReporter getInstance() {
    return ServiceManager.getService(SyncIssuesReporter.class);
  }

  @SuppressWarnings("unused") // Instantiated by IDEA
  public SyncIssuesReporter(@NotNull UnresolvedDependenciesReporter unresolvedDependenciesReporter) {
    this(unresolvedDependenciesReporter, new ExternalNdkBuildIssuesReporter(), new UnsupportedGradleReporter(),
         new BuildToolsTooLowReporter(), new MissingSdkPackageSyncIssuesReporter(), new SdkInManifestIssuesReporter(),
         new DeprecatedConfigurationReporter());
  }

  @VisibleForTesting
  SyncIssuesReporter(@NotNull BaseSyncIssuesReporter... strategies) {
    for (BaseSyncIssuesReporter strategy : strategies) {
      int issueType = strategy.getSupportedIssueType();
      myStrategies.put(issueType, strategy);
    }
    myDefaultMessageFactory = new UnhandledIssuesReporter();
  }

  public void report(@NotNull Module... modules) {
    report(Arrays.asList(modules));
  }

  public void report(@NotNull List<Module> modules) {
    Map<Module, List<SyncIssue>> issuesByModule = Maps.newHashMap();

    for (Module module : modules) {
      AndroidModuleModel androidModuleModel = AndroidModuleModel.get(module);
      if (androidModuleModel != null) {
        Collection<SyncIssue> androidSyncIssues = androidModuleModel.getSyncIssues();
        if (androidSyncIssues != null) {
          issuesByModule.computeIfAbsent(module, m -> Lists.newArrayList()).addAll(androidSyncIssues);
        }
      }
    }

    report(issuesByModule);
  }

  /**
   * Reports all sync errors for the provided collection of modules.
   */
  public void report(@NotNull Map<Module, List<SyncIssue>> issuesByModules) {
    if (issuesByModules.isEmpty()) {
      return;
    }

    Map<Integer, List<SyncIssue>> syncIssues = Maps.newHashMap();
    // Note: Since the SyncIssues don't store the module they come from their hashes will be the same.
    // As such we use an IdentityHashMap to ensure different issues get hashed to different values.
    Map<SyncIssue, Module> moduleMap = Maps.newIdentityHashMap();
    Map<Module, VirtualFile> buildFileMap = Maps.newHashMap();

    Project project = null;
    boolean[] hasSyncErrors = new boolean[1];
    // Go through all the issue, grouping them by their type. In doing so we also populate
    // the module and buildFile maps which will be used by each reporter.
    for (Module module : issuesByModules.keySet()) {
      project = module.getProject();
      buildFileMap.put(module, getGradleBuildFile(module));

      issuesByModules.get(module).forEach(issue -> {
        syncIssues.computeIfAbsent(issue.getType(), (type) -> Lists.newArrayList()).add(issue);
        moduleMap.put(issue, module);
        if (issue.getSeverity() == SEVERITY_ERROR) {
          hasSyncErrors[0] = true;
        }
      });
    }

    for (Map.Entry<Integer, List<SyncIssue>> entry : syncIssues.entrySet()) {
      BaseSyncIssuesReporter strategy = myStrategies.get(entry.getKey());
      if (strategy == null) {
        strategy = myDefaultMessageFactory;
      }
      strategy.reportAll(entry.getValue(), moduleMap, buildFileMap);
    }

    if (hasSyncErrors[0] && project != null) {
      GradleSyncState.getInstance(project).getSummary().setSyncErrorsFound(true);
    }
  }

  @VisibleForTesting
  @NotNull
  Map<Integer, BaseSyncIssuesReporter> getStrategies() {
    return myStrategies;
  }

  @VisibleForTesting
  @NotNull
  BaseSyncIssuesReporter getDefaultMessageFactory() {
    return myDefaultMessageFactory;
  }
}
