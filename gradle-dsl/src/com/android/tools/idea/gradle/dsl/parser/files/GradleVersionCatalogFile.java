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
package com.android.tools.idea.gradle.dsl.parser.files;

import static com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral.LiteralType.REFERENCE;

import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

public class GradleVersionCatalogFile extends GradleDslFile {
  private final @NotNull String catalogName;

  GradleVersionCatalogFile(@NotNull VirtualFile file,
                           @NotNull Project project,
                           @NotNull String moduleName,
                           @NotNull String catalogName,
                           @NotNull BuildModelContext context) {
    super(file, project, moduleName, context);
    this.catalogName = catalogName;
  }

  public @NotNull String getCatalogName() {
    return catalogName;
  }

  @Override
  public void parse() {
    myGradleDslParser.parse();
    replaceVersionRefsWithInjections();
  }

  protected void replaceVersionRefsWithInjections() {
    GradleDslExpressionMap libraries = getPropertyElement("libraries", GradleDslExpressionMap.class);
    GradleDslExpressionMap plugins = getPropertyElement("plugins", GradleDslExpressionMap.class);
    GradleDslExpressionMap versions = getPropertyElement("versions", GradleDslExpressionMap.class);
    if (versions == null) return;
    Consumer<GradleDslExpressionMap> versionRefReplacer = (library) -> {
      GradleDslElement versionProperty = library.getPropertyElement("version");
      if (versionProperty instanceof GradleDslExpressionMap) {
        GradleDslExpressionMap version = (GradleDslExpressionMap)versionProperty;
        GradleDslElement refProperty = version.getPropertyElement("ref");
        if (refProperty instanceof GradleDslLiteral) {
          GradleDslLiteral ref = (GradleDslLiteral)refProperty;
          String targetName = ref.getValue(String.class);
          if (targetName != null) {
            GradleDslElement targetProperty = versions.getPropertyElement(targetName);
            if (targetProperty != null) {
              library.hideProperty(versionProperty);
              GradleDslLiteral reference =
                new GradleDslLiteral(library, version.getPsiElement(), versionProperty.getNameElement(), ref.getPsiElement(), REFERENCE);
              // TODO(xof): this pre-resolution of the injection is (probably) fine if we are happy with the changes in property
              //  visibility that implies.  If we wanted to avoid the surgery below, to make sure that dependencies are properly
              //  registered in both directions, we should be able to use a proper targetName (I think it should be
              //    `"versions." + targetName`
              //  so that the natural walk up the properties tree finds the correct element.)
              GradleReferenceInjection injection = new GradleReferenceInjection(reference, targetProperty, ref.getPsiElement(), targetName);
              targetProperty.registerDependent(injection);
              reference.addDependency(injection);
              library.addParsedElement(reference);
            }
          }
        }
      }
    };
    if (libraries != null) {
      libraries.getPropertyElements(GradleDslExpressionMap.class).forEach(versionRefReplacer);
    }
    if (plugins != null) {
      plugins.getPropertyElements(GradleDslExpressionMap.class).forEach(versionRefReplacer);
    }
  }
}
