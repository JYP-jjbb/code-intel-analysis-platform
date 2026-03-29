package javachecker;
import static org.junit.Assert.assertNotNull;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import org.junit.Test;


public class ClassloadingTest {

  @Test
  public void checkFromJar() throws Exception {
    URL[] urls = { new URL("jar:file:build/libs/example.jar!/") };
    URLClassLoader cl = URLClassLoader.newInstance(urls);

    Map<Integer, SortedSet<Integer>> lineToOffset = 
        CFGAnalyzer.lineToLabelOffset(cl, "javachecker.Example", "main");
    assertNotNull(lineToOffset.get(7));
    
    int offset = lineToOffset.get(7).last();
 
    Boolean[] res = RankChecker.check(cl, "javachecker.Example", "main", offset, 
        List.of("args", "i"), List.of(0, 1, 0));
    assertNotNull(res);
  }

}
