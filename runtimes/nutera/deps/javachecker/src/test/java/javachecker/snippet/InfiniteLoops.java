package javachecker.snippet;

public class InfiniteLoops {

  public static void wrongWayRound(int i) {
    while (i < 42) {
      i--;
    }
  }   
}
