/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.android.synthetic.idea

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.psi.xml.XmlAttributeValue
import org.jetbrains.kotlin.android.KotlinAndroidTestCase
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.psi.KtElement

abstract class AbstractAndroidGotoTest : KotlinAndroidTestCase() {

    fun doTest(path: String) {
        copyResourceDirectoryForTest(path)
        val virtualFile = myFixture.copyFileToProject(path + getTestName(true) + ".kt", "src/" + getTestName(true) + ".kt")
        myFixture.configureFromExistingVirtualFile(virtualFile)

        val expression = TargetElementUtil.findReference(myFixture.editor, myFixture.caretOffset)!!.element as KtElement
        val resolvedCall = expression.resolveToCall()!!
        val property = resolvedCall.resultingDescriptor as? PropertyDescriptor ?: throw AssertionError("PropertyDescriptor expected")

        val targetElement = GotoDeclarationAction.findTargetElement(myFixture.project, myFixture.editor, myFixture.caretOffset)!!

        assert(targetElement is XmlAttributeValue) { "XmlAttributeValue expected, got ${targetElement::class.java}" }
        assertEquals("@+id/${property.name}", (targetElement as XmlAttributeValue).value)
    }
}