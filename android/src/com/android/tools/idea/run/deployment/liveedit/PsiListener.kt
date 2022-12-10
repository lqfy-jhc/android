/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * @param file: Where the file event originated
 * @param origin: The most narrow PSI Element where the edit event occurred.
 * @param parentGroup: A list of all functions that encapsulate the origin of the event in the source code ordered by nesting level, from
 * innermost to outermost. This will be used to determine which compose groups to invalidate on the given change.
 */
data class EditEvent(val file: PsiFile,
                     val origin: KtElement,
                     val parentGroup: List<KtFunction>) {
  constructor(file: PsiFile, origin: KtElement) : this(file, origin, emptyList())
}

class PsiListener(val onPsiChanged: (EditEvent) -> Unit) : PsiTreeChangeListener {
  override fun childAdded(event: PsiTreeChangeEvent) = handleChangeEvent(event)
  override fun childRemoved(event: PsiTreeChangeEvent) = handleChangeEvent(event)
  override fun childReplaced(event: PsiTreeChangeEvent) = handleChangeEvent(event)
  override fun childrenChanged(event: PsiTreeChangeEvent) = handleChangeEvent(event)
  override fun childMoved(event: PsiTreeChangeEvent) = handleChangeEvent(event)
  override fun propertyChanged(event: PsiTreeChangeEvent) = handleChangeEvent(event)
  override fun beforeChildAddition(event: PsiTreeChangeEvent) = handleChangeEvent(event)
  override fun beforeChildRemoval(event: PsiTreeChangeEvent) = handleChangeEvent(event)
  override fun beforeChildReplacement(event: PsiTreeChangeEvent) = handleChangeEvent(event)
  override fun beforeChildMovement(event: PsiTreeChangeEvent) = handleChangeEvent(event)
  override fun beforeChildrenChange(event: PsiTreeChangeEvent) = handleChangeEvent(event)
  override fun beforePropertyChange(event: PsiTreeChangeEvent) = handleChangeEvent(event)

  @com.android.annotations.Trace
  private fun handleChangeEvent(psiEvent: PsiTreeChangeEvent) {
    // THIS CODE IS EXTREMELY FRAGILE AT THE MOMENT.
    // According to the PSI listener doc, there is no guarantee what events we get.
    // Changing a single variable name can result with a "replace" of the whole file.
    //
    // While this works "ok" for the most part, we need to figure out a better way to detect
    // the change is actually a function change somehow.

    if (psiEvent.file == null || psiEvent.file !is KtFile) {
      return
    }

    val file = psiEvent.file as KtFile
    var parent = psiEvent.parent;

    // The code might not be valid at this point, so we should not be making any
    // assumption based on the Kotlin language structure.

    while (parent != null) {
      if (parent is KtNamedFunction || parent is KtClass) {
        val event = EditEvent(file, parent as KtElement)
        handleEventIfValid(event)
        break;
      }

      if (parent is KtFunction) {
        val event = EditEvent(file, parent, getGroupParents(parent))
        handleEventIfValid(event)
        break;
      }
      parent = parent.parent
    }

    // This is a workaround to experiment with partial recomposition. Right now any simple edit would create multiple
    // edit events and one of them is usually a spurious whole file event that will trigger an unnecessary whole recompose.
    // For now we just ignore that event until Live Edit becomes better at diff'ing changes.
    if (!LiveEditAdvancedConfiguration.getInstance().usePartialRecompose) {
      // If there's no Kotlin construct to use as a parent for this event, use the KtFile itself as the parent.
      val event = EditEvent(file, file)
      handleEventIfValid(event)
    }
  }

  private fun handleEventIfValid(event: EditEvent) {
    // Drop any invalid events.
    // As mention in other parts of the code. The type of PSI event sent are really unpredictable. Intermediate events
    // sometimes contains event origins that is not valid or no longer exist in any file. In automatic mode this might not be a big
    // issue but in automatic mode, a single failing event can get merged into the big edit event which causes the single compiler
    // invocation to crash.
    if (!event.origin.isValid || event.origin.containingFile == null) {
      return
    }

    onPsiChanged(event)
  }

  private fun getGroupParents(function: KtFunction): List<KtNamedFunction> {
    // Record each unnamed function as part of the event until we reach a named function.
    // This will be used to determine how partial recomposition is done on this edit in a later stage.
    var groupParent = function.parent
    while (groupParent != null) {
      if (groupParent is KtNamedFunction) {
        return listOf(groupParent)
      }
      groupParent = groupParent.parent
    }
    return emptyList()
  }
}
