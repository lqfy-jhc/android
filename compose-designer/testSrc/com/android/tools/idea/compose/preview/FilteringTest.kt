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
package com.android.tools.idea.compose.preview

import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThat
import org.junit.Test

class FilteringTest {
  @Test
  fun testGroupFiltering() {
    val groupPreviewProvider = GroupNameFilteredPreviewProvider(StaticPreviewProvider(listOf(
      SinglePreviewElementInstance.forTesting("com.sample.preview.TestClass.PreviewMethod1", "PreviewMethod1", "GroupA"),
      SinglePreviewElementInstance.forTesting("com.sample.preview.TestClass.PreviewMethod2", "PreviewMethod2", "GroupA"),
      SinglePreviewElementInstance.forTesting("com.sample.preview.TestClass.PreviewMethod3", "PreviewMethod3", "GroupB"),
      SinglePreviewElementInstance.forTesting("com.sample.preview.TestClass.PreviewMethod4", "PreviewMethod4")
    )))

    // No filtering at all
    assertEquals(4, groupPreviewProvider.previewElements.count())
    assertThat(groupPreviewProvider.availableGroups, `is`(setOf("GroupA", "GroupB")))

    // An invalid group should return all the items
    groupPreviewProvider.groupName = "InvalidGroup"
    assertThat(groupPreviewProvider.previewElements.map { it.displaySettings.name }.toList(),
               `is`(listOf("PreviewMethod1", "PreviewMethod2", "PreviewMethod3", "PreviewMethod4")))

    groupPreviewProvider.groupName = "GroupA"
    assertThat(groupPreviewProvider.previewElements.map { it.displaySettings.name }.toList(),
               `is`(listOf("PreviewMethod1", "PreviewMethod2")))

    groupPreviewProvider.groupName = "GroupB"
    assertEquals("PreviewMethod3", groupPreviewProvider.previewElements.map { it.displaySettings.name }.single())

    groupPreviewProvider.groupName = null
  }

  @Test
  fun testSingleElementFiltering() {
    val singleElementProvider = SinglePreviewElementFilteredPreviewProvider(StaticPreviewProvider(listOf(
      SinglePreviewElementInstance.forTesting("com.sample.preview.TestClass.PreviewMethod1", "PreviewMethod1", "GroupA"),
      SinglePreviewElementInstance.forTesting("com.sample.preview.TestClass.PreviewMethod2", "PreviewMethod2", "GroupA"),
      SinglePreviewElementInstance.forTesting("com.sample.preview.TestClass.PreviewMethod3", "PreviewMethod3", "GroupB"),
      SinglePreviewElementInstance.forTesting("com.sample.preview.TestClass.PreviewMethod4", "PreviewMethod4")
    )))

    // No filtering at all
    assertEquals(4, singleElementProvider.previewElements.count())

    // An invalid group should return all the items
    singleElementProvider.composableMethodFqn = "com.notvalid.NotValid"
    assertEquals(4, singleElementProvider.previewElements.count())

    singleElementProvider.composableMethodFqn = "com.sample.preview.TestClass.PreviewMethod3"
    assertEquals("PreviewMethod3", singleElementProvider.previewElements.single().displaySettings.name)
    singleElementProvider.composableMethodFqn = "com.sample.preview.TestClass.PreviewMethod1"
    assertEquals("PreviewMethod1", singleElementProvider.previewElements.single().displaySettings.name)
  }
}