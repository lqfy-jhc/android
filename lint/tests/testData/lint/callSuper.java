package p1.p2;

import android.support.annotation.CallSuper;

@SuppressWarnings("UnusedDeclaration")
public class CallSuperTest {
  private static class Child extends Parent {
    @Override
    protected void <error descr="Overriding method should call `super.test1`">te<caret>st1</error>(String first, int second) {
    }
  }

  private static class Parent extends ParentParent {
    @Override
    protected void test1(String first, int second) {
      super.test1(first, second);
    }
  }

  private static class ParentParent extends ParentParentParent {
  }

  private static class ParentParentParent {
    @android.support.annotation.CallSuper
    protected void test1(String first, int second) {

    }

  }
}
