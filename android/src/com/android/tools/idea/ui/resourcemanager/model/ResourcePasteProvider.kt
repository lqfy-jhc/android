/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.model

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.templates.TemplateUtils
import com.android.tools.idea.ui.resourcemanager.ResourceManagerTracking
import com.android.tools.idea.util.dependsOnAppCompat
import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.util.ensureNamespaceImported
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypesAndPredicate
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Extension [PasteProvider] to handle paste of [java.awt.datatransfer.Transferable] providing [RESOURCE_URL_FLAVOR] in
 * intelliJ editors.
 */
class ResourcePasteProvider : PasteProvider {

  private val IMAGE_LIKE_TYPES = setOf<ResourceType>(ResourceType.DRAWABLE, ResourceType.MIPMAP, ResourceType.COLOR)

  override fun performPaste(dataContext: DataContext) {
    val caret = CommonDataKeys.CARET.getData(dataContext)!!
    val psiFile = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return
    val psiElement = runReadAction { psiFile.findElementAt(caret.offset) }

    when (psiFile.fileType) {
      StdFileTypes.XML -> performForXml(psiElement, dataContext, caret)
      StdFileTypes.JAVA -> performForJavaCode(dataContext, caret)
      KotlinFileType.INSTANCE -> performForKotlinCode(psiElement, dataContext, caret)
    }
  }

  private fun performForKotlinCode(psiElement: PsiElement?,
                                   dataContext: DataContext,
                                   caret: Caret) {
    val resourceUrl = getResourceUrl(dataContext) ?: return
    if (psiElement == null) return
    val resourceCodeReference = resourceUrl.resourceCodeReference
    val argumentParent = psiElement.getParentOfTypesAndPredicate(false, PsiElement::class.java) { it is ValueArgument }
    if (argumentParent != null) {
      processForMethodValue(argumentParent, caret, resourceCodeReference)
      // TODO(143899540): Update tracking, to differentiate when pasting on code files.
      ResourceManagerTracking.logPasteUrlText(resourceUrl.type)
      return
    }
    val methodCallParent = psiElement.getParentOfTypesAndPredicate(false, PsiElement::class.java) { it is KtCallExpression }
    if (methodCallParent != null) {
      processForMethodCall(methodCallParent, caret, resourceCodeReference)
      // TODO(143899540): Update tracking, to differentiate when pasting on code files.
      ResourceManagerTracking.logPasteUrlText(resourceUrl.type)
      return
    }
    pasteAtCaret(caret, resourceCodeReference, resourceUrl.type)
  }

  private fun performForJavaCode(dataContext: DataContext, caret: Caret) {
    val resourceUrl = getResourceUrl(dataContext) ?: return
    val resourceCodeReference = resourceUrl.resourceCodeReference
    // TODO: Add proper support for Java files
    pasteAtCaret(caret, resourceCodeReference, resourceUrl.type)
  }

  /**
   * Perform the paste in an XML file context.
   * The paste operation will be different depending on the psiElement under the caret.
   *
   * For example, if the caret is within an ImageView tag, the `src` attribute will be populated with the
   * pasted [ResourceUrl].
   */
  private fun performForXml(psiElement: PsiElement?,
                            dataContext: DataContext,
                            caret: Caret) {
    val resourceUrl = getResourceUrl(dataContext)
    val resourceReference = resourceUrl?.toString() ?: return

    if (psiElement is PsiWhiteSpace) {
      if (getFolderType(psiElement.containingFile) == ResourceFolderType.LAYOUT) {
        // TODO: Should figure out a way to have a more consistent behavior with the Drag Target in Layout Editor.
        ResourceManagerTracking.logPasteOnBlank(resourceUrl.type)
        if (IMAGE_LIKE_TYPES.contains(resourceUrl.type)) {
          insertImageView(resourceReference, psiElement, caret, isFixedDimension = resourceUrl.type == ResourceType.COLOR)
          return
        }
        else if (resourceUrl.type == ResourceType.LAYOUT) {
          insertIncludeTag(resourceReference, psiElement, caret)
          return
        }
      }
    }

    if (psiElement !is XmlElement) {
      pasteAtCaret(caret, resourceReference, resourceUrl.type)
      return
    }

    val xmlAttributeValue = psiElement.parentOfType<XmlAttributeValue>()
    if (processForValue(xmlAttributeValue, caret, resourceReference)) {
      ResourceManagerTracking.logPasteOnXmlAttribute(resourceUrl.type)
      return
    }

    val xmlAttribute = psiElement.parentOfType<XmlAttribute>()
    if (processForAttribute(xmlAttribute, caret, resourceReference)) {
      ResourceManagerTracking.logPasteOnXmlAttribute(resourceUrl.type)
      return
    }

    val xmlTag = psiElement.parentOfType<XmlTag>()
    if (processForTag(xmlTag, caret, resourceReference)) {
      ResourceManagerTracking.logPasteOnXmlTag(resourceUrl.type)
      return
    }

    pasteAtCaret(caret, resourceReference, resourceUrl.type)
  }

  private fun insertIncludeTag(resourceReference: String, psiElement: PsiElement, caret: Caret) {
    val parent = psiElement.parentOfType<XmlTag>() ?: return
    val before = parent.children.last { it.textRange.startOffset < caret.offset }

    runWriteAction {
      val childTag = parent.addAfter(parent.createChildTag(SdkConstants.TAG_INCLUDE, parent.namespace, null, false), before) as XmlTag
      with(childTag) {
        setAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.ANDROID_URI, SdkConstants.VALUE_WRAP_CONTENT)
        setAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.ANDROID_URI, SdkConstants.VALUE_WRAP_CONTENT)
        setAttribute(SdkConstants.ATTR_LAYOUT, resourceReference)
        collapseIfEmpty()
        TemplateUtils.reformatAndRearrange(parent.project, this)
      }
    }
  }

  private fun insertImageView(resourceReference: String, psiElement: PsiElement, caret: Caret, isFixedDimension: Boolean) {
    val parent = psiElement.parentOfType<XmlTag>() ?: return
    val dependsOnAppCompat = dependsOnAppCompat(parent)

    val before = parent.children.last { it.textRange.startOffset < caret.offset }

    val dimensionValue = if (isFixedDimension) "50${SdkConstants.UNIT_DP}" else SdkConstants.VALUE_WRAP_CONTENT

    runWriteAction {
      val childTag = parent.addAfter(parent.createChildTag(SdkConstants.IMAGE_VIEW, parent.namespace, null, false), before) as XmlTag
      with(childTag) {
        setAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.ANDROID_URI, dimensionValue)
        setAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.ANDROID_URI, dimensionValue)
        setSrcAttribute(dependsOnAppCompat, this, resourceReference)
        collapseIfEmpty()
        TemplateUtils.reformatAndRearrange(parent.project, this)
      }
    }
  }

  private fun processForTag(xmlTag: XmlTag?,
                            caret: Caret,
                            resourceReference: String): Boolean {
    return when (xmlTag?.name) {
      SdkConstants.IMAGE_VIEW -> performForImageView(xmlTag, resourceReference)
      else -> false
    }
  }

  private fun processForAttribute(xmlAttribute: XmlAttribute?,
                                  caret: Caret,
                                  resourceReference: String): Boolean {
    if (xmlAttribute == null) return false
    runWriteAction {
      xmlAttribute.setValue(resourceReference)
    }
    xmlAttribute.valueElement?.valueTextRange?.startOffset?.let {
      caret.selectStringFromOffset(resourceReference, it)
    }
    return true
  }

  private fun processForMethodCall(value: PsiElement, caret: Caret, resourceReference: String) {
    val methodCallElement = value as KtCallExpression
    val resourceReferenceArgument = KtPsiFactory(value.project).createArgument(resourceReference)
    val resultingArgument = runWriteAction {
      methodCallElement.valueArgumentList?.addArgument(resourceReferenceArgument)
    }
    (resultingArgument as? PsiElement)?.let {
      caret.selectStringFromOffset(resourceReference, it.startOffset)
    }
  }

  private fun processForMethodValue(value: PsiElement, caret: Caret, resourceReference: String) {
    val rangeOffset = value.textRange
    runWriteAction {
      caret.editor.document.replaceString(rangeOffset.startOffset, rangeOffset.endOffset, resourceReference)
    }
    caret.selectStringFromOffset(resourceReference, rangeOffset.startOffset)
  }

  private fun processForValue(xmlAttributeValue: XmlAttributeValue?,
                              caret: Caret,
                              resourceReference: String): Boolean {
    if (xmlAttributeValue == null) return false
    processForAttribute(xmlAttributeValue.parent as? XmlAttribute, caret, resourceReference)
    return true
  }

  private fun pasteAtCaret(caret: Caret,
                           resourceReference: String,
                           type: ResourceType? = null) {
    runWriteAction {
      caret.editor.document.insertString(caret.offset, resourceReference)
    }
    caret.selectStringFromOffset(resourceReference, caret.offset)
    ResourceManagerTracking.logPasteUrlText(type)
  }

  private fun replaceAtCaret(caret: Caret, psiElement: PsiElement, resourceReference: String, type: ResourceType? = null) {
    runWriteAction {
      caret.editor.document.replaceString(psiElement.textRange.startOffset, psiElement.textRange.endOffset, resourceReference)
    }
    caret.selectStringFromOffset(resourceReference, psiElement.textRange.startOffset)
    ResourceManagerTracking.logPasteUrlText(type)
  }

  private fun Caret.selectStringFromOffset(resourceReference: String, offset: Int) {
    setSelection(offset, offset + resourceReference.length)
    moveToOffset(offset + resourceReference.length)
  }

  /**
   * Set the src attribute for an ImageView [xmlTag] with the provided [resourceReference].
   * This method will use the app compat attributes if the module is using AppCompat
   */
  private fun performForImageView(xmlTag: XmlTag, resourceReference: String): Boolean {
    val dependsOnAppCompat = dependsOnAppCompat(xmlTag)
    runWriteAction {
      setSrcAttribute(dependsOnAppCompat, xmlTag, resourceReference)
      TemplateUtils.reformatAndRearrange(xmlTag.project, xmlTag)
    }
    return true
  }

  private fun setSrcAttribute(dependsOnAppCompat: Boolean, xmlTag: XmlTag, resourceReference: String) {
    if (dependsOnAppCompat) {
      (xmlTag.containingFile as? XmlFile)?.let {
        ensureNamespaceImported(it, SdkConstants.AUTO_URI)
      }
      xmlTag.setAttribute(SdkConstants.ATTR_SRC_COMPAT, SdkConstants.AUTO_URI, resourceReference)
      xmlTag.setAttribute(SdkConstants.ATTR_SRC, SdkConstants.ANDROID_URI, null)
    }
    else {
      xmlTag.setAttribute(SdkConstants.ATTR_SRC_COMPAT, SdkConstants.AUTO_URI, null)
      xmlTag.setAttribute(SdkConstants.ATTR_SRC, SdkConstants.ANDROID_URI, resourceReference)
    }
  }

  private fun dependsOnAppCompat(xmlTag: XmlTag) =
    runReadAction { ModuleUtilCore.findModuleForPsiElement(xmlTag)?.dependsOnAppCompat() } == true

  private fun getResourceUrl(dataContext: DataContext): ResourceUrl? =
    PasteAction.TRANSFERABLE_PROVIDER.getData(dataContext)
      ?.produce()
      ?.getTransferData(RESOURCE_URL_FLAVOR) as ResourceUrl?

  override fun isPastePossible(dataContext: DataContext): Boolean {
    return PasteAction.TRANSFERABLE_PROVIDER.getData(dataContext)
             ?.produce()
             ?.isDataFlavorSupported(RESOURCE_URL_FLAVOR) ?: false
  }

  override fun isPasteEnabled(dataContext: DataContext) = isPastePossible(dataContext)

  /** The most common use case to use resources on code: 'namespace.R.type.name' or just 'R.type.name' for app resources. */
  private val ResourceUrl.resourceCodeReference get() = "${this.namespace?.let { "$it." } ?: ""}R.${this.type}.${this.name}"
}