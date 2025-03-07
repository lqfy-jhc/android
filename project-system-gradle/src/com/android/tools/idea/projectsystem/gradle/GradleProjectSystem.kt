/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.sdklib.AndroidVersion
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.tools.idea.gradle.AndroidGradleClassJarProvider
import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeJavaArtifact
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.run.OutputBuildAction
import com.android.tools.idea.gradle.run.PostBuildModel
import com.android.tools.idea.gradle.run.PostBuildModelProvider
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil.getGeneratedSourceFoldersToUse
import com.android.tools.idea.gradle.util.OutputType
import com.android.tools.idea.gradle.util.getOutputFilesFromListingFile
import com.android.tools.idea.gradle.util.getOutputListingFile
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.model.ClassJarProvider
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.BuildConfigurationSourceProvider
import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.android.tools.idea.projectsystem.IdeaSourceProviderImpl
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider
import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderImpl
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.projectsystem.SourceProvidersFactory
import com.android.tools.idea.projectsystem.SourceProvidersImpl
import com.android.tools.idea.projectsystem.createSourceProvidersForLegacyModule
import com.android.tools.idea.projectsystem.emptySourceProvider
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.res.AndroidInnerClassFinder
import com.android.tools.idea.res.AndroidManifestClassPsiElementFinder
import com.android.tools.idea.res.AndroidResourceClassPsiElementFinder
import com.android.tools.idea.res.ProjectLightResourceClassService
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.GradleApkProvider
import com.android.tools.idea.run.GradleApplicationIdProvider
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.run.configuration.AndroidWearConfiguration
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.util.androidFacet
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.nio.file.Path

class GradleProjectSystem(val project: Project) : AndroidProjectSystem {
  private val moduleHierarchyProvider: GradleModuleHierarchyProvider = GradleModuleHierarchyProvider(project)
  private val mySyncManager: ProjectSystemSyncManager = GradleProjectSystemSyncManager(project)
  private val myBuildManager: ProjectSystemBuildManager = GradleProjectSystemBuildManager(project)
  private val myProjectBuildModelHandler: ProjectBuildModelHandler = ProjectBuildModelHandler.getInstance(project)

  private val myPsiElementFinders: List<PsiElementFinder> = run {
    listOf(
      AndroidInnerClassFinder.INSTANCE,
      AndroidManifestClassPsiElementFinder.getInstance(project),
      AndroidResourceClassPsiElementFinder(getLightResourceClassService())
    )
  }

  override fun getBootClasspath(module: Module): Collection<String> {
    return GradleAndroidModel.get(module)?.androidProject?.bootClasspath ?: emptyList()
  }

  override fun getSyncManager(): ProjectSystemSyncManager = mySyncManager
  override fun getBuildManager(): ProjectSystemBuildManager = myBuildManager

  override fun getPathToAapt(): Path {
    return AaptInvoker.getPathToAapt(AndroidSdks.getInstance().tryToChooseSdkHandler(), LogWrapper(GradleProjectSystem::class.java))
  }

  override fun allowsFileCreation() = true

  override fun getDefaultApkFile(): VirtualFile? {
    return ModuleManager.getInstance(project).modules.asSequence()
      .mapNotNull { GradleAndroidModel.get(it) }
      .filter { it.androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_APP }
      .flatMap { androidModel ->
        if (androidModel.features.isBuildOutputFileSupported) {
          androidModel
            .selectedVariant
            .mainArtifact
            .buildInformation
            .getOutputListingFile(OutputType.Apk)
            ?.let { getOutputFilesFromListingFile(it) }
            ?.asSequence()
            .orEmpty()
        }
        else {
          emptySequence()
        }
      }
      .filterNotNull()
      .find { it.exists() }
      ?.let { VfsUtil.findFileByIoFile(it, true) }
  }

  override fun getModuleSystem(module: Module): GradleModuleSystem {
    return GradleModuleSystem(module, myProjectBuildModelHandler, moduleHierarchyProvider.createForModule(module))
  }

  override fun getApplicationIdProvider(runConfiguration: RunConfiguration): GradleApplicationIdProvider? {
    if (runConfiguration !is AndroidRunConfigurationBase &&
        runConfiguration !is AndroidWearConfiguration) return null
    val androidFacet = (runConfiguration as? ModuleBasedConfiguration<*, *>)?.configurationModule?.module?.androidFacet ?: return null
    val androidModel = GradleAndroidModel.get(androidFacet) ?: return null
    val isTestConfiguration = if (runConfiguration is AndroidRunConfigurationBase) runConfiguration.isTestConfiguration else false

    return GradleApplicationIdProvider.create(
      androidFacet,
      isTestConfiguration,
      androidModel,
      androidModel.selectedBasicVariant,
      androidModel.selectedVariant
    )
  }

  override fun getApkProvider(runConfiguration: RunConfiguration): ApkProvider? {
    val context = runConfiguration.getGradleContext() ?: return null
    return GradleApkProvider(
      context.androidFacet,
      getApplicationIdProvider(runConfiguration) ?: return null,
      PostBuildModelProvider { (runConfiguration as? UserDataHolder)?.getUserData(GradleApkProvider.POST_BUILD_MODEL) },
      context.isTestConfiguration,
      context.alwaysDeployApkFromBundle
    )
  }

  override fun validateRunConfiguration(runConfiguration: RunConfiguration): List<ValidationError> {
    val context = runConfiguration.getGradleContext() ?: return super.validateRunConfiguration(runConfiguration)
    return GradleApkProvider.doValidate(context.androidFacet, context.isTestConfiguration, context.alwaysDeployApkFromBundle)
  }

  internal fun getBuiltApksForSelectedVariant(
    androidFacet: AndroidFacet,
    assembleResult: AssembleInvocationResult,
    forTests: Boolean = false
  ): List<ApkInfo>? {
    val androidModel = GradleAndroidModel.get(androidFacet) ?: return null

    // Composite builds are not properly supported with AGPs 3.x and we ignore a possibility of receiving multiple models here.
    // `PostBuildModel`s were not designed to handle this.
    val postBuildModel: PostBuildModel? =
      (assembleResult.invocationResult.models.firstOrNull() as? OutputBuildAction.PostBuildProjectModels)
        ?.let { PostBuildModel(it) }

    val postBuildModelProvider = PostBuildModelProvider { postBuildModel }

    return GradleApkProvider(
      androidFacet,
      GradleApplicationIdProvider.create(
        androidFacet,
        forTests,
        androidModel,
        androidModel.selectedBasicVariant,
        androidModel.selectedVariant
      ),
      postBuildModelProvider,
      forTests,
      false // Overriden and doesn't matter.
    )
      .getApks(
        emptyList(),
        AndroidVersion(30),
        androidModel,
        androidModel.selectedVariant,
        when (assembleResult.buildMode) {
          BuildMode.APK_FROM_BUNDLE -> GradleApkProvider.OutputKind.AppBundleOutputModel
          BuildMode.ASSEMBLE -> GradleApkProvider.OutputKind.Default
          else -> error("Unsupported build mode: ${assembleResult.buildMode}")
        }
      )
  }

  override fun getPsiElementFinders(): List<PsiElementFinder> = myPsiElementFinders

  override fun getLightResourceClassService() = ProjectLightResourceClassService.getInstance(project)

  override val submodules: Collection<Module>
    get() = moduleHierarchyProvider.forProject.submodules

  override fun getSourceProvidersFactory(): SourceProvidersFactory = object : SourceProvidersFactory {
    override fun createSourceProvidersFor(facet: AndroidFacet): SourceProviders? {
      val model = GradleAndroidModel.get(facet)
      return if (model != null) createSourceProvidersFromModel(model) else createSourceProvidersForLegacyModule(facet)
    }
  }

  override fun getBuildConfigurationSourceProvider(): BuildConfigurationSourceProvider {
    return CachedValuesManager.getManager(project).getCachedValue(project) {
      CachedValueProvider.Result.create(
        GradleBuildConfigurationSourceProvider(project),
        ProjectRootModificationTracker.getInstance(project)
      )
    }
  }

  override fun getClassJarProvider(): ClassJarProvider {
    return AndroidGradleClassJarProvider()
  }

  /**
   * Stores information about all the gradle projects.
   *
   * [packageToModule] stores mapping information from package name to [Module]s that have that package name.
   */
  internal class GradleProjectCensus(
    val packageToModule: Map<String, Collection<Module>>,
    val namespacesWithPrefixes: Set<String>,
    val applicationIdToModule: Map<String, Collection<Module>>
  )

  private fun getGradleProjectCensus(project: Project): GradleProjectCensus {
    return CachedValuesManager.getManager(project).getCachedValue(project, CachedValueProvider {
      val packageToModule = mutableMapOf<String, MutableList<Module>>()
      val applicationIdsToModule = mutableMapOf<String, MutableList<Module>>()

      for (androidFacet in project.getAndroidFacets()) {
        val model = GradleAndroidModel.get(androidFacet) ?: continue
        val mainModule = androidFacet.mainModule
        val androidTestModule = androidFacet.androidTestModule
        model.androidProject.namespace?.let { namespace ->
          packageToModule.getOrPut(namespace) { mutableListOf() }.add(mainModule)

        }
        if (androidTestModule != null) {
          model.androidProject.testNamespace?.let { namespace ->
              packageToModule.getOrPut(namespace) { mutableListOf() }.add(androidTestModule)
          }
        }
        // Collect application IDs into sets as they might be duplicated
        val mainApplicationIds = mutableSetOf<String>()
        val testApplicationIds = mutableSetOf<String>()
        for (variant in model.androidProject.basicVariants) {
          variant.applicationId?.let { mainApplicationIds.add(it) }
          variant.testApplicationId?.let { testApplicationIds.add(it) }
        }
        for (applicationId in mainApplicationIds) {
          applicationIdsToModule.getOrPut(applicationId) { mutableListOf() }.add(mainModule)
        }
        if (androidTestModule != null) {
          for (applicationId in testApplicationIds) {
            applicationIdsToModule.getOrPut(applicationId) { mutableListOf() }.add(androidTestModule)
          }
        }
      }
      val namespacesWithPrefixes = HashSet<String>()
      for (namespace in packageToModule.keys) {
        var packageName = namespace
        while (true) {
          if (!namespacesWithPrefixes.add(packageName)) break
          val lastDot = packageName.lastIndexOf('.').takeIf { it > 0 } ?: break
          packageName = packageName.substring(0, lastDot)
        }
      }
      return@CachedValueProvider CachedValueProvider.Result(
        GradleProjectCensus(packageToModule, namespacesWithPrefixes, applicationIdsToModule), ProjectSyncModificationTracker.getInstance(project)
      )
    })
  }

  override fun getAndroidFacetsWithPackageName(project: Project, packageName: String): List<AndroidFacet> {
    val census = getGradleProjectCensus(project)
    return census.packageToModule[packageName]?.mapNotNull { it.androidFacet } ?: emptyList()
  }

  override fun isNamespaceOrParentPackage(packageName: String): Boolean {
    val census = getGradleProjectCensus(project)
    return census.namespacesWithPrefixes.contains(packageName)
  }

  override fun getKnownApplicationIds(project: Project): Set<String> {
    val census = getGradleProjectCensus(project)
    return census.applicationIdToModule.keys
  }

  /**
   * Gradle supports the profiling mode flag.
   */
  override fun supportsProfilingMode() = true

}

private val IdeAndroidArtifact.scopeType: ScopeType
  get() = when (this.name) {
    IdeArtifactName.ANDROID_TEST -> ScopeType.ANDROID_TEST
    IdeArtifactName.MAIN -> ScopeType.MAIN
    IdeArtifactName.TEST_FIXTURES -> ScopeType.TEST_FIXTURES
    IdeArtifactName.UNIT_TEST -> ScopeType.UNIT_TEST
  }

fun createSourceProvidersFromModel(model: GradleAndroidModel): SourceProviders {
  val all =
    (
      model.allSourceProviders.associateWith { createIdeaSourceProviderFromModelSourceProvider(it, ScopeType.MAIN) } +
      model.allUnitTestSourceProviders.associateWith { createIdeaSourceProviderFromModelSourceProvider(it, ScopeType.UNIT_TEST) } +
      model.allAndroidTestSourceProviders.associateWith { createIdeaSourceProviderFromModelSourceProvider(it, ScopeType.ANDROID_TEST) } +
      model.allTestFixturesSourceProviders.associateWith { createIdeaSourceProviderFromModelSourceProvider(it, ScopeType.TEST_FIXTURES) }
    )

  fun IdeSourceProvider.toIdeaSourceProvider(): NamedIdeaSourceProvider {
    if (!all.containsKey(this)) {
      println("Does not contain: $this")
    }
    return all.getValue(this)
  }

  fun IdeAndroidArtifact.toGeneratedIdeaSourceProvider(): IdeaSourceProvider {
    val sourceFolders = getGeneratedSourceFoldersToUse(this, model.androidProject)
    return IdeaSourceProviderImpl(
      scopeType,
      object : IdeaSourceProviderImpl.Core {
        override val manifestFileUrls: Sequence<String> = emptySequence()
        override val manifestDirectoryUrls: Sequence<String> = emptySequence()
        override val javaDirectoryUrls: Sequence<String> =
          sourceFolders.map { VfsUtil.fileToUrl(it) }.asSequence()
        override val kotlinDirectoryUrls: Sequence<String> = emptySequence()
        override val resourcesDirectoryUrls: Sequence<String> = emptySequence()
        override val aidlDirectoryUrls: Sequence<String> = emptySequence()
        override val renderscriptDirectoryUrls: Sequence<String> = emptySequence()
        override val jniLibsDirectoryUrls: Sequence<String> = emptySequence()
        override val resDirectoryUrls: Sequence<String> =
          this@toGeneratedIdeaSourceProvider.generatedResourceFolders.map { VfsUtil.fileToUrl(it) }.asSequence()
        override val assetsDirectoryUrls: Sequence<String> = emptySequence()
        override val shadersDirectoryUrls: Sequence<String> = emptySequence()
        override val mlModelsDirectoryUrls: Sequence<String> = emptySequence()
        override val customSourceDirectories: Map<String, Sequence<String>> = emptyMap()
        override val baselineProfileDirectoryUrls: Sequence<String> get() = emptySequence()
      }
    )
  }

  fun IdeJavaArtifact.toGeneratedIdeaSourceProvider(): IdeaSourceProvider {
    val sourceFolders = getGeneratedSourceFoldersToUse(this, model.androidProject)
    return IdeaSourceProviderImpl(
      ScopeType.UNIT_TEST,
      object : IdeaSourceProviderImpl.Core {
        override val manifestFileUrls: Sequence<String> = emptySequence()
        override val manifestDirectoryUrls: Sequence<String> = emptySequence()
        override val javaDirectoryUrls: Sequence<String> =
          sourceFolders.map { VfsUtil.fileToUrl(it) }.asSequence()
        override val kotlinDirectoryUrls: Sequence<String> = emptySequence()
        override val resourcesDirectoryUrls: Sequence<String> = emptySequence()
        override val aidlDirectoryUrls: Sequence<String> = emptySequence()
        override val renderscriptDirectoryUrls: Sequence<String> = emptySequence()
        override val jniLibsDirectoryUrls: Sequence<String> = emptySequence()
        override val resDirectoryUrls: Sequence<String> = emptySequence()
        override val assetsDirectoryUrls: Sequence<String> = emptySequence()
        override val shadersDirectoryUrls: Sequence<String> = emptySequence()
        override val mlModelsDirectoryUrls: Sequence<String> = emptySequence()
        override val customSourceDirectories: Map<String, Sequence<String>> = emptyMap()
        override val baselineProfileDirectoryUrls: Sequence<String> get() = emptySequence()
      }
    )
  }

  return SourceProvidersImpl(
    mainIdeaSourceProvider = model.defaultSourceProvider.toIdeaSourceProvider(),
    currentSourceProviders = model.activeSourceProviders.map { it.toIdeaSourceProvider() },
    currentUnitTestSourceProviders = model.unitTestSourceProviders.map { it.toIdeaSourceProvider() },
    currentAndroidTestSourceProviders = model.androidTestSourceProviders.map { it.toIdeaSourceProvider() },
    currentTestFixturesSourceProviders = model.testFixturesSourceProviders.map { it.toIdeaSourceProvider() },
    currentAndSomeFrequentlyUsedInactiveSourceProviders = model.allSourceProviders.map { it.toIdeaSourceProvider() },
    mainAndFlavorSourceProviders =
    listOf(model.defaultSourceProvider.toIdeaSourceProvider()) + model.androidProject.multiVariantData?.let { multiVariantData ->
      val flavorNames = model.selectedVariant.productFlavors.toSet()
      multiVariantData.productFlavors.filter { it.productFlavor.name in flavorNames }
        .mapNotNull { it.sourceProvider?.toIdeaSourceProvider() }
    }.orEmpty(),
    generatedSources = model.selectedVariant.mainArtifact.toGeneratedIdeaSourceProvider(),
    generatedUnitTestSources = model.selectedVariant.unitTestArtifact?.toGeneratedIdeaSourceProvider() ?: emptySourceProvider(
      ScopeType.UNIT_TEST),
    generatedAndroidTestSources = model.selectedVariant.androidTestArtifact?.toGeneratedIdeaSourceProvider() ?: emptySourceProvider(
      ScopeType.ANDROID_TEST),
    generatedTestFixturesSources = model.selectedVariant.testFixturesArtifact?.toGeneratedIdeaSourceProvider() ?: emptySourceProvider(
      ScopeType.TEST_FIXTURES)
  )
}

private fun createIdeaSourceProviderFromModelSourceProvider(it: IdeSourceProvider, scopeType: ScopeType = ScopeType.MAIN): NamedIdeaSourceProvider {
  return NamedIdeaSourceProviderImpl(
    it.name,
    scopeType,
    core = object : NamedIdeaSourceProviderImpl.Core {
      override val manifestFileUrl: String get() = VfsUtil.fileToUrl(it.manifestFile)
      override val javaDirectoryUrls: Sequence<String> get() = it.javaDirectories.asSequence().toUrls()
      override val kotlinDirectoryUrls: Sequence<String> get() = it.kotlinDirectories.asSequence().toUrls()
      override val resourcesDirectoryUrls: Sequence<String> get() = it.resourcesDirectories.asSequence().toUrls()
      override val aidlDirectoryUrls: Sequence<String> get() = it.aidlDirectories.asSequence().toUrls()
      override val renderscriptDirectoryUrls: Sequence<String> get() = it.renderscriptDirectories.asSequence().toUrls()
      override val jniLibsDirectoryUrls: Sequence<String> get() = it.jniLibsDirectories.asSequence().toUrls()
      override val resDirectoryUrls: Sequence<String> get() = it.resDirectories.asSequence().toUrls()
      override val assetsDirectoryUrls: Sequence<String> get() = it.assetsDirectories.asSequence().toUrls()
      override val shadersDirectoryUrls: Sequence<String> get() = it.shadersDirectories.asSequence().toUrls()
      override val mlModelsDirectoryUrls: Sequence<String> get() = it.mlModelsDirectories.asSequence().toUrls()
      override val customSourceDirectories: Map<String, Sequence<String>>
        get() = it.customSourceDirectories.associateBy({ it.sourceTypeName }) { f -> sequenceOf(f.directory).toUrls() }
      override val baselineProfileDirectoryUrls: Sequence<String> get() = it.baselineProfileDirectories.asSequence().toUrls()
    }
  )
}

/** Convert a set of IO files into a set of IDEA file urls referring to equivalent virtual files  */
private fun Sequence<File>.toUrls(): Sequence<String> = map { VfsUtil.fileToUrl(it) }

fun AssembleInvocationResult.getBuiltApksForSelectedVariant(androidFacet: AndroidFacet, forTests: Boolean = false): List<ApkInfo>? {
  val projectSystem = androidFacet.module.project.getProjectSystem() as? GradleProjectSystem
                      ?: error("The supplied facet does not represent a project managed by the Gradle project system. " +
                               "Module: ${androidFacet.module.name}")
  return projectSystem.getBuiltApksForSelectedVariant(androidFacet, this, forTests)
}
