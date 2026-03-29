package javachecker.snippet;

public class Lexicographic {
 
  static void nestedLoopsCountingDown(int x, int y) {
    while (x > 0) {
      while (y > 0) {
        y--;
      }
      x--;
    }
  }
  
  static void nestedLoopsCountingUp(int x, int y) {
    int a = 0, b = 0;
    while (a < x) {
      b = 0;
      while (b < y) {
        b++;
      }
      a++;
    }
  }
  
  static void nestedLoopsCountingTriangular(int x) {
    int a = 0, b = 0;
    while (a < x) {
      b = 0;
      while (b < a) {
        b++;
      }
      a++;
    }
  }
  
  static void nestedLoopsFakeChange(int x, int y) {
    while (x > 0) {
      while (y > 0) {
        x++;
        y--;
        x--;
      }
      x--;
    }
  }
}
