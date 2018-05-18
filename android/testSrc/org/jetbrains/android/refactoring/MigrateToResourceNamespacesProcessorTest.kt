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
package org.jetbrains.android.refactoring

import com.android.builder.model.AndroidProject
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnusedNamespaceInspection
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.inspections.AndroidDomInspection
import org.jetbrains.android.inspections.AndroidElementNotAllowedInspection
import org.jetbrains.android.inspections.AndroidUnknownAttributeInspection

class MigrateToResourceNamespacesProcessorTest : AndroidTestCase() {

  override fun configureAdditionalModules(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
    modules: MutableList<MyAdditionalModuleData>
  ) {
    addModuleWithAndroidFacet(
      projectBuilder,
      modules,
      "lib",
      AndroidProject.PROJECT_TYPE_LIBRARY,
      true
    )
  }

  override fun setUp() {
    super.setUp()

    myFixture.enableInspections(
      AndroidDomInspection::class.java,
      AndroidUnknownAttributeInspection::class.java,
      AndroidElementNotAllowedInspection::class.java,
      XmlUnusedNamespaceInspection::class.java
    )

    runUndoTransparentWriteAction {
      myFacet.manifest!!.`package`.value = "com.example.app"
      AndroidFacet.getInstance(getAdditionalModuleByName("lib")!!)!!.manifest!!.`package`.value = "com.example.lib"
    }

    myFixture.addFileToProject(
      "${getAdditionalModulePath("lib")}/res/values/lib.xml",
      // language=xml
      """
        <resources>
          <string name="libString">Hello from lib</string>
        </resources>
      """.trimIndent()
    )

    // This is necessary to get the augmenting mechanism started.
    myFixture.addFileToProject(
      "gen/com/example/app/R.java",
      """
        package com.example.app;

        public class R {}
      """.trimIndent()
      )
  }

  fun testResourceValues() {
    val appXml = myFixture.addFileToProject(
      "/res/values/app.xml",
      // language=xml
      """
        <resources>
          <string name="appString">Hello from app</string>
          <string name="s1">@string/appString</string>
          <string name="s2">@string/libString</string>
        </resources>
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "/res/layout/layout.xml",
      // language=xml
      """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
          <TextView android:text="@string/appString" />
          <TextView android:text="@string/libString" />
        </LinearLayout>
      """.trimIndent()
    )

    refactorAndSync()

    myFixture.checkResult(
      "/res/layout/layout.xml",
      // language=xml
      """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
          <TextView android:text="@string/appString" />
          <TextView android:text="@com.example.lib:string/libString" />
        </LinearLayout>
      """.trimIndent(),
      true
    )

    FileDocumentManager.getInstance().saveAllDocuments()
    myFixture.configureFromExistingVirtualFile(appXml.virtualFile)

    myFixture.checkResult(
      // language=xml
      """
        <resources>
          <string name="appString">Hello from app</string>
          <string name="s1">@string/appString</string>
          <string name="s2">@com.example.lib:string/libString</string>
        </resources>
      """.trimIndent(),
      true
    )

    myFixture.checkHighlighting()
  }

  fun testManifest() {
    runUndoTransparentWriteAction {
      myFacet.manifest!!.application.label.stringValue = "@string/libString"
    }

    refactorAndSync()

    myFixture.checkResult(
      "AndroidManifest.xml",
      """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="com.example.app">
            <application android:icon="@drawable/icon"
                android:label="@com.example.lib:string/libString">
            </application>
        </manifest>
      """.trimIndent(),
      true
    )
  }

  fun testCode() {
    myFixture.addFileToProject(
      "/res/values/app.xml",
      // language=xml
      """
        <resources>
          <string name="appString">Hello from app</string>
        </resources>
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "/src/com/example/app/MainActivity.java",
      // language=java
      """
        package com.example.app;

        import android.app.Activity;
        import android.os.Bundle;

        public class MainActivity extends Activity {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                getResources().getString(R.string.appString);
                getResources().getString(com.example.app.R.string.appString);

                getResources().getString(R.string.libString);
                getResources().getString(com.example.app.R.string.libString);
                getResources().getString(com.example.lib.R.string.libString);
            }
        }
      """.trimIndent()
    )

    refactorAndSync()

    myFixture.checkResult(
      "/src/com/example/app/MainActivity.java",
      // language=java
      """
        package com.example.app;

        import android.app.Activity;
        import android.os.Bundle;

        public class MainActivity extends Activity {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                getResources().getString(R.string.appString);
                getResources().getString(com.example.app.R.string.appString);

                getResources().getString(com.example.lib.R.string.libString);
                getResources().getString(com.example.lib.R.string.libString);
                getResources().getString(com.example.lib.R.string.libString);
            }
        }
      """.trimIndent(),
      true
    )
  }

  fun testGradleFiles() {
    // Make sure there's at least on reference to rewrite.
    runUndoTransparentWriteAction {
      myFacet.manifest!!.application.label.stringValue = "@string/libString"
    }

    myFixture.addFileToProject(
      "build.gradle",
      """
        android {
            compileSdkVersion 27

            defaultConfig {
                applicationId "com.example.app"
            }
        }
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "${getAdditionalModulePath("lib")}/build.gradle",
      """
        android {
            compileSdkVersion 27
        }
      """.trimIndent()
    )

    refactorAndSync()

    myFixture.checkResult(
      "build.gradle",
      """
        android {
            compileSdkVersion 27

            defaultConfig {
                applicationId "com.example.app"
            }
            aaptOptions {
                namespaced true
            }
        }
      """.trimIndent(),
      true
    )

    myFixture.checkResult(
      "${getAdditionalModulePath("lib")}/build.gradle",
      """
        android {
            compileSdkVersion 27
            aaptOptions {
                namespaced true
            }
        }
      """.trimIndent(),
      true
    )
  }

  /**
   * Runs the refactoring and changes the model to enable namespacing, like the sync would do.
   */
  private fun refactorAndSync() {
    MigrateToResourceNamespacesProcessor(myFacet).run()
    enableNamespacing(myFacet, "com.example.app")
    enableNamespacing(AndroidFacet.getInstance(getAdditionalModuleByName("lib")!!)!!, "com.example.lib")
  }
}
