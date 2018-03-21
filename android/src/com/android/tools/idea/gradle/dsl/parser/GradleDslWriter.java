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
package com.android.tools.idea.gradle.dsl.parser;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.java.JavaVersionDslElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * A writer for BUILD.gradle files. Used to convert a modified {@link GradleBuildModel} back to the underlying file it was parsed from.
 *
 * The {@link GradleDslWriter} provides methods for subclasses of {@link GradleDslElement} to modify themselves on the underlying
 * file.
 *
 * Each subclass should create a subset of the following three methods to implement their functionality (replacing the * with the element's
 * name:
 * <p><ul>
 * <li>createDsl*  - Creates an element in the file from scratch. These return the created {@link PsiElement}.</li>
 * <li>applyDsl*  - Updates the existing {@link PsiElement}s given by {@link GradleDslElement#getPsiElement()} based on any changed
 *                  made to the {@link GradleDslElement}</li>.
 * <li>dataDsl*  - Deletes an existing {@link GradleDslElement} from the underlying file.</li>
 * </ul><p>
 *
 * Every {@link GradleDslElement} should be representable by only one {@link PsiElement}.
 *
 * This interface aims to allow the {@link GradleBuildModel} to support different languages, each language should have its
 * own implementation of both {@link GradleDslParser} and {@link GradleDslWriter}.
 *
 */
public interface GradleDslWriter {
  PsiElement createDslElement(@NotNull GradleDslElement element);

  void deleteDslElement(@NotNull GradleDslElement element);

  PsiElement createDslLiteral(@NotNull GradleDslLiteral literal);

  void applyDslLiteral(@NotNull GradleDslLiteral literal);

  void deleteDslLiteral(@NotNull GradleDslLiteral literal);

  PsiElement createDslReference(@NotNull GradleDslReference reference);

  void applyDslReference(@NotNull GradleDslReference reference);

  void deleteDslReference(@NotNull GradleDslReference reference);

  PsiElement createDslMethodCall(@NotNull GradleDslMethodCall methodCall);

  void applyDslMethodCall(@NotNull GradleDslMethodCall methodCall);

  PsiElement createDslExpressionList(@NotNull GradleDslExpressionList expressionList);

  PsiElement createDslExpressionMap(@NotNull GradleDslExpressionMap expressionMap);

  PsiElement createDslJavaVersionElement(@NotNull JavaVersionDslElement javaVersionDslElement);

  void applyDslJavaVersionElement(@NotNull JavaVersionDslElement javaVersionDslElement);

  void deleteDslJavaVersionElement(@NotNull JavaVersionDslElement javaVersionDslElement);

  class Adapter implements GradleDslWriter {
    @Override
    public PsiElement createDslElement(@NotNull GradleDslElement element) { return null; }

    @Override
    public void deleteDslElement(@NotNull GradleDslElement element) { }

    @Override
    public PsiElement createDslLiteral(@NotNull GradleDslLiteral literal) { return null; }

    @Override
    public void applyDslLiteral(@NotNull GradleDslLiteral literal) { }

    @Override
    public void deleteDslLiteral(@NotNull GradleDslLiteral literal) { }

    @Override
    public PsiElement createDslReference(@NotNull GradleDslReference reference) { return null; }

    @Override
    public void applyDslReference(@NotNull GradleDslReference reference) { }

    @Override
    public void deleteDslReference(@NotNull GradleDslReference reference) { }

    @Override
    public PsiElement createDslMethodCall(@NotNull GradleDslMethodCall methodCall) { return null; }

    @Override
    public void applyDslMethodCall(@NotNull GradleDslMethodCall methodCall) { }

    @Override
    public PsiElement createDslExpressionList(@NotNull GradleDslExpressionList expressionList) { return null; }

    @Override
    public PsiElement createDslExpressionMap(@NotNull GradleDslExpressionMap expressionMap) { return null; }

    @Override
    public PsiElement createDslJavaVersionElement(@NotNull JavaVersionDslElement javaVersionDslElement) { return null; }

    @Override
    public void applyDslJavaVersionElement(@NotNull JavaVersionDslElement javaVersionDslElement) { }

    @Override
    public void deleteDslJavaVersionElement(@NotNull JavaVersionDslElement javaVersionDslElement) { }
  }
}
