load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.editors.manifest",
    iml_files = ["intellij.android.core.editors.manifest.iml"],
    test_class = "com.android.tools.idea.IdeaTestSuite",
    # keep sorted
    test_data = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android:test_deps",
        "//tools/adt/idea/android/testData",
        "//tools/base/build-system:android_gradle_plugin.zip",  # Please do not add old versions of AGP here. Use the old-agp-tests module instead.
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system:gradle-distrib",
    ],
    test_jvm_flags = [
        "-Djdk.attach.allowAttachSelf=true",
    ],
    test_srcs = ["testSrc"],
    test_tags = [
        "block-network",
        "no_test_mac",
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module, test]",
        "//tools/adt/idea/android:intellij.android.core.tests[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/project-system-gradle-models:intellij.android.projectSystem.gradle.models[module, test]",
        "//tools/adt/idea/rendering:intellij.android.rendering[module, test]",
    ],
)
