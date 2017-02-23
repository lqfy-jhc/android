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
package com.android.tools.profilers.memory;

import com.android.tools.profilers.memory.adapters.MemoryObject;
import org.junit.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import java.util.Comparator;
import java.util.Enumeration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class MemoryObjectTreeNodeTest {

  @Test
  public void testAddChildNodeWithExistingParent() {
    MemoryObjectTreeNode<TestMemoryObject> node = new MemoryObjectTreeNode<>(new TestMemoryObject());
    MemoryObjectTreeNode<TestMemoryObject> child = new MemoryObjectTreeNode<>(new TestMemoryObject());
    MemoryObjectTreeNode<TestMemoryObject> existingParent = new MemoryObjectTreeNode<>(new TestMemoryObject());
    existingParent.add(child);
    assertEquals(existingParent, child.getParent());
    assertEquals(1, existingParent.getChildCount());
    node.add(child);
    assertEquals(node, child.getParent());
    assertEquals(1, node.getChildCount());
    assertEquals(child, node.getChildAt(0));
    assertEquals(0, existingParent.getChildCount());
  }

  @Test(expected = AssertionError.class)
  public void testChildNodeWithIncompatibleMemoryObjectCannotBeAdded() {
    MemoryObjectTreeNode<TestMemoryObject> node = new MemoryObjectTreeNode<>(new TestMemoryObject());
    MutableTreeNode child = new DefaultMutableTreeNode();
    node.insert(child, 0);
  }

  @Test
  public void testSortChildNodesEnumeration() {
    MemoryObjectTreeNode<TestMemoryObject> node = new MemoryObjectTreeNode<>(new TestMemoryObject());
    MemoryObjectTreeNode<TestMemoryObject> child1 = new MemoryObjectTreeNode<>(new TestMemoryObject(333));
    MemoryObjectTreeNode<TestMemoryObject> child2 = new MemoryObjectTreeNode<>(new TestMemoryObject(111));
    MemoryObjectTreeNode<TestMemoryObject> child3 = new MemoryObjectTreeNode<>(new TestMemoryObject(222));
    node.add(child1);
    node.add(child2);
    node.add(child3);
    Enumeration children = node.children();
    assertEquals(child1, children.nextElement());
    assertEquals(child2, children.nextElement());
    assertEquals(child3, children.nextElement());
    node.sort(Comparator.comparingInt(c -> c.getAdapter().getNum()));
    children = node.children();
    assertEquals(child2, children.nextElement());
    assertEquals(child3, children.nextElement());
    assertEquals(child1, children.nextElement());
  }

  @Test
  public void testSetParent() {
    MemoryObjectTreeNode<TestMemoryObject> node = new MemoryObjectTreeNode<>(new TestMemoryObject());
    MemoryObjectTreeNode<TestMemoryObject> parent = new MemoryObjectTreeNode<>(new TestMemoryObject());
    assertNull(node.getParent());
    node.setParent(parent);
    assertEquals(parent, node.getParent());
  }

  @Test
  public void testGetAdapter() {
    TestMemoryObject adapter = new TestMemoryObject();
    MemoryObjectTreeNode<TestMemoryObject> node = new MemoryObjectTreeNode<>(adapter);
    assertEquals(adapter, node.getAdapter());
  }

  @Test(expected = AssertionError.class)
  public void testGetPathToRootSelfCycleDetection() {
    MemoryObjectTreeNode<TestMemoryObject> selfCycle = new MemoryObjectTreeNode<>(new TestMemoryObject());
    selfCycle.myParent = selfCycle;
    selfCycle.getPathToRoot();
  }

  @Test(expected = AssertionError.class)
  public void testGetPathToRootCycleDetection() {
    MemoryObjectTreeNode<TestMemoryObject> node1 = new MemoryObjectTreeNode<>(new TestMemoryObject());
    MemoryObjectTreeNode<TestMemoryObject> node2 = new MemoryObjectTreeNode<>(new TestMemoryObject());
    MemoryObjectTreeNode<TestMemoryObject> node3 = new MemoryObjectTreeNode<>(new TestMemoryObject());
    node1.myParent = node2;
    node2.myParent = node3;
    node3.myParent = node1;
    node1.getPathToRoot();
  }

  private static class TestMemoryObject implements MemoryObject {
    private final int myNum;

    public TestMemoryObject() {
      this(-1);
    }

    public TestMemoryObject(int num) {
      myNum = num;
    }

    public int getNum() {
      return myNum;
    }
  }
}
