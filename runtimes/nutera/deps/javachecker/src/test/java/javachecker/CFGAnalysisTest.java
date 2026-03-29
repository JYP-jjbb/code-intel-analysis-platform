package javachecker;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CFGAnalysisTest {

  static String snippetpackage = "javachecker.snippet";
  
  @Test
  public void oneLoopTwoBranches() throws Exception {
    String className = snippetpackage + ".ControlFlow";
    List<Integer> res = CFGAnalyzer.loopHeaders(className, "oneLoopTwoBranches");
    Map<Integer, Integer> offsetToLine = CFGAnalyzer.labelOffsetToLine(className, "oneLoopTwoBranches");
    assertFalse(res.contains(null));
    assertTrue(res.size() == 1);
    assertEquals(offsetToLine.get(res.get(0)), Integer.valueOf(7));
  }
  
  @Test
  public void oneLoopCountUpOrDown() throws Exception {
    String className = snippetpackage + ".ControlFlow";
    List<Integer> res = CFGAnalyzer.loopHeaders(className, "oneLoopCountUpOrDown");
    Map<Integer, Integer> offsetToLine = CFGAnalyzer.labelOffsetToLine(className, "oneLoopCountUpOrDown");
    assertFalse(res.contains(null));
    assertTrue(res.size() == 1);
    assertEquals(offsetToLine.get(res.get(0)), Integer.valueOf(23));
  }
  
  @Test
  public void twoIntricatedLoops() throws Exception {
    String className = snippetpackage + ".ControlFlow";
    List<Integer> res = CFGAnalyzer.loopHeaders(className, "twoIntricatedLoops");
    Map<Integer, Integer> offsetToLine = CFGAnalyzer.labelOffsetToLine(className, "twoIntricatedLoops");
    assertFalse(res.contains(null));
    assertTrue(res.size() == 2);
    assertTrue(res.stream().map(o -> offsetToLine.get(o)).anyMatch(l -> l == 34));
    assertTrue(res.stream().map(o -> offsetToLine.get(o)).anyMatch(l -> l == 36));
  }
  
  @Test
  public void twoClutteredLoops() throws Exception {
    String className = snippetpackage + ".ControlFlow";
    List<Integer> res = CFGAnalyzer.loopHeaders(className, "twoClutteredLoops");
    Map<Integer, Integer> offsetToLine = CFGAnalyzer.labelOffsetToLine(className, "twoClutteredLoops");
    assertFalse(res.contains(null));
    assertEquals(2, res.size());
    assertTrue(res.stream().distinct().count() == 2);
    assertTrue(res.stream().map(o -> offsetToLine.get(o)).allMatch(l -> l == 48));
  }
}
