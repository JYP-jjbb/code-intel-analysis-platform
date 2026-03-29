package javachecker.snippet;

public class ControlFlow {

  public static void oneLoopTwoBranches() {
    int i = 10, j = 10;
    while (i > 0) {
      if (j > 0) {
        i--;
      } else {
        j--;
      }
      if (j > 0) {
        // skip();
      } else {
        i--;
      }
    }
  }

  public static void oneLoopCountUpOrDown() {
    int i = 10, j = 10;
    while (i > 0) {
      if (i - j > 10) {
        i--;
      } else {
        j++;
      }
    }
  }

  public static void twoIntricatedLoops() {
    int i = 10, j = 10;
    a: while (i > 0) {
      i--;
      while (j > 0) {
        if (i > 5)
          continue a;
        if (i < 2)
          break a;
        j--;
      }
    }
  }

  public static void twoClutteredLoops() {
    int i = 10, j = 10;
    while (i > 0) while (j > 0) { i--; j--; }
  }

  public static void simple(int i) {
    while (i > 0) {
      i--;
    }
  }
  
  public static void countDownAndThrow(int i) throws Throwable {
    while (true) {
      i--;
      if (i < 0)
        throw new Throwable();
    }
  }
  
  public static int decreaseOrThrow(int i) throws Exception {
    if (i < 0)
      throw new Exception("done!");
    else
      return i - 1;
  }
  
  public static void countDownCatching(int i) {
    while (true) {
      try {
        i = decreaseOrThrow(i);
      } catch (Exception e) {
        break;
      } catch (Throwable e) {
        continue;
      }
    }
  }
  
  public static void sequentialWhiles(int i, int j) {
    while (i > 0) {
      i--;
    }
    while (j < 42) {
      j++;
    }
  }

  public static void sequentialFors() {
    for (int i = 0; i < 42; i++) {
    }

    for (int j = 5; j < 24; j += 3) {
    }
  }
  
  public static int dec(int a) { return a - 1; }

  public static void oneLoopFunctionCall(int i) {
    while (i > 10) {
      i = dec(i);
    }
  }

  public static void skip() {} ;
  
  public static void oneLoopNoop(int i) {
    while (i > 10) {
      skip();
      i--;
    }
  }
}
