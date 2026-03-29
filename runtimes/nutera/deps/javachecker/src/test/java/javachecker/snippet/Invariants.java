package javachecker.snippet;

public class Invariants {

  public static void pastaA9(int x, int y, int z) {
    if (y > 0) {
      while (x >= z) {
        z += y;
      }
    }
  }

  public static int logMult(int x, int y) {
    int res = 1;
    if (x < 0 || y <= 1) return 0;
    else {
      while (x > y) { 
        y = y*y;
        res = 2*res;
      }
    }
    return res;
  }
  
  public static void pastaB8(int x) {
    if (x > 0) {
      while (x != 0) {
        if (x % 2 == 0) {
          x = x / 2;
        } else {
          x--;
        }
      }
    }
  }
  
  public static void pastaC5(int x, int y) {
    if (x > 0 && y > 0) {
      while (x != y) {
        if (x > y) {
          x = x - y;
        } else {
          y = y - x;
        }
      }
    }
  }
}
