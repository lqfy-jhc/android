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
package com.android.tools.idea.smali.parser;

import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class SmaliParserUtil extends GeneratedParserUtilBase {
  @NotNull
  public static String getJavaClassFqn(@NotNull PsiElement javaIdentifier) {
    String classDefinition = javaIdentifier.getText();
    if (classDefinition.startsWith("L") && classDefinition.endsWith(";")) {
      return classDefinition.substring(1, classDefinition.length() - 1).replace('/', '.');
    }
    return "";
  }
}
