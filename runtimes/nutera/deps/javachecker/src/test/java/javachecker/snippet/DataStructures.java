package javachecker.snippet;

import java.util.Iterator;
import java.util.LinkedList;

public class DataStructures {

  public static void exampleOne(LinkedList<Integer> list) {
    Iterator<Integer> iterator = list.iterator();
    while (iterator.hasNext()) {
      Integer x = iterator.next();
      if (x < 0)
        iterator.remove();
    }
  }
  
  public static void differentBranchDifferentType(boolean b) {
    Number x = Integer.valueOf(10);
    while (x.intValue() > 0) {
      if (b)
        x = 0;
      else
        x = .0;
    }
  }
  
  static class A {
    int i = 0;
    
    public A() {
    }
    
    void inc() {
      i++;
    }
    
    int get() {
      return i;
    }
  }
  
  public static void sideEffect(int limit) {
    A a = new A();
    while (a.get() < limit) {
      a.inc();
    }
  }
  
  static class B {
    int i = 0;
    class C {
      void inc() {
        i++;
      }
    }
    
    C get() {
      return new C();
    }
  }
  
  public static void innerClass(int limit) {
    B b = new B();
    B.C c = b.get();
    while (b.i < limit) {
      c.inc();
    }
  }
  
  public static void first(int [] array, int x) {
    for (int i = 0; i < array.length; i++) {
      if (array[i] == x)
        break;
    }
  }
  
  public static void exampleZero(LinkedList<Integer> list) {
    Iterator<Integer> iterator = list.iterator();
    while (iterator.hasNext()) {
      iterator.next();
    }
  }
}
