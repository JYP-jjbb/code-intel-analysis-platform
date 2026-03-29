package javachecker;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import org.junit.Test;

import static org.junit.Assert.*;

public class TerminationTest {

  static String snippetpackage = "javachecker.snippet";
  
  static int lineToLoopHeaderOffset(String className, String methodName, int line) throws NoSuchMethodException, ClassNotFoundException {
      Map<Integer, SortedSet<Integer>> lineToOffset = 
          CFGAnalyzer.lineToLabelOffset(className, methodName);
      List<Integer> heads = CFGAnalyzer.loopHeaders(className, methodName);
      assert lineToOffset.get(line) != null;
      heads.retainAll(lineToOffset.get(line));
      assert heads.size() == 1;
      return heads.get(0);
  }
  
  static List<Integer> lineToLoopHeaderOffsets(String className, String methodName, int... lines) throws NoSuchMethodException, ClassNotFoundException {
    return CFGAnalyzer.loopHeaders(className, methodName);
  }
  
  @Test
  public void simple() throws Exception {
    String className = snippetpackage + ".ControlFlow";
    int offset = lineToLoopHeaderOffset(className, "simple", 52);
    List<Integer> ranks = List.of(1, 0);
    Boolean[] res = RankChecker.check(className, "simple", offset, List.of("i"), ranks);
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }
  
  @Test
  public void oneLoopTwoBranchesWithGoodRanking() throws Exception {
    String className = snippetpackage + ".ControlFlow";
    int offset = lineToLoopHeaderOffset(className, "oneLoopTwoBranches", 7);
    List<Integer> ranks = List.of(1, 0, 0);
    Boolean[] res = RankChecker.check(className, "oneLoopTwoBranches", offset, List.of("i", "j"), ranks);
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }

  @Test
  public void oneLoopTwoBranchesWithBadRanking() throws Exception {
    String className = snippetpackage + ".ControlFlow";
    int offset = lineToLoopHeaderOffset(className, "oneLoopTwoBranches", 7);
    List<Integer> ranks = List.of(0, 1, 0);
    Boolean[] res = RankChecker.check(className, "oneLoopTwoBranches", offset, List.of("i", "j"), ranks);
    assertFalse(res[0] && res[1]);
  }
  
  @Test
  public void oneLoopCountUpOrDownWithBadRanking() throws Exception {
    String className = snippetpackage + ".ControlFlow";
    int offset = lineToLoopHeaderOffset(className, "oneLoopCountUpOrDown", 23);
    List<Integer> ranks = List.of(1, 0, 0);
    Boolean[] res = RankChecker.check(className, "oneLoopCountUpOrDown", offset, List.of("i", "j"), ranks);
    assertFalse(res[0]);
  }
  
  @Test
  public void oneLoopCountUpOrDownWithAlmostGoodRanking() throws Exception {
    //It is ALMOST good because it requires an upper-bound for j (extra invariant) to be well-founded
    String className = snippetpackage + ".ControlFlow";
    int offset = lineToLoopHeaderOffset(className, "oneLoopCountUpOrDown", 23);
    List<Integer> ranks = List.of(1, -1, 0);
    Boolean[] res = RankChecker.check(className, "oneLoopCountUpOrDown", offset, List.of("i", "j"), ranks);
    assertTrue(res[0]);
    assertFalse(res[1]); 
  }
  
  @Test
  public void oneLoopFunctionCall() throws Exception {
    String className = snippetpackage + ".ControlFlow";
    int offset = lineToLoopHeaderOffset(className, "oneLoopFunctionCall", 104);
    List<Integer> ranks = List.of(1, 0);
    Boolean[] res = RankChecker.check(className, "oneLoopFunctionCall", offset, List.of("i"), ranks);
    assertTrue(res[0]);
    assertTrue(res[1]);
  }
  
  @Test
  public void simpleDecreasingSumWithGoodRanking() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "decreasingSum", 6);
    List<Integer> ranks = List.of(1, 1, 0);
    Boolean[] res = RankChecker.check(className, "decreasingSum", offset, List.of("i", "j"), ranks);
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }

  @Test
  public void decIncDecWithGoodRanking() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "decIncDec", 16);
    List<Integer> ranks = List.of(1, 0);
    Boolean[] res = RankChecker.check(className, "decIncDec", offset, List.of("i"), ranks);
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }
  
  @Test
  public void sequentialWhilesWithGoodRank() throws Exception {
    String className = snippetpackage + ".ControlFlow";
    int offset1 = lineToLoopHeaderOffset(className, "sequentialWhiles", 85);
    int offset2 = lineToLoopHeaderOffset(className, "sequentialWhiles", 88);

    Boolean[] res = RankChecker.check(className, "sequentialWhiles", offset1, 
        List.of("i", "j"), List.of(1, 0, 0));
    assertTrue(res[0] && res[1]);
    res = RankChecker.check(className, "sequentialWhiles", offset2, 
        List.of("i", "j"), List.of(0, -1, +100));
    assertTrue(res[0] && res[1]);
  }

//  @Test
//  public void sequentialForsWithGoodRank() throws Exception {
//    String className = snippetpackage + ".ControlFlow";
//    int offset1 = lineToLoopHeaderOffset(className, "sequentialFors", 94);
//    int offset2 = lineToLoopHeaderOffset(className, "sequentialFors", 97);
//
//    // note that i and j correspond to the same local variable in the stack
//    Boolean[] res = RankChecker.check(className, "sequentialFors", offset1, 
//        List.of("i"), List.of(-1, +42));
//    assertTrue(res[0] && res[1]);
//    res = RankChecker.check(className, "sequentialFors", offset2, 
//        List.of("j"), List.of(-1, +100));
//    assertTrue(res[0] && res[1]);
//  }

  @Test
  public void pastaB8WithGoodRank() throws Exception {
    // uses invariant x >= 0
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "pastaB8", 70);
    Boolean[] res = RankChecker.check(className, "pastaB8", offset, List.of("x"), List.of(1, 0));
    assertTrue(res[0]);
  }

  @Test
  public void pastaB12WithMissingInvariantAndBadRank() throws Exception {
    // this test fails because x + y is not a ranking function, 
    // unless x >= 0 and y >= 0 is guaranteed. Note that, variables initialisation is nondet
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "pastaB12", 53);
    Boolean[] res = RankChecker.check(className, "pastaB12", offset, List.of("x", "y"), List.of(1, 1, 0));
    assertTrue("not decresing", res[0]);
    assertFalse(res[1]);
  }

  @Test
  public void pastaB6WithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "pastaB6", 63);
    Boolean[] res = RankChecker.check(className, "pastaB6", offset, List.of("x", "y"), List.of(1, 1, 0));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);

  }

  @Test
  public void pastaB1WithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "pastaB1", 48);
    Boolean[] res = RankChecker.check(className, "pastaB1", offset, List.of("x", "y"), List.of(1, -1, 0));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }

  @Test
  public void otherWayRound() throws Exception {
    String className = snippetpackage + ".InfiniteLoops";
    String methodName = "wrongWayRound";
    int offset = lineToLoopHeaderOffset(className, methodName, 6);
    Boolean[] res = RankChecker.check(className, methodName, offset, List.of("i"), List.of(1, 0));
    assertFalse(res[0] && res[1]);
  }


  @Test
  public void countDownIntoNegativeWithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "countDownIntoNegative", 31);
    Boolean[] res = RankChecker.check(className, "countDownIntoNegative", offset, List.of("i"), List.of(1, 42));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }
  
  @Test
  public void countUpWithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "countUp", 25);
    Boolean[] res = RankChecker.check(className, "countUp", offset, List.of("i"), List.of(-1, 100));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }

  @Test
  public void countUpWithPossiblyNegativeRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "countUp", 25);
    Boolean[] res = RankChecker.check(className, "countUp", offset, List.of("i"), List.of(-1, 99));
    assertFalse(res[0] && res[1]);
  }

  @Test
  public void divMinusWithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "divMinus", 39);
    Boolean[] res = RankChecker.check(className, "divMinus", offset, List.of("x", "y", "res"), List.of(1, 0, 0, 0));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }

//  @Test
//  public void firstWithGoodRank() throws Exception {
//    String className = snippetpackage + ".Arrays";
//    int offset = lineToLoopHeaderOffset(className, "first", 6);
//    Boolean[] res = RankChecker.check(className, "first", offset, List.of("array.length", "x", "i"), List.of(1, 0, -1, 0));
//    assertTrue("not decresing", res[0]);
//    assertTrue("not bounded below", res[1]);
//  }
  
  @Test
  public void oneLoopNoop() throws Exception {
    String className = snippetpackage + ".ControlFlow";
    int offset = lineToLoopHeaderOffset(className, "oneLoopNoop", 112);
    Boolean[] res = RankChecker.check(className, "oneLoopNoop", offset, List.of("i"), List.of(1, 0));
    assertTrue(res[0]);
    assertTrue(res[1]);
  }
  
//  @Test
//  public void exampleOne() throws Exception {
//    String className = snippetpackage + ".DataStructures";
//    int offset = lineToLoopHeaderOffset(className, "exampleOne", 10);
//    Boolean[] res = RankChecker.check(className, "exampleOne", offset, List.of("list.size", "iterator.nextIndex", "x"), List.of(1, -1, 0, 0));
//    assertTrue(res[0]);
//    assertTrue(res[1]);
//  }
  
  @Test
  public void sideEffect() throws Exception {
    String className = snippetpackage + ".DataStructures";
    int offset = lineToLoopHeaderOffset(className, "sideEffect", 44);
    Boolean[] res = RankChecker.check(className, "sideEffect", offset, List.of("a.i", "limit"), List.of(-1, 1, 0));
    assertTrue(res[0]);
    assertTrue(res[1]);
  }
  
//  @Test
//  public void exampleZero() throws Exception {
//    String className = snippetpackage + ".DataStructures";
//    int offset = lineToLoopHeaderOffset(className, "exampleZero", 79);
//    Boolean[] res = RankChecker.check(className, "exampleZero", offset, List.of("list.size", "iterator.nextIndex"), List.of(1, -1, 0));
//    assertTrue(res[0]);
//    assertTrue(res[1]);
//  }
  
  @Test
  public void innerClass() throws Exception {
    String className = snippetpackage + ".DataStructures";
    int offset = lineToLoopHeaderOffset(className, "innerClass", 65);
    Boolean[] res = RankChecker.check(className, "innerClass", offset, List.of("b.i", "limit"), List.of(-1, 1, 0));
    assertTrue(res[0]);
    assertTrue(res[1]);
  }

  @Test
  public void innerClassWithBadRank() throws Exception {
    String className = snippetpackage + ".DataStructures";
    int offset = lineToLoopHeaderOffset(className, "innerClass", 65);
    Boolean[] res = RankChecker.check(className, "innerClass", offset, List.of("b.i", "limit"), List.of(-1, 0, 0));
    assertTrue(res[0]);
    assertFalse(res[1]);
  }
//  
//  @Test
//  public void countDownAndThrowWithGoodRank() throws Exception {
//    String className = snippetpackage + ".ControlFlow";
//    int offset = lineToLoopHeaderOffset(className, "countDownAndThrow", 59);
//    List<Integer> ranks = Map.of(offset, List.of(1, 0));
//    Boolean[] res = RankChecker.check(className, "countDownAndThrow", ranks);
//    assertTrue("not decresing", res[0]);
//    assertTrue("not bounded below", res[1]);
//  }
//  
//  @Test
//  public void countDownCatchingWithGoodRank() throws Exception {
//    String className = snippetpackage + ".ControlFlow";
//    int offset = lineToLoopHeaderOffset(className, "countDownCatching", 75);
//    List<Integer> ranks = Map.of(offset, List.of(1, 0));
//    Boolean[] res = RankChecker.check(className, "countDownCatching", ranks);
//    assertTrue("not decresing", res[0]);
//    assertTrue("not bounded below", res[1]);
//  }
  
  @Test
  public void countUpToLimitWithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "countUpToLimit", 83);
    Boolean[] res = RankChecker.check(className, "countUpToLimit", offset, List.of("i", "limit"), List.of(-1, 1, 0));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }
  
  @Test
  public void gcdWithNoRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "gcd", 89);
    Boolean[] res = RankChecker.check(className, "gcd", offset, List.of("a", "b", "tmp"), List.of(0, 0, 0, 0));
    assertFalse("not decresing", res[0]);
    assertFalse("not bounded below", res[1]);
  }

  @Test
  public void pastaC3withGoodReluRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "pastaC3", 98);
    Boolean[] res = RankChecker.checkRelu(className, "pastaC3", offset, List.of("x", "y", "z"), 
        List.of(List.of(-1, 1, 0, 0), List.of(0, 1, -1, 0)));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }
  
  @Test
  public void pastaC3withBadReluRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "pastaC3", 98);
    Boolean[] res = RankChecker.checkRelu(className, "pastaC3", offset, List.of("x", "y", "z"), 
        List.of(List.of(-1, 0, 1, 0), List.of(0, 1, -1, 0)));
    assertFalse("not decresing", res[0]);
  }
  
  @Test
  public void pastaC3withBadRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "pastaC3", 98);
    Boolean[] res = RankChecker.check(className, "pastaC3", offset, List.of("x", "y", "z"), List.of(-1, 2, -1, 0));
    assertTrue("not decresing", res[0]);
    assertFalse("not bounded below", res[1]);
  }
  
  @Test
  public void pastaA9WithGoodRank() throws Exception {
    String className = snippetpackage + ".Invariants";
    int offset = lineToLoopHeaderOffset(className, "pastaA9", 7);
    Boolean[] res = RankChecker.check(className, "pastaA9", offset, List.of("x", "y", "z"), List.of(1, 0, -1, 0));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }
  
  @Test
  public void pastaA9WithAnotherGoodRank() throws Exception {
    String className = snippetpackage + ".Invariants";
    int offset = lineToLoopHeaderOffset(className, "pastaA9", 7);
    Boolean[] res = RankChecker.check(className, "pastaA9", offset, List.of("x", "y", "z"), List.of(1, 1, -1, 0));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
    assertTrue("should require invar", res[2]);
  }
  
  @Test
  public void logMultWithGoodRank() throws Exception {
    String className = snippetpackage + ".Invariants";
    int offset = lineToLoopHeaderOffset(className, "logMult", 17);
    Boolean[] res = RankChecker.check(className, "logMult", offset, List.of("x", "y"), List.of(1, -1, 0));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }
  
  @Test
  public void pastaB12WithGoodNonLinearRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "pastaB12", 53);
    Boolean[] res = RankChecker.checkRelu(className, "pastaB12", offset, List.of("x", "y"), List.of(List.of(1, 0, 0), List.of(0, 1, 0)));
    assertTrue("not decresing", res[0]);
    assertTrue(res[1]);
  }
  
  @Test
  public void pastaC5WithGoodRank() throws Exception {
    String className = snippetpackage + ".Invariants";
    int offset = lineToLoopHeaderOffset(className, "pastaC5", 39);
    Boolean[] res = RankChecker.checkRelu(className, "pastaC5", offset, List.of("x", "y"), List.of(List.of(1, 1, 0)));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }
  
  @Test
  public void HHLP13Fig5WithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "HHLP13Fig5", 112);
    Boolean[] res = RankChecker.checkRelu(className, "HHLP13Fig5", offset, List.of("x"), List.of(List.of(1, 1)));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }
  
  @Test
  public void nestedLoopsCountingDownWithGoodRank() throws Exception {
    String className = snippetpackage + ".Lexicographic";
    List<Integer> heads = lineToLoopHeaderOffsets(className, "nestedLoopsCountingDown", 6, 7);
    Boolean[] res = RankChecker.checkLexiRelu(className, "nestedLoopsCountingDown", heads, List.of("x", "y"), 
        List.of(List.of(List.of(1, 0, 0)), List.of(List.of(0, 1, 0))));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }
  
  @Test
  public void nestedLoopsCountingUpWithGoodRank() throws Exception {
    String className = snippetpackage + ".Lexicographic";
    List<Integer> heads = lineToLoopHeaderOffsets(className, "nestedLoopsCountingUp", 16, 18);
    Boolean[] res = RankChecker.checkLexiRelu(className, "nestedLoopsCountingUp", heads, List.of("x", "y", "a", "b"), 
        List.of(List.of(List.of(1, 0, -1, 0, 0)), List.of(List.of(0, 1, 0, -1, 0))));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }
  
  @Test
  public void nestedLoopsCountingUpWithBadRank() throws Exception {
    String className = snippetpackage + ".Lexicographic";
    List<Integer> heads = lineToLoopHeaderOffsets(className, "nestedLoopsCountingUp", 16, 18);
    Boolean[] res = RankChecker.checkLexiRelu(className, "nestedLoopsCountingUp", heads, List.of("x", "y", "a", "b"), 
        List.of(List.of(List.of(0, 0, -1, 0, 0)), List.of(List.of(0, 0, 0, -1, 0))));
    assertFalse(res[0]);
  }
  
  @Test
  public void nestedLoopsCountingUpWithSwitchedRank() throws Exception {
    String className = snippetpackage + ".Lexicographic";
    List<Integer> heads = lineToLoopHeaderOffsets(className, "nestedLoopsCountingUp", 16, 18);
    Boolean[] res = RankChecker.checkLexiRelu(className, "nestedLoopsCountingUp", heads, List.of("x", "y", "a", "b"), 
        List.of(List.of(List.of(0, 1, 0, -1, 0)), List.of(List.of(1, 0, -1, 0, 0))));
    assertFalse(res[0]);
  }
  
  @Test
  public void nestedLoopsCountingTriangularWithGoodRank() throws Exception {
    String className = snippetpackage + ".Lexicographic";
    List<Integer> heads = lineToLoopHeaderOffsets(className, "nestedLoopsCountingTriangular", 27, 29);
    Boolean[] res = RankChecker.checkLexiRelu(className, "nestedLoopsCountingTriangular", heads, List.of("x", "a", "b"), 
        List.of(List.of(List.of(1, -1, 0, 0)), List.of(List.of(0, 1, -1, 0))));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }
  
  @Test
  public void nestedLoopsCountingTriangularWithBadRank() throws Exception {
    String className = snippetpackage + ".Lexicographic";
    List<Integer> heads = lineToLoopHeaderOffsets(className, "nestedLoopsCountingTriangular", 27, 29);
    Boolean[] res = RankChecker.checkLexiRelu(className, "nestedLoopsCountingTriangular", heads, List.of("x", "a", "b"), 
        List.of(List.of(List.of(1, -1, 0, 0)), List.of(List.of(0, 0, -1, 0))));
    assertFalse("shouldn't decrease", res[0]);
  }
  
  // TODO requires transition invariant. 
//  @Test
//  public void nestedLoopsFakeChangeWithGoodRank() throws Exception {
//    String className = snippetpackage + ".Lexicographic";
//    List<Integer> heads = lineToLoopHeaderOffsets(className, "nestedLoopsFakeChange");
//    Boolean[] res = RankChecker.checkLexiRelu(className, "nestedLoopsFakeChange", heads, List.of("x", "y"), 
//        List.of(List.of(List.of(1, 0, 0)), List.of(List.of(0, 1, 0))));
//    assertTrue("not decresing", res[0]);
//    assertTrue("not bounded below", res[1]);
//  }
  
  @Test
  public void CFM12Ex216WithoutInvariant1() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "CFM12Ex216", 122);
    Boolean[] res = RankChecker.checkRelu(className, "CFM12Ex216", offset, List.of("y"), List.of(List.of(1, 0)));
    assertFalse(res[0]);
  }
  
  @Test
  public void CFM12Ex216WithoutInvariant2() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "CFM12Ex216", 122);
    Boolean[] res = RankChecker.checkRelu(className, "CFM12Ex216", offset, List.of("x"), List.of(List.of(1, 0)));
    assertFalse(res[0]);
  }
  
  @Test
  public void CFM12Ex216WithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "CFM12Ex216", 122);
    Boolean[] res = RankChecker.checkRelu(className, "CFM12Ex216", offset, List.of("x","y"), List.of(List.of(-1, 2, 0), List.of(1, 0, 0)));
    assertTrue(res[0]);
  }
  
  @Test
  public void CS01Fig1WithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "CS01Fig1", 133);
    Boolean[] res = RankChecker.checkRelu(className, "CS01Fig1", offset, List.of("i","j","k"), List.of(List.of(-1, -1, 1, 101)));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }
  
  @Test
  public void pastaA7WithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "pastaA7", 142);
    Boolean[] res = RankChecker.checkRelu(className, "pastaA7", offset, List.of("x","y","z"), List.of(List.of(2, -1, -1, 0)));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }
  
  @Test
  public void countUpWithBiasWithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "countUpWithBias", 149);
    Boolean[] res = RankChecker.checkRelu(className, "countUpWithBias", offset, List.of("i","j","k"), List.of(List.of(-1, -1, +1, 100)));
    assertTrue("not decresing", res[0]);
    assertTrue("not bounded below", res[1]);
  }
  
  @Test
  public void pastaC1WithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    List<Integer> offsets = lineToLoopHeaderOffsets(className, "pastaC1", 156, 158);
    Boolean[] res = RankChecker.checkLexiRelu(className, "pastaC1", offsets, List.of("x","y"), List.of(List.of(List.of(1,0,1)), List.of(List.of(1,-1,1))));
    assertTrue("not decresing", res[0]);
  }
  
  @Test
  public void pastaC1WithBadRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    List<Integer> offsets = lineToLoopHeaderOffsets(className, "pastaC1", 156, 158);
    Boolean[] res = RankChecker.checkLexiRelu(className, "pastaC1", offsets, List.of("x","y"), List.of(List.of(List.of(1,0,0)), List.of(List.of(1,-1,1))));
    assertFalse(res[0]);
  }
  
  @Test
  public void pastaA1WithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    List<Integer> offsets = lineToLoopHeaderOffsets(className, "pastaA1", 166, 168);
    Boolean[] res = RankChecker.checkLexiRelu(className, "pastaA1", offsets, List.of("x","y"), List.of(List.of(List.of(1,0,0)), List.of(List.of(1,-1,0))));
    assertTrue(res[0]);
  }
  
  @Test
  public void pastaA1WithBadRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    List<Integer> offsets = lineToLoopHeaderOffsets(className, "pastaA1", 166, 168);
    Boolean[] res = RankChecker.checkLexiRelu(className, "pastaA1", offsets, List.of("x","y"), List.of(List.of(List.of(1,0,0)), List.of(List.of(1,-1,0),List.of(0,1,0))));
    assertFalse(res[0]);
  }
  
  @Test
  public void CFM12Ex218WithBadRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "CFM12Ex218", 176);
    Boolean[] res = RankChecker.checkRelu(className, "CFM12Ex218", offset, List.of("x","y"), List.of(1, -1, -1), List.of(List.of(2, 0, 0), List.of(0, 1, 0), List.of(0, -1, 0)));
    //Boolean[] res = RankChecker.checkRelu(className, "CFM12Ex218", offset, List.of("x","y"), List.of(1), List.of(List.of(1, 0, 0)));
    assertFalse(res[0]);
  }
  
//  @Test
//  public void CFM12Ex218WithGoodRank2() throws Exception {
//    String className = snippetpackage + ".Arithmetic";
//    int offset = lineToLoopHeaderOffset(className, "CFM12Ex218", 176);
//    Boolean[] res = RankChecker.checkRelu(className, "CFM12Ex218", offset, List.of("x","y"), List.of(-1, 1, 1, 1, 2), List.of(List.of(-2, 0, 0), List.of(-1, 0, 0), List.of(0, -1, 0), List.of(2, 1, 0), List.of(3, 1, 0)));
//    assertTrue(res[0]);
//  }
  
  @Test
  public void gcdWithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "gcd", 89);
    Boolean[] res = RankChecker.checkRelu(className, "gcd", offset, List.of("a", "b", "tmp"), List.of(2, 2, 2), List.of(List.of(-1,1,0,0), List.of(0,1,0,1), List.of(1,0,0,1)));
    assertTrue(res[0]);
  }
  
  @Test
  public void logIterativeWithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "logIterative", 184);
    Boolean[] res = RankChecker.checkRelu(className, "logIterative", offset, List.of("x", "y"), List.of(1), List.of(List.of(1, -1, 1)));
    assertTrue(res[0]);
  }
  
  @Test
  public void plusSwapWithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    int offset = lineToLoopHeaderOffset(className, "plusSwap", 196);
    Boolean[] res = RankChecker.checkRelu(className, "plusSwap", offset, List.of("x", "y"), List.of(1,1,1,1), List.of(List.of(1, 0, 0),List.of(-1, 0, 0),List.of(0, 1, 0),List.of(0, -1, 0)));
    assertTrue(res[0]);
  }
  
  @Test
  public void PR04Fig1WithGoodRank() throws Exception {
    String className = snippetpackage + ".Arithmetic";
    List<Integer> offsets = lineToLoopHeaderOffsets(className, "PR04Fig1", 207, 209);
    Boolean[] res = RankChecker.checkLexiRelu(className, "PR04Fig1", offsets, List.of("x","y"), List.of(List.of(List.of(1,0,0)), List.of(List.of(1,-1,0))));
    assertFalse(res[0]);
  }
  
  @Test
  public void logMultWithGoodReluRank() throws Exception {
    String className = snippetpackage + ".Invariants";
    int offset = lineToLoopHeaderOffset(className, "logMult", 17);
    Boolean[] res = RankChecker.checkRelu(className, "logMult", offset, List.of("x", "y", "res"), List.of(2,2,2,2,2), List.of(List.of(1, 0, -1, 0),
        List.of(2, -1, 0, 2),List.of(-1, 0, 0, -1),List.of(-2, 0, -1, -1),List.of(1, 0, 0, -1)));
    assertTrue(res[0]);
  }
}
