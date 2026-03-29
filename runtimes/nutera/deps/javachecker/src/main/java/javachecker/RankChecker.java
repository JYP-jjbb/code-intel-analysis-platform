package javachecker;


import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import com.microsoft.z3.Params;



public class RankChecker {

  // =========================================================================
  // Encoding
  // =========================================================================

// 把 Value[]（loop.input 这种）转成 IntExpr
private static IntExpr asIntExpr(Value v) {
  if (v instanceof MyPrimitive) return ((MyPrimitive) v).value;
  if (v instanceof Expr) {
    com.microsoft.z3.Expr e = ((Expr) v).expr;
    if (e instanceof IntExpr) return (IntExpr) e;
  }
  return null;
}

// 读取 assume：优先 System property，其次 env；并把变量绑定到 state(Value[]) 里实际用的 IntExpr
private static BoolExpr parseAssumeAtHead(Context ctx, java.util.List<String> args, Value[] state) {
  String raw = System.getProperty("RANKCHECKER_ASSUME");
  if (raw == null || raw.trim().isEmpty()) raw = System.getenv("RANKCHECKER_ASSUME");
  if (raw == null) return ctx.mkTrue();
  raw = raw.trim();
  if (raw.isEmpty()) return ctx.mkTrue();

  java.util.HashMap<String, IntExpr> vmap = new java.util.HashMap<>();
  int n = Math.min(args.size(), state.length);
  for (int i = 0; i < n; i++) {
    IntExpr ie = asIntExpr(state[i]);
    if (ie != null) vmap.put(args.get(i), ie);
  }

  for (String aName : args) {
    if (!vmap.containsKey(aName)) {
      vmap.put(aName, ctx.mkIntConst(aName + "!0"));
    }
  }

  String[] parts = raw.split("\\s*(?:&&|,)\\s*");
  java.util.regex.Pattern pat =
      java.util.regex.Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*(==|=|>=|<=|>|<)\\s*([A-Za-z_][A-Za-z0-9_]*|-?\\d+)\\s*$");

  java.util.ArrayList<BoolExpr> conjs = new java.util.ArrayList<>();
  for (String p : parts) {
    if (p == null) continue;
    p = p.trim();
    if (p.isEmpty()) continue;

    java.util.regex.Matcher m = pat.matcher(p);
    if (!m.matches()) continue;

    String lhsName = m.group(1);
    String op = m.group(2);
    String rhsTok = m.group(3);

    IntExpr lhs = vmap.get(lhsName);
    if (lhs == null) continue;

    ArithExpr rhs;
    IntExpr rhsVar = vmap.get(rhsTok);
    if (rhsVar != null) {
      rhs = rhsVar;
    } else {
      try {
        rhs = ctx.mkInt(Integer.parseInt(rhsTok));
      } catch (NumberFormatException e) {
        continue;
      }
    }

    BoolExpr be;
    switch (op) {
      case "=":
      case "==": be = ctx.mkEq(lhs, rhs); break;
      case ">=": be = ctx.mkGe(lhs, rhs); break;
      case "<=": be = ctx.mkLe(lhs, rhs); break;
      case ">":  be = ctx.mkGt(lhs, rhs); break;
      case "<":  be = ctx.mkLt(lhs, rhs); break;
      default: continue;
    }
    conjs.add(be);
  }

  if (conjs.isEmpty()) return ctx.mkTrue();
  return ctx.mkAnd(conjs.toArray(new BoolExpr[0]));
}



// 把 assume 直接绑定到 var!0 这组符号上（用于保证最终打印的 cex 也满足 assume）
private static BoolExpr parseAssume0(Context ctx, java.util.List<String> args) {
  String raw = System.getProperty("RANKCHECKER_ASSUME");
  if (raw == null || raw.trim().isEmpty()) raw = System.getenv("RANKCHECKER_ASSUME");
  if (raw == null) return ctx.mkTrue();
  raw = raw.trim();
  if (raw.isEmpty()) return ctx.mkTrue();

  java.util.HashSet<String> argSet = new java.util.HashSet<>(args);

  String[] parts = raw.split("\\s*(?:&&|,)\\s*");
  java.util.regex.Pattern pat =
      java.util.regex.Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*(==|=|>=|<=|>|<)\\s*([A-Za-z_][A-Za-z0-9_]*|-?\\d+)\\s*$");

  java.util.ArrayList<BoolExpr> conjs = new java.util.ArrayList<>();
  for (String p : parts) {
    if (p == null) continue;
    p = p.trim();
    if (p.isEmpty()) continue;

    java.util.regex.Matcher m = pat.matcher(p);
    if (!m.matches()) continue;

    String lhsName = m.group(1);
    String op = m.group(2);
    String rhsTok = m.group(3);

    if (!argSet.contains(lhsName)) continue;

    IntExpr lhs = ctx.mkIntConst(lhsName + "!0");

    ArithExpr rhs;
    if (argSet.contains(rhsTok)) {
      rhs = ctx.mkIntConst(rhsTok + "!0");
    } else {
      try {
        rhs = ctx.mkInt(Integer.parseInt(rhsTok));
      } catch (NumberFormatException e) {
        continue;
      }
    }

    BoolExpr be;
    switch (op) {
      case "=":
      case "==": be = ctx.mkEq(lhs, rhs); break;
      case ">=": be = ctx.mkGe(lhs, rhs); break;
      case "<=": be = ctx.mkLe(lhs, rhs); break;
      case ">":  be = ctx.mkGt(lhs, rhs); break;
      case "<":  be = ctx.mkLt(lhs, rhs); break;
      default: continue;
    }
    conjs.add(be);
  }

  if (conjs.isEmpty()) return ctx.mkTrue();
  return ctx.mkAnd(conjs.toArray(new BoolExpr[0]));
}



// ============================================================
// Assume parsing (STRICT: bind to var!0)
// Source: System property "RANKCHECKER_ASSUME" or env "RANKCHECKER_ASSUME"
// Supports atoms joined by "&&":
//   v >= k, v > k, v <= k, v < k
//   v == w, v != w, v == k, v != k
// where v/w are variable names (method args), k is integer literal.
// ============================================================
private static String readAssumeRaw() {
  String raw = System.getProperty("RANKCHECKER_ASSUME");
  if (raw == null || raw.trim().isEmpty()) {
    raw = System.getenv("RANKCHECKER_ASSUME");
  }
  return (raw == null) ? "" : raw.trim();
}

private static IntExpr var0(Context ctx, String name) {
  // IMPORTANT: bind to SSA name var!0 (matches your logs: x!0,y!0,...)
  return ctx.mkIntConst(name + "!0");
}

private static BoolExpr parseAssume0Strict(Context ctx, List<String> vars) {
  String raw = readAssumeRaw();
  if (raw.isEmpty()) return ctx.mkTrue();

  java.util.HashSet<String> varset = new java.util.HashSet<>(vars);

  // split by &&
  String[] parts = raw.split("&&");
  java.util.ArrayList<BoolExpr> cs = new java.util.ArrayList<>();

  // regex for (lhs op rhs), rhs can be int or var
  java.util.regex.Pattern p = java.util.regex.Pattern.compile(
      "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(==|!=|>=|<=|>|<)\\s*([A-Za-z_][A-Za-z0-9_]*|[-]?[0-9]+)\\s*$"
  );

  for (String part : parts) {
    String t = part.trim();
    if (t.isEmpty()) continue;

    java.util.regex.Matcher m = p.matcher(t);
    if (!m.matches()) {
      // 如果某个子句解析不了：直接忽略（不让它把整个 assume 搞崩）
      // 你想更严格也可以改成 cs.add(ctx.mkFalse());
      continue;
    }

    String lhs = m.group(1);
    String op  = m.group(2);
    String rhs = m.group(3);

    if (!varset.contains(lhs)) {
      // assume 里出现了不在 vars 里的名字，忽略
      continue;
    }

    ArithExpr L = var0(ctx, lhs);
    ArithExpr R;

    if (rhs.matches("[-]?[0-9]+")) {
      R = ctx.mkInt(Integer.parseInt(rhs));
    } else {
      if (!varset.contains(rhs)) continue;
      R = var0(ctx, rhs);
    }

    BoolExpr atom;
    switch (op) {
      case "==": atom = ctx.mkEq(L, R); break;
      case "!=": atom = ctx.mkNot(ctx.mkEq(L, R)); break;
      case ">=": atom = ctx.mkGe(L, R); break;
      case "<=": atom = ctx.mkLe(L, R); break;
      case ">":  atom = ctx.mkGt(L, R); break;
      case "<":  atom = ctx.mkLt(L, R); break;
      default:   atom = ctx.mkTrue(); break;
    }
    cs.add(atom);
  }

  if (cs.isEmpty()) return ctx.mkTrue();
  return ctx.mkAnd(cs.toArray(new BoolExpr[0]));
}





  static abstract class Value implements org.objectweb.asm.tree.analysis.Value{
    
    int creationIndex;
    
    public Value(int creationIndex) {
      super();
      this.creationIndex = creationIndex;
    }

    @Override
    public int getSize() {
      return 1;
    }
  }
  
  static class None extends Value {
    public None(int creationIndex) {
      super(creationIndex);
    }

    @Override
    public String toString() {
      return "none";
    }
  }
  
  static class MyString extends Value {
    String val = null;
    
    public MyString(int creationIndex, String val) {
      super(creationIndex);
      this.val = val;
    }

    @Override
    public String toString() {
      return "\"" + val.toString() + "\"";
    }
  }
  
  static class MyStringBuilder extends Value {
    String val = "";
    
    public MyStringBuilder(int creationIndex) {
      super(creationIndex);
    }

    public MyStringBuilder(int creationIndex, String val) {
      super(creationIndex);
      this.val = val;
    }
    
    @Override
    public String toString() {
      return "sb:\"" + val.toString() + "\"";
    }
  }
  
  static class MyClass extends Value {
    Type val = null;
    
    public MyClass(int creationIndex, Type val) {
      super(creationIndex);
      this.val = val;
    }
    
    @Override
    public String toString() {
      return "<" + val.toString() + ">";
    }
  }
  
  static class MyThrowable extends Value {
    Type type = null;
    MyString message = null;
    MyThrowable cause = null;
    
    public MyThrowable(int creationIndex, Type type) {
      super(creationIndex);
      this.type = type;
    }

    public MyThrowable(int creationIndex, Type type, MyString message, MyThrowable cause) {
      super(creationIndex);
      this.type = type;
      this.message = message;
      this.cause = cause;
    }
    
    @Override
    public String toString() {
      if (message != null)
        return "<" + type.getInternalName() + ":" + message.val + ">";
      else
        return "<" + type.getInternalName() + ">";
    }
  }
  
  static class MyPrimitive extends Value {
    IntExpr value;
    
    public MyPrimitive(int creationIndex) {
      super(creationIndex);
    }
 
    public MyPrimitive(int creationIndex, IntExpr value) {
      super(creationIndex);
      this.value = value;
    }
    
    @Override
    public String toString() {
      return value == null ? "uninit" : value.toString();
    }
  }
  
  static class Phi extends Expr {
    boolean fresh;
    int oldIndex;
    
    private Phi(int creationIndex, IntExpr symbol, boolean fresh, int prevIndex) {
      super(creationIndex, symbol);
      this.fresh = fresh;
      this.oldIndex = prevIndex;
    }

    Phi (int creationIndex, IntExpr symbol, int prevIndex) {
      this(creationIndex, symbol, true, prevIndex);
    }
  }
  
  static class Expr extends Value {
    IntExpr expr;

    public Expr(int creationIndex, IntExpr expr) {
      super(creationIndex);
      this.expr = expr;
    }
    
    @Override
    public String toString() {
      return expr.toString();// + "@" + creationIndex;
    }
    
    @Override
    public boolean equals(java.lang.Object obj) {
      if (!(obj instanceof Expr))
        return false;
      
      Expr other = (Expr) obj;
      return other.expr.equals(expr);
    }
    
    @Override
    public int hashCode() {
      return expr.hashCode();
    }
  }
  
  static class Array extends Value {
    Value[] elements;
    
    public Array(int creationIndex, int length) {
      super(creationIndex);
      this.elements = new Value[length];
    }

    @Override
    public String toString() {
      return Arrays.toString(elements);
    }

    @Override
    public boolean equals(java.lang.Object obj) {
      if (!(obj instanceof Array))
        return false;
      
      Array other = (Array) obj;
      return Arrays.deepEquals(this.elements, other.elements);
    }

  }
  
  static class Object extends Value {
    Type type;
    Map<String, Value> fields = new TreeMap<>(); //ordering is important for inlining leafs

    static Integer ids = 0;
    Integer id;
    public Object(int creationIndex, Type type) {
      super(creationIndex);
      this.type = type;
      id = ids++;
    }

    @Override
    public String toString() {
//      String fullName = type.getInternalName().replace('.', '/');
//      return fullName.substring(fullName.lastIndexOf('/') + 1) + "@" + id;
      //return fullName.substring(fullName.lastIndexOf('/') + 1) + fields + "@" + id;
      StringBuilder sb = new StringBuilder();
      sb.append("(");
      fields.forEach((k,v) -> sb.append(v + ","));
      sb.replace(sb.length()-1, sb.length(), ")");
      sb.append("@" + id);
      return sb.toString();
    }
    
    @Override
    public boolean equals(java.lang.Object obj) {
      if (!(obj instanceof Object))
        return false;
      
      Object other = (Object) obj;
      return other.fields.equals(fields);
    }
  }
  
  static class Any extends Value {

    public Any(int creationIndex) {
      super(creationIndex);
    }

    @Override
    public String toString() {
      return "any";
    }

  }

  static class SSAFrame extends Frame<Value> {

    SSAFrame parent;
    Map<String, Map<String, Value>> global;
    
    Frame<Value>[] frames;
    Boolean jump;
    Boolean unreachable;

    Value result = null;
    
    public SSAFrame(SSAFrame parent, int numLocals, int numStack, Frame<Value>[] frames) {
      super(numLocals, numStack);
      assert (parent != null);
      this.parent = new SSAFrame(parent);
      this.global = parent.global;
      this.frames = frames;
      this.jump = null;
      this.unreachable = false;
      assert !parent.unreachable;
    }

    public SSAFrame(Map<String, Map<String, Value>> global, int numLocals, int numStack, Frame<Value>[] frames) {
      super(numLocals, numStack);
      this.parent = null;
      this.global = global;
      this.frames = frames;
      this.jump = null;
      this.unreachable = false;
    }
    
//    public SSAFrame(Map<String, Map<String, Value>> global, int numLocals, int numStack, Frame<Value>[] frames) {
//      super(numLocals, numStack);
//      this.global = global;
//      this.frames = frames;
//      this.jump = null;
//      this.unreachable = false;
//      assert parent == null || !parent.unreachable;
//    }
    
    public SSAFrame(SSAFrame frame) {
      super(frame);
//      if (frame.parent != null) 
//        this.parent = new SSAFrame(frame.parent);
//      this.frames = frame.frames;
//      this.unreachable = frame.unreachable;
    }
    
    @Override
    public Frame<Value> init(Frame<? extends Value> frame) {
      super.init(frame);
      SSAFrame ssaframe = (SSAFrame) frame;
      frames = ssaframe.frames;
      if (ssaframe.parent != null) {
        if (parent != null)
          parent.init(ssaframe.parent);
        else
          parent = new SSAFrame(ssaframe.parent);
      } 
      global = ssaframe.global;
      jump = null;
      unreachable = ssaframe.unreachable;
      return this;
    }
    
    @Override
    public void initJumpTarget(int opcode, LabelNode target) {
      //assert IntStream.range(0, getLocals()).allMatch(var -> getLocal(var).var >= 0);
      //assert IntStream.range(0, getLocals()).allMatch(var -> getLocal(var).mergeIndex >= 0);

//      if (target == null) {
//        for (int var = 0, end = getLocals(); var != end; var++)
//          setLocal(var, getLocal(var).mkContinue());
//      } else {
//        for (int var = 0, end = getLocals(); var != end; var++)
//          setLocal(var, getLocal(var).mkJump(target));
//      }
      
      if (jump != null) {
        unreachable = jump == (target == null);
      } else {
        unreachable = false;
      }
    }
    
    private int pos = -1;
    int pos() {
      if (pos >= 0)
        return pos;
      
      for (pos = 0; pos < frames.length; pos++) {
        if (frames[pos] == this)
          break;
      }
      
      if (pos == frames.length)
        pos = -1;
      
      return pos;
    }
    
    @Override
    public String toString() {
      return (unreachable ? "emp" : super.toString());
    }
    
    boolean reaches(Value src, Set<Object> good, Set<Object> bad) {
      if (src instanceof Object) {
        if (good.contains(src)) {
          return true;
        } else if (bad.contains(src)) {
          return false;
        } else {
          boolean reached = false;
          for (Value child : ((Object)src).fields.values()) {
            reached = reached || reaches(child, good, bad);
          }
          
          if (reached)
            good.add((Object) src);
          else
            bad.add((Object) src);
          
          return reached;
        }
      } else {
        return false;
      }
    }
    
    Object root(Object dst) {
      Object root = dst;
      
      Set<Object> bad = new HashSet<>();

      SSAFrame frame = this;
      while (frame != null) {
        for (int i = 0; i < frame.getLocals(); i++) {
          if (frame.getLocal(i) instanceof Object) {
            Set<Object> good = new HashSet<>(Set.of(root));
            Object local = (Object) frame.getLocal(i);
            if (reaches(local, good, bad))
              root = local;
          }
        }
        for (int i = 0; i < frame.getStackSize(); i++) {
          if (frame.getStack(i) instanceof Object) {
            Set<Object> good = new HashSet<>(Set.of(root));
            Object local = (Object) frame.getStack(i);
            if (reaches(local, good, bad))
              root = local;
          }
        }
        frame = frame.parent;
      }
      
      return root;
    } 
    
    static Set<Object> allChildredObject(Object obj) {
      Set<Object> result = new HashSet<>();
      for (Value val : obj.fields.values()) {
        if (val instanceof Object) {
          result.add((Object) val);
          result.addAll(allChildredObject((Object) val));
        }
      }
      return result;
    }
    
    Set<Object> allObjects() {
      Set<Object> result = new HashSet<>();
      for (int i = 0; i < getLocals(); i++) {
        if (getLocal(i) instanceof Object) {
          result.add((Object) getLocal(i));
          result.addAll(allChildredObject((Object) getLocal(i)));
        }
      }
      for (int i = 0; i < getStackSize(); i++) {
        if (getStack(i) instanceof Object) {
          result.add((Object) getStack(i));
          result.addAll(allChildredObject((Object) getStack(i)));
        }
      }
      if (parent != null)
        result.addAll(parent.allObjects());
      return result;
    }
    
    Object putField(int index, Object root, Object node, String field, Value value) {
      Object result = null;
      boolean changed = false;
      
      if (root == node) {
        result = new Object(index, node.type);
        for (Map.Entry<String, Value> entry : root.fields.entrySet()) {
          if (entry.getKey().equals(field)) 
            result.fields.put(field, value);
          else
            result.fields.put(entry.getKey(), entry.getValue());
        }
        assert result.fields.keySet().equals(root.fields.keySet());
        changed = true;
      } else {
        Map<String, Value> children = new HashMap<>();
        for (Map.Entry<String, Value> entry : root.fields.entrySet()) {
          if (entry.getValue() instanceof Object) {
            Object child = putField(index, (Object) entry.getValue(), node, field, value);
            if (child != entry.getValue())
              changed = true;
            children.put(entry.getKey(), child);
          } else {
            children.put(entry.getKey(), entry.getValue());
          }
        }
        assert children.keySet().equals(root.fields.keySet());
        
        if (changed) {
          result = new Object(index, root.type);
          result.fields = children;
        }
      }
      
      SSAFrame frame = this;
      while (changed && frame != null) {
        for (int i = 0; i < frame.getLocals(); i++) {
          if (root == frame.getLocal(i))
            frame.setLocal(i, result);
        }
        
        for (int i = 0; i < frame.getStackSize(); i++) {
          if (root == frame.getStack(i))
            frame.setStack(i, result);
        }
        
        frame = frame.parent;
      }
      
      assert changed == (result != null);
      
      return changed ? result : root;
    }
    
//    Value reindexAndPutField(int index, Value root, Value leaf, Value value) {
//      Value result = null;
//
//      if (root instanceof Object) { 
//        Object oroot = (Object) (root == leaf ? value : root);
//        Object oresult = new Object(index, oroot.type);
//        for (Map.Entry<String, Value> entry : oroot.fields.entrySet()) {
//          Value child = reindexAndPutField(index, entry.getValue(), leaf, value);
//          if (child instanceof Object) {
//            assert !(entry.getValue() instanceof Object) || ((Object)entry.getValue()).parent == root;
//            Object newChild = (Object) child;
//            newChild.parent = oresult;
//            newChild.name = entry.getKey();
//            oresult.fields.put(entry.getKey(), newChild);
//          } else {
//            oresult.fields.put(entry.getKey(), child);
//          }
//        }
//        result = oresult;
//      } else if (value instanceof Object){
//        if (root == leaf) {
//          Object obj = new Object(index, ((Object) value).type);
//          for (Map.Entry<String, Value> entry : ((Object) value).fields.entrySet()) {
//            Value val = reindexAndPutField(index, entry.getValue(), leaf, value);
//            if (val instanceof Object) {
//              Object oval = (Object) val;
//              oval.parent = obj;
//              oval.name = entry.getKey();
//            }
//            obj.fields.put(entry.getKey(), val);
//          }
//          result = obj;
//        } else {
//          result = root;
//        }
//      } else {
//        result = (root == leaf ? value : root).reindex(index);
//      }
//      
//      SSAFrame frame = this;
//      while (frame != null) {
//        for (int i = 0; i < frame.getLocals(); i++) {
//          if ((root == leaf ? value : root) == frame.getLocal(i))
//            frame.setLocal(i, result);
//        }
//        
//        for (int i = 0; i < frame.getStackSize(); i++) {
//          if ((root == leaf ? value : root) == frame.getStack(i))
//            frame.setStack(i, result);
//        }
//        
//        frame = frame.parent;
//      }
//      
//      return result;
//    }

    void initClass(int index, String className, SSAInterpreter interpreter) throws ClassNotFoundException, AnalyzerException {
      Map<String, Map<String, Value>> global = new HashMap<>(getGlobal());
      assert !getGlobal().containsKey(className);

      ClassNode n = interpreter.reader.getClassNode(className);
      HashMap<String, Value> fields = new HashMap<>();
      for (FieldNode f : n.fields) {
        if ((f.access & Opcodes.ACC_STATIC) != 0) {
          if (f.value == null)
            fields.put(f.name, interpreter.mkDefault(index, f.desc));
          else if (f.value instanceof Long) 
            fields.put(f.name, interpreter.mkConst(((Long) f.value).intValue(), index));
          else if (f.value instanceof Integer) 
            fields.put(f.name, interpreter.mkConst(((Integer) f.value).intValue(), index));
          else if (f.value instanceof String) 
            fields.put(f.name, new MyString(index, (String) f.value));
          else assert false;
        }
      }
      global.put(className, fields);
      
      MethodNode method = n.methods.stream()
          .filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
      
      if (method != null) {
        SSAInterpreter initInterpreter = new SSAInterpreter(interpreter.reader, 
            interpreter.ctx, new Value[0], null, interpreter.endOffset, Collections.emptyList());
        SSAEncoder encoder = new SSAEncoder(initInterpreter, initInterpreter.reader, global);

        // LinkedList<String> trace = new LinkedList<>(this.trace);
        // trace.addFirst(owner + "." + name + " " + desc + "\n");

        encoder.analyze(className, method);

        interpreter.encoders.put(interpreter.endOffset, encoder);
        interpreter.endOffset = initInterpreter.endOffset;
        
        List<SSAFrame> outputs = encoder.getReturned();
        assert outputs.size() == 1;
        setGlobal(outputs.iterator().next().global);
      } else {
        setGlobal(global);
      }

    }
    
    Map<String, Map<String, Value>> getGlobal() {
      return global;
    }
    
    void setGlobal(Map<String, Map<String, Value>> global) {
      SSAFrame f = this;
      while(f != null) {
        f.global = global;
        f = f.parent;
      }
    }
    
    @Override
    public void execute(AbstractInsnNode insn, Interpreter<Value> interpreter)
        throws AnalyzerException {
      if (unreachable) 
        return;
      
      assert allObjects().stream().filter(o -> o.type.getInternalName().endsWith("ListItr")).count() <= 1;
      
      SSAInterpreter ssaInterpreter = (SSAInterpreter) interpreter;
      int insnIndex = ssaInterpreter.instructions.indexOf(insn);

      String className = null;
      switch (insn.getOpcode()) {
        case Opcodes.NEW:
          className = ((TypeInsnNode)insn).desc;
          break;
        case Opcodes.GETSTATIC:
        case Opcodes.PUTSTATIC:
          className = ((FieldInsnNode)insn).owner;
          break;
        case Opcodes.INVOKESTATIC:
          className = ((MethodInsnNode)insn).owner;
          break;
      }
      if (className != null && !className.equals("java/lang/Throwable") &&
          !getGlobal().containsKey(className) && !className.equals("java/lang/Integer")) {
        try {
          initClass(insnIndex, className, ssaInterpreter);
        } catch (ClassNotFoundException e) {
          throw new AnalyzerException(insn, e.getMessage(), e);
        }
      }

      switch (insn.getOpcode()) {
        case Opcodes.PUTFIELD:
          ssaInterpreter.setFrame(this);
          super.execute(insn, interpreter);
          
           
          if (ssaInterpreter.putFieldNode == null)
            break;
          Object root = root(ssaInterpreter.putFieldNode);
          
//          reindexAndPutField(insnIndex, root, 
//              ssaInterpreter.putFieldNode.fields.get(ssaInterpreter.putFieldName), 
//              ssaInterpreter.putFieldValue);
          
          putField(insnIndex + ssaInterpreter.beginOffset, root, ssaInterpreter.putFieldNode, ssaInterpreter.putFieldName, 
              ssaInterpreter.putFieldValue);
          
          ssaInterpreter.putFieldNode = null;
          ssaInterpreter.putFieldIndex = -1;
          ssaInterpreter.putFieldName = null;
          
          break;
        case Opcodes.INVOKEVIRTUAL:
        case Opcodes.INVOKESPECIAL:
        case Opcodes.INVOKESTATIC:
        case Opcodes.INVOKEINTERFACE: {
          ssaInterpreter.setFrame(this);
          super.execute(insn, interpreter);

          Value result = null;
          
          if (Type.getReturnType(((MethodInsnNode)insn).desc) != Type.VOID_TYPE) 
            result = pop();

          assert result == null;
          
          assert ssaInterpreter.invokeOutput != null;
          init(ssaInterpreter.invokeOutput.get(0).parent);
          if (ssaInterpreter.invokeOutput.get(0).result != null)
            push(ssaInterpreter.invokeOutput.get(0).result);
          else
            assert Type.getReturnType(((MethodInsnNode)insn).desc) == Type.VOID_TYPE;
          
          Iterator<SSAFrame> it = ssaInterpreter.invokeOutput.listIterator(1);
          while (it.hasNext()) {
            SSAFrame next = it.next();
            Value res = next.result;
            next = next.parent;
            if (res != null)
              next.push(res);
            merge(next, ssaInterpreter);
          }
          ssaInterpreter.invokeOutput = null;
          
          if (Type.getReturnType(((MethodInsnNode)insn).desc) != Type.VOID_TYPE) {
            result = pop();
            assert result != null;
          }
          
          if (ssaInterpreter.callEncoder[insnIndex] != null) {
            ssaInterpreter.callInput[insnIndex] = ssaInterpreter.callEncoder[insnIndex].getInput();
            ssaInterpreter.callOutput[insnIndex] = SSAEncoder.frameToVector(this);
            ssaInterpreter.callResult[insnIndex] = result;
          }

          if (result != null)
            push(result);
          
          break;
        }
        case Opcodes.PUTSTATIC: {
          Map<String, Map<String, Value>> global = new HashMap<>(getGlobal());
          FieldInsnNode fn = (FieldInsnNode) insn;
          assert global.containsKey(fn.owner);
          Map<String, Value> fields = new HashMap<>(global.get(fn.owner));
          assert fields.containsKey(fn.name);
          fields.put(fn.name, pop());
          global.put(className, fields);
          setGlobal(global);
        }
        break;
        case Opcodes.RETURN: {
          SSAFrame out = new SSAFrame(this);
          out.result = null;
          ssaInterpreter.returned[insnIndex] = List.of(out);
          break;
        }
        default:
          ssaInterpreter.setFrame(this);
          super.execute(insn, interpreter);
      }
      
      ssaInterpreter.output[insnIndex] = new SSAFrame(this);
      
      jump = ssaInterpreter.jump;
      ssaInterpreter.jump = null;
    }

    Expr merge(Expr oldValue, Expr newValue, SSAInterpreter interpreter, String prefix) {
//      if (target)
        return interpreter.merge(oldValue, newValue, pos(), prefix);
//      else
//        return interpreter.merge(oldValue, newValue);
    }
    
    Value merge(Value oldValue, Value newValue, SSAInterpreter interpreter, String prefix) {
      assert (oldValue instanceof Expr) == (newValue instanceof Expr) ||
          (oldValue instanceof None && newValue instanceof Expr);
      assert (oldValue instanceof Object) == (newValue instanceof Object);
      assert (oldValue instanceof Array) == (newValue instanceof Array);
   
      if (oldValue instanceof Any) {
        return oldValue;
      } else if (newValue instanceof Any) {
        //assert false;
        return new Any(pos());
      } else if (oldValue instanceof None && newValue instanceof None) {
        return oldValue;
      } else if (oldValue instanceof None && newValue instanceof Expr) {
        return interpreter.merge((None)oldValue, (Expr)newValue, pos(), prefix);
      } else if (oldValue instanceof Expr) {
        return merge((Expr)oldValue, (Expr)newValue, interpreter, prefix);//may have name clash
      } else if (oldValue instanceof Array) {
        Array oldArray = (Array) oldValue, newArray = (Array) newValue;
        assert oldArray.elements.length == newArray.elements.length;
        if (oldArray.equals(newArray)) {
          return oldArray;
        } else {
          Array result = new Array(-1, oldArray.elements.length);
          for (int i = 0; i < result.elements.length; i++) {
            result.elements[i] = merge(oldArray.elements[i], newArray.elements[i], interpreter, prefix);
          }
          return result;
        }
      } else {
        Set<String> keys = ((Object)oldValue).fields.keySet();
        HashMap<String, Value> fields = new HashMap<>();
        boolean changed = false;
        for (String key : keys) {
          Value oldChild = ((Object)oldValue).fields.get(key);
          Value newChild = ((Object)newValue).fields.get(key);
          Value v = merge(oldChild, newChild, interpreter, key);
          fields.put(key, v);
          if (v != oldChild) 
            changed  = true;
        }
        
        if (changed) {
          Object result = new Object(pos(), ((Object)oldValue).type);
          result.fields.putAll(fields);
          
          SSAFrame frame = this;
          while (frame != null) {
            for (int i = 0; i < frame.getLocals(); i++) {
              if (oldValue == frame.getLocal(i))
                frame.setLocal(i, result);
            }
            
            for (int i = 0; i < frame.getStackSize(); i++) {
              if (oldValue == frame.getStack(i))
                frame.setStack(i, result);
            }
            
            frame = frame.parent;
          }
          return result;
        } else {
          return oldValue;
        }
      }
    }
    
    @Override
    public boolean merge(Frame<? extends Value> frame, Interpreter<Value> interpreter)
        throws AnalyzerException {
      return merge((SSAFrame)frame, (SSAInterpreter)interpreter);
    }
    
    public boolean merge(SSAFrame frame, SSAInterpreter interpreter)
        throws AnalyzerException {
      if (unreachable && frame.unreachable) { 
        return false;
      } else if (unreachable && !frame.unreachable) {
        for (int i = 0; i < frame.getLocals(); i++) {
          setLocal(i, frame.getLocal(i));
        }
        for (int i = 0; i < frame.getStackSize(); i++) {
          if (i < getStackSize())
            setStack(i, frame.getStack(i));
          else 
            push(frame.getStack(i));
        }
        unreachable = false;
        jump = null;
        if (parent != null)
          parent.init(frame.parent);
        setGlobal(frame.global);
        return true;
      } else if (!unreachable && frame.unreachable) {
        return false;
      } 
      
      Map<Object, Object> roots = new HashMap<>();

      boolean changed = false;
      assert getLocals() == frame.getLocals();
      for (int i = 0; i < getLocals(); i++) {
        assert (getLocal(i) instanceof Expr) == (frame.getLocal(i) instanceof Expr) ||
            getLocal(i) instanceof None;
        assert (getLocal(i) instanceof Object) == (frame.getLocal(i) instanceof Object);
        if (getLocal(i) instanceof Object) {
          Object oldRoot = root((Object) getLocal(i)), newRoot = frame.root((Object) frame.getLocal(i));
          assert oldRoot.type.equals(newRoot.type);
          assert !roots.containsKey(oldRoot) || newRoot == roots.get(oldRoot);
          roots.put(oldRoot, newRoot);
        } else {
          Value v = merge(getLocal(i), frame.getLocal(i), interpreter, interpreter.varNames[i]);
          if (!v.equals(getLocal(i))) {
            setLocal(i, v);
            changed = true;
          }
        }
      }
      
      assert getStackSize() == frame.getStackSize();
      for (int i = 0; i < getStackSize(); i++) {
        assert (getStack(i) instanceof Expr) == (frame.getStack(i) instanceof Expr);
        assert (getStack(i) instanceof Object) == (frame.getStack(i) instanceof Object);
        if (getStack(i) instanceof Object) {
          Object oldRoot = root((Object) getStack(i)), newRoot = frame.root((Object) frame.getStack(i));
          assert oldRoot.type.equals(newRoot.type);
          assert !roots.containsKey(oldRoot) || newRoot == roots.get(oldRoot);
          roots.put(oldRoot, newRoot);
        } else {
          Value v = merge(getStack(i), frame.getStack(i), interpreter, null);
          if (!v.equals(getStack(i))) {
            setStack(i, v);
            changed = true;
          }
        }
      }
      
      for (Map.Entry<Object, Object> entry : roots.entrySet()) {
        Value res = merge(entry.getKey(), entry.getValue(), interpreter, null);
        if (res != entry.getKey())
          changed = true;
      }
      
      return changed;
    }
    
  };

  static class SSAInterpreter extends Interpreter<Value> {

    final ClassReader reader;
    final Context ctx;
    final Value[] args;
    final SSAFrame input;
    
    SSAFrame current = null;
    
    String[] varNames;
    String[] varDescriptors;
    InsnList instructions = null;

    Expr[] assignmentSymbol;
    IntExpr[] assignmentExpr;

    BoolExpr[] jumpSymbol;
    BoolExpr[] jumpCondition;

    Value[][] callInput, callOutput;
    SSAEncoder[] callEncoder;
    Value[] callResult;
    List<SSAFrame>[] callThrows;
    
    Map<String, Set<String>>[] merges; // debug only
    Map<Integer, SSAEncoder> encoders = new TreeMap<>();
    
    int beginOffset;
    int endOffset;
    
    List<String> trace;
    
    Boolean jump = null;

    List<SSAFrame>[] thrown;
    List<SSAFrame>[] returned;
    
    SSAFrame[] output;
    
    public SSAInterpreter(ClassReader reader, Context ctx, Value[] args, SSAFrame input, 
        int offset, List<String> trace) {
      super(Opcodes.ASM8);
      this.reader = reader;
      this.ctx = ctx;
      this.args = args;
      this.beginOffset = this.endOffset = offset;
      this.input = input;
      this.trace = trace;
    }

    void setFrame(SSAFrame frame) {
      current = frame;
    }

    void init(MethodNode method) {
      varNames = new String[method.maxLocals];
      varDescriptors = new String[method.maxLocals];
      for (LocalVariableNode v : method.localVariables) {
        if (varNames[v.index] == null) {
          varNames[v.index] = v.name;
          varDescriptors[v.index] = v.desc;
        } 
      }
      //method.localVariables.stream().map(n -> n.name).toArray(String[]::new);
      instructions = method.instructions;

      assert StreamSupport.stream(instructions.spliterator(), false).allMatch(insn -> instructions.indexOf(insn) >= 0);

      assignmentSymbol = new Expr[instructions.size()];
      assignmentExpr = new IntExpr[instructions.size()];
      jumpSymbol = new BoolExpr[instructions.size()];
      jumpCondition = new BoolExpr[instructions.size()];
      
      callEncoder = new SSAEncoder[instructions.size()];
      callResult = new Value[instructions.size()];
      callInput = new Value[instructions.size()][];
      callOutput = new Value[instructions.size()][];
      callThrows = new List[instructions.size()];
          
      returned = new List[instructions.size()];
      thrown = new List[instructions.size()];
      output = new SSAFrame[instructions.size()];
      
      endOffset += instructions.size();
      
      merges = new Map[instructions.size()];
    }
    
    void initVarNames(String[] varNames) {
      assert this.varNames == null;
      this.varNames = varNames;
    }
    
    BoolExpr mkJumpSymbol(int insnIndex) {
      return ctx.mkBoolConst("J" + (insnIndex + beginOffset));
    }

    Value mkConst(int val, int insnIndex) {
      return new Expr(insnIndex + beginOffset, ctx.mkInt(val));
    }
    
    Value mkConst(boolean val, int insnIndex) {
      return new Expr(insnIndex + beginOffset, ctx.mkInt(val ? 1 : 0));
    }
    
    void mkBranch(BoolExpr expr, int insnIndex) {
      expr = (BoolExpr) expr.simplify();

      // create symbol
      if (jumpSymbol[insnIndex] == null)
        jumpSymbol[insnIndex] = mkJumpSymbol(insnIndex);

      // update condition
      jumpCondition[insnIndex] = expr;
      
      if (expr.isConst())
        jump = Boolean.valueOf(expr.getBoolValue().toInt() == 1);
      else 
        jump = null;
    }

    void mkAnyBranch(int insnIndex) {
      // create symbol
      if (jumpSymbol[insnIndex] == null)
        jumpSymbol[insnIndex] = mkJumpSymbol(insnIndex);

      // update condition
      jumpCondition[insnIndex] = null;
    }
    
    Expr mkAssignment(String prefix, IntExpr expr, int insnIndex) {
      // create symbol
      if (assignmentSymbol[insnIndex] == null) {
        assignmentSymbol[insnIndex] = (Expr) mkFreshModel(ctx, "I", prefix, "!"+Integer.toString(insnIndex + beginOffset), insnIndex + beginOffset);
      }
      // update assignment
      assignmentExpr[insnIndex] = expr = (IntExpr) expr.simplify();

      if (expr.isIntNum()) {
        return new Expr(insnIndex + beginOffset, expr);
      } else {
        return assignmentSymbol[insnIndex];
      }
    }
    
    Expr mkMerge(Value oldValue, Expr newValue, int insnIndex, String prefix) {
      assert oldValue instanceof Expr || oldValue instanceof None;
      
      // temporary; must find a safer naming system
      if (prefix == null)
        prefix = "expr";
      
      Phi merge = new Phi(insnIndex + beginOffset, ctx.mkIntConst(prefix + "!M" + (insnIndex + beginOffset)), oldValue.creationIndex);
      // store merges here
      if (merges[insnIndex] == null)
        merges[insnIndex] = new HashMap<>();
      assert !merges[insnIndex].containsKey(merge.toString());
      merges[insnIndex].put(merge.toString(), new HashSet<>(Arrays.asList(oldValue.toString(), newValue.toString())));

      return merge;
    }

    @Override
    public Value newEmptyValue(int local) {
      return new None(0);
    }

    @Override
    public Value newParameterValue(boolean isInstanceMethod, int local, Type type) {
      assert type.getSize() == 1;
      assert local < args.length;
      //return mkArg(args[local], local);
      return args[local];
    }

    @Override
    public Value newReturnTypeValue(Type type) {
      //assert false;
      return null;
    }

    @Override
    public Value newValue(Type type) {
      assert false;
      return null;
    }

    @Override
    public Value newExceptionValue(TryCatchBlockNode tryCatchBlockNode, Frame<Value> handlerFrame,
        Type exceptionType) {
      SSAFrame frame = (SSAFrame) handlerFrame;
      assert frame.result != null;
      return frame.result;
    }
    
    Value mkDefault(int creationIndex, String desc) {
      if (desc.length() == 1)
        return mkConst(0, creationIndex + beginOffset);
      else if (desc.startsWith("L"))
        return new None(creationIndex + beginOffset);
      else if (desc.startsWith("["))
        return new None(creationIndex + beginOffset);
      else
        assert false;
      return null;
    }
    
    public MyThrowable mkThrowable(int creationIndex, ClassNode classNode) throws ClassNotFoundException {
      return new MyThrowable(creationIndex, Type.getObjectType(classNode.name));
    }
    
    public Value mkNew(int creationIndex, String name) throws ClassNotFoundException {
      if (name.equals("java.lang.StringBuilder")) {
        return new MyStringBuilder(creationIndex + beginOffset);
      } else if (name.equals("java.lang.Integer")) {
        return new MyPrimitive(creationIndex + beginOffset);
      }
      
      assert !name.contains("/");
      
      ClassNode cn = reader.getClassNode(name);
      
      Class<?> clazz = reader.cl.loadClass(name);
      if (Throwable.class.isAssignableFrom(clazz)) {
        return mkThrowable(creationIndex, cn);
      }
      
      Object obj = new Object(creationIndex + beginOffset, Type.getObjectType(name));

      while (cn != null) {
        for (FieldNode fnode : cn.fields) {
          if ((fnode.access & Opcodes.ACC_STATIC) == 0)
            obj.fields.put(fnode.name, mkDefault(creationIndex, fnode.desc));
        }
        cn = cn.superName != null ? reader.getClassNode(cn.superName) : null;
      }
      return obj;
    }

    @Override
    public Value newOperation(AbstractInsnNode insn) throws AnalyzerException {
      final int insnIndex = instructions.indexOf(insn);
      switch (insn.getOpcode()) {
        case Opcodes.BIPUSH:
          return mkConst(((IntInsnNode) insn).operand, insnIndex);
        case Opcodes.SIPUSH:
          return mkConst(((IntInsnNode) insn).operand, insnIndex);
        case Opcodes.ICONST_M1:
            return mkConst(-1, insnIndex);
        case Opcodes.ICONST_0:
          return mkConst(0, insnIndex);
        case Opcodes.ICONST_1:
          return mkConst(1, insnIndex);
        case Opcodes.ICONST_2:
          return mkConst(2, insnIndex);
        case Opcodes.ICONST_3:
          return mkConst(3, insnIndex);
        case Opcodes.ICONST_4:
          return mkConst(4, insnIndex);
        case Opcodes.ICONST_5:
          return mkConst(5, insnIndex);
        case Opcodes.ACONST_NULL:
          return new None(insnIndex);
        case Opcodes.NEW:
          try {
            return mkNew(insnIndex, ((TypeInsnNode)insn).desc.replace("/", "."));
          } catch (ClassNotFoundException e) {
            throw new AnalyzerException(insn, e.getMessage(), e);
          }
        case Opcodes.LDC:
        {
          LdcInsnNode ldcn = (LdcInsnNode) insn;
          if (ldcn.cst instanceof String) 
            return new MyString(insnIndex, (String) ldcn.cst);
          else if (ldcn.cst instanceof Type)
            return new MyClass(insnIndex, (Type) ldcn.cst);
          else if (ldcn.cst instanceof Integer)
            return mkConst((Integer) ldcn.cst, insnIndex);
          else assert false;
        }
          
        case Opcodes.GETSTATIC: {
            FieldInsnNode f = (FieldInsnNode) insn;
            switch (f.owner) {
              case "java/lang/String":
                switch(f.name) {
                  case "COMPACT_STRINGS":
                    return new Expr(insnIndex, ctx.mkInt(1));
                  default:
                    assert false;
                } 
                break;
            default:
              assert current.global.containsKey(f.owner);
              assert current.global.get(f.owner).containsKey(f.name);
              return current.global.get(f.owner).get(f.name);
          }   
        }
        default:
          assert false;
      }
      return null;
    }

    @Override
    public Value copyOperation(AbstractInsnNode insn, Value value)
        throws AnalyzerException {
      final int insnIndex = instructions.indexOf(insn);
      switch (insn.getOpcode()) {
        case Opcodes.ISTORE:
          if (value instanceof MyString)
            return value;

          // If the stored int is unknown (e.g., from IALOAD -> Any), keep it as Any
          // instead of casting to Expr.
          if (value instanceof Any || value instanceof None)
            return value;

          return mkAssignment(varNames[((VarInsnNode) insn).var], ((Expr) value).expr, insnIndex);
        case Opcodes.ILOAD:
          return value;
        case Opcodes.ALOAD:
          return value;
        case Opcodes.DUP:
          return value;
        case Opcodes.ASTORE:
          return value;
        default:
          assert false;
          return null;
      }
    }
    
    @Override
    public Value unaryOperation(AbstractInsnNode insn, Value value) throws AnalyzerException {
      final int insnIndex = instructions.indexOf(insn);
      
      if (value instanceof Any && insn instanceof JumpInsnNode) {
        mkAnyBranch(insnIndex);
        return null;
      }
      
      switch (insn.getOpcode()) {
        case Opcodes.IINC: {
          IincInsnNode iincInsn = (IincInsnNode) insn;
          return mkAssignment(varNames[iincInsn.var], (IntExpr) ctx.mkAdd(((Expr)value).expr, ctx.mkInt(iincInsn.incr)),
              insnIndex);
        }
        case Opcodes.IFGT:
          mkBranch(ctx.mkGt(((Expr)value).expr, ctx.mkInt(0)), insnIndex);
          return null;
        case Opcodes.IFLT:
          mkBranch(ctx.mkLt(((Expr)value).expr, ctx.mkInt(0)), insnIndex);
          return null;
        case Opcodes.IFGE:
          mkBranch(ctx.mkGe(((Expr)value).expr, ctx.mkInt(0)), insnIndex);
          return null;
        case Opcodes.IFLE:
          mkBranch(ctx.mkLe(((Expr)value).expr, ctx.mkInt(0)), insnIndex);
          return null;
        case Opcodes.IFEQ:
          mkBranch(ctx.mkEq(((Expr)value).expr, ctx.mkInt(0)), insnIndex);
          return null;
        case Opcodes.IFNE:
          mkBranch(ctx.mkNot(ctx.mkEq(((Expr)value).expr, ctx.mkInt(0))), insnIndex);
          return null;
        case Opcodes.INEG:
          return new Expr(insnIndex, (IntExpr) ctx.mkUnaryMinus(((Expr)value).expr));
        case Opcodes.ARRAYLENGTH:
          if (value instanceof Array) {
            return mkConst(insnIndex, ((Array) value).elements.length);
          }
          return new Any(insnIndex);
        case Opcodes.GETFIELD:
          if (value instanceof Object) {
            assert ((Object)value).fields.containsKey(((FieldInsnNode)insn).name);
            return ((Object)value).fields.get(((FieldInsnNode)insn).name);
          } else if (value instanceof Any) {
            return new Any(insnIndex);
          } else assert false;
          return null;
        case Opcodes.NEWARRAY:
        case Opcodes.ANEWARRAY:
          {
            IntExpr length = ((Expr) value).expr;

            // If the array length is symbolic (e.g., m!23), do NOT parseInt.
            // Abstract the array as Any to avoid crashing (precision may drop).
            if (!length.isIntNum()) {
              return new Any(insnIndex);
            }

            Array array = new Array(insnIndex, Integer.valueOf(length.toString()));
            for (int i = 0; i < array.elements.length; i++)
              array.elements[i] = mkDefault(insnIndex, ((TypeInsnNode) insn).desc);
            return array;
          }
        case Opcodes.IFNONNULL:
          mkBranch(ctx.mkBool(!(value instanceof None)), insnIndex);
          return null;
        case Opcodes.ATHROW: {
          SSAFrame out = new SSAFrame(current);
          out.result = value;
          assert value instanceof MyThrowable;
          thrown[insnIndex] = List.of(out);
          return null;
        }
        case Opcodes.IRETURN:
        case Opcodes.ARETURN: {
          SSAFrame out = new SSAFrame(current);
          out.result = value;
          returned[insnIndex] = List.of(out);
          return null;
        }
        case Opcodes.CHECKCAST: {
          if (value instanceof Object) {
            String expectedName = Type.getObjectType(((TypeInsnNode)insn).desc).getInternalName().replace("/", ".");
            String actualName = ((Object)value).type.getInternalName();
            try {
              Class<?> expected = reader.cl.loadClass(expectedName);
              Class<?> actual = reader.cl.loadClass(expectedName);
              if (!expected.isAssignableFrom(actual)) {
                // TODO: throw
                assert false;
              } 
              return value;
            } catch (ClassNotFoundException e) {
              throw new AnalyzerException(insn, e.getMessage(), e);
            }
          } else if (value instanceof Any) {
            // TODO: conditional throw
            return value;
          } else if (value instanceof None) {
            return value;
          }
          assert false;
          return null;
        }
        default:
          assert false;
          return null;
      }
    }

    // side effect
    int putFieldIndex;
    Object putFieldNode;
    String putFieldName;
    Value putFieldValue;
    
    void mkPutField(int index, Object object, String field, Value value) {
      assert object.fields.containsKey(field);
      //assert object.fields.get(field) instanceof None || object.fields.get(field) instanceof Expr;
      
      putFieldIndex = index + beginOffset;
      putFieldNode = object;
      putFieldName = field;
      if (value instanceof Expr)
        putFieldValue = mkAssignment(field, ((Expr)value).expr, index);
      else if (value instanceof Object)
        putFieldValue = value;
      else if (value instanceof Array)
        putFieldValue = value;
      else if (value instanceof Any || value instanceof None)
        putFieldValue = value;
      else assert false;
    }
    
    @Override
    public Value binaryOperation(AbstractInsnNode insn, Value value1, Value value2) throws AnalyzerException {
      final int insnIndex = instructions.indexOf(insn);
      
      if (value1 instanceof Any || value2 instanceof Any) {
      // Branch on unknown: fork both ways.
      if (insn instanceof JumpInsnNode) {
        mkAnyBranch(insnIndex);
        return null;
      }
      // Non-branch binary ops with unknowns: over-approximate as Any
      return new Any(insnIndex);
    }
      
      switch (insn.getOpcode()) {
        case Opcodes.IF_ICMPLE:
          mkBranch(ctx.mkLe(((Expr)value1).expr, ((Expr)value2).expr), insnIndex);
          return null;
        case Opcodes.IF_ICMPGE:
          mkBranch(ctx.mkGe(((Expr)value1).expr, ((Expr)value2).expr), insnIndex);
          return null;
        case Opcodes.IF_ICMPGT:
          mkBranch(ctx.mkGt(((Expr)value1).expr, ((Expr)value2).expr), insnIndex);
          return null;
        case Opcodes.IF_ICMPLT:
          mkBranch(ctx.mkLt(((Expr)value1).expr, ((Expr)value2).expr), insnIndex);
          return null;
        case Opcodes.IF_ICMPEQ:
          mkBranch(ctx.mkEq(((Expr)value1).expr, ((Expr)value2).expr), insnIndex);
          return null;
        case Opcodes.IF_ICMPNE:
          mkBranch(ctx.mkNot(ctx.mkEq(((Expr)value1).expr, ((Expr)value2).expr)), insnIndex);
          return null;
        case Opcodes.ISUB:
          return new Expr(insnIndex, (IntExpr) ctx.mkSub(((Expr)value1).expr, ((Expr)value2).expr));
        case Opcodes.IADD:
          return new Expr(insnIndex, (IntExpr) ctx.mkAdd(((Expr)value1).expr, ((Expr)value2).expr));
        case Opcodes.IMUL:
          return new Expr(insnIndex, (IntExpr) ctx.mkMul(((Expr)value1).expr, ((Expr)value2).expr));
        case Opcodes.IDIV:
          return new Expr(insnIndex, (IntExpr) ctx.mkDiv(((Expr)value1).expr, ((Expr)value2).expr));
        case Opcodes.IREM:
          return new Expr(insnIndex, (IntExpr) ctx.mkRem(((Expr)value1).expr, ((Expr)value2).expr));
        case Opcodes.IALOAD:
          return new Any(insnIndex);
        case Opcodes.PUTFIELD:
          if (value1 instanceof Any && value2 instanceof Any || 
              value1 instanceof Any && value2 instanceof None)
            return null;
          assert value1 instanceof Object;
          //assert value2 instanceof Expr;
          mkPutField(insnIndex, (Object) value1, ((FieldInsnNode)insn).name, value2);
          return null;
        case Opcodes.ISHL:
          assert value2 instanceof Expr && ((Expr)value2).expr.isIntNum();
          {
              int k = Integer.valueOf(((Expr)value2).expr.toString());
              IntExpr res = ((Expr)value1).expr;
              while (k != 0) {
                res = (IntExpr) ctx.mkMul(res, ctx.mkInt(2));
                k--;
              }
              return new Expr(insnIndex, res);
          }
        case Opcodes.ISHR:
          assert value2 instanceof Expr && ((Expr)value2).expr.isIntNum();
          {
            int i = Integer.valueOf(((Expr)value2).expr.toString());
            IntExpr res = ((Expr)value1).expr;
            while (i != 0) {
              res = (IntExpr) ctx.mkDiv(res, ctx.mkInt(2));
              i--;
            }
            return new Expr(insnIndex, res);
          }
        default:
          assert false;
          return null;
      }
    }

    @Override
    public Value ternaryOperation(AbstractInsnNode insn, Value value1,
        Value value2, Value value3) throws AnalyzerException {
      assert false;
      return null;
    }

    CallNode openCall(List<String> trace, MethodInsnNode insn) {
      CallNode node = RankChecker.calls.get(insn);
      if (node == null) {
        LinkedList<String> newtrace = new LinkedList<String>(trace);
        newtrace.add(insn.name);
        node = new CallNode(newtrace);
        node.offset = endOffset;
        RankChecker.calls.put(insn, node);
        return node;
      } else {
        assert !node.open;
        node.open = true;
        return node;
      }
    }
    
    static Set<String> langCalls = new HashSet<>();

    List<SSAFrame> invokeOutput = null; 

    private SSAEncoder analyzeInvoke(int insnIndex, String owner, MethodNode methodNode,
        List<? extends Value> args, int offset) throws AnalyzerException {

//      if (owner.replace('/', '.').startsWith("java.lang")) {
         langCalls.add(owner + "." + methodNode.name + " " + methodNode.desc + "\n");
//        return null;
//      }

 //     assert args.stream().allMatch(v -> !(v instanceof Object) || ((Object)v).parent == null);
      Value[] inputs = args.stream().toArray(Value[]::new);

      List<String> newtrace = new LinkedList<>(trace);
      newtrace.add(methodNode.name);
      SSAInterpreter interpreter = new SSAInterpreter(reader, ctx, inputs, current, offset, newtrace);
      SSAEncoder analyzer = new SSAEncoder(interpreter, reader);

      // LinkedList<String> trace = new LinkedList<>(this.trace);
      // trace.addFirst(owner + "." + name + " " + desc + "\n");

      assert (methodNode.access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) == 0;
      analyzer.analyze(owner, methodNode);
      
      encoders.put(offset, analyzer);
      
      assert invokeOutput == null;
      List<SSAFrame> outputs = analyzer.getReturned();
      invokeOutput = outputs;
      
      callThrows[insnIndex] = analyzer.getThrown().stream().map(f ->
          { SSAFrame g = new SSAFrame(f.parent); 
              g.result = f.result;
              return g;
            }).collect(Collectors.toList());

      return analyzer;
    }

    public Value interpretConst(int insnIndex, MethodInsnNode minsn, List<? extends Value> values) {
      assert minsn.owner.equals("java/lang/String");
      
      if (minsn.name.equals("getBytes")) {
        return null;
      }
      
      String str = (String) ((MyString)values.get(0)).val;
      Type[] argTypes = Type.getArgumentTypes(minsn.desc);
      Class<?>[] argClasses = new Class<?>[argTypes.length];
      java.lang.Object[] args = new java.lang.Object[argTypes.length];
      for (int i = 0; i < argClasses.length; i++) {
        try {
          switch(argTypes[i].getSort()) {
            case Type.OBJECT:
              argClasses[i] = reader.cl.loadClass(argTypes[i].getClassName());
              break;
            case Type.BYTE:
              argClasses[i] = byte.class;
              break;
            case Type.INT:
              argClasses[i] = int.class;
              break;
            case Type.BOOLEAN:
              argClasses[i] = boolean.class;
              break;
            default:
              assert false;
          }
        } catch (ClassNotFoundException e) {
          assert false;
        }
        assert values.get(i) instanceof MyString;
        args[i] = ((MyString)values.get(i)).val;
      }
      
      Class<?> c = str.getClass();
      try {
        Method m = c.getDeclaredMethod(minsn.name, argClasses);
        m.setAccessible(true);
        java.lang.Object res = m.invoke(str, args);
        if (res == null)
          return new None(insnIndex);
        else if (res instanceof Integer) {
          return mkConst((Integer)res, insnIndex);
        } else if (res instanceof Byte) {
          return mkConst((Byte)res, insnIndex);
        } else
          return new MyString(insnIndex, (String) res);
      } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        assert false;
      }
      
      assert false;
      
      return null;
    }
    
    Value interpretStringBuilder(int insnIndex, MethodInsnNode minsn, List<? extends Value> values) {
      assert invokeOutput == null;
      
      MyStringBuilder prev = (MyStringBuilder) values.get(0), next = prev;
      SSAFrame output = new SSAFrame(current, values.size(), 0, null);
      for (int i = 0; i < values.size(); i++) {
        output.setLocal(i, values.get(i));
      }
      
      output.result = null;
      
      switch(minsn.name) {
        case "<init>":
          assert values.size() == 1;
          break;
        case "append":
          assert values.size() == 2;
          if (values.get(1) instanceof MyString) {
            MyString str = (MyString) values.get(1);
            output.result = next = new MyStringBuilder(insnIndex, prev.val.concat(str.val));
          } else if (values.get(1) instanceof Expr) {
            Expr expr = (Expr) values.get(1);
            output.result = next = new MyStringBuilder(insnIndex, prev.val.concat(expr.expr.toString()));
          }
          break;
        case "toString":
          assert values.size() == 1;
          output.result = new MyString(insnIndex, prev.val);
          break;
        default:
          assert false;
      }
      
      SSAFrame frame = output;
      while (frame != null) {
        for (int i = 0; i < frame.getLocals(); i++) {
          if (frame.getLocal(i) == prev) 
            frame.setLocal(i, next);
        }
        for (int i = 0; i < frame.getMaxStackSize(); i++) {
          if (frame.getStack(i) == prev) 
            frame.setStack(i, next);
        }
        frame = frame.parent;
      }
      
      invokeOutput = List.of(output);
      
      return null;
    }
    
    Value interpretClass(int insnIndex, MethodInsnNode minsn, List<? extends Value> values) {
      MyClass clazz = (MyClass) values.get(0);
      SSAFrame output = new SSAFrame(current, values.size(), 0, null);
      for (int i = 0; i < values.size(); i++) {
        output.setLocal(i, values.get(i));
      }
      
      output.result = null;
      
      switch(minsn.name) {
        case "desiredAssertionStatus":
          try {
            Class<?> c = reader.cl.loadClass(clazz.val.getClassName());
            output.result = mkConst(c.desiredAssertionStatus(), insnIndex);
            break;
          } catch (ClassNotFoundException e) {
            assert false; 
          }
        default:
          assert false;
      }
      
      invokeOutput = List.of(output);
      
      return null;
    }
    
    Value interpretThrowable(int insnIndex, MethodInsnNode minsn, List<? extends Value> values) {
      assert invokeOutput == null;
      
      MyThrowable prev = (MyThrowable) values.get(0), next = prev;
      
      SSAFrame output = new SSAFrame(current, values.size(), 0, null);
      for (int i = 0; i < values.size(); i++) {
        output.setLocal(i, values.get(i));
      }
      
      output.result = null;
      
      switch (minsn.name) {
        case "<init>": {
          Type[] argTypes = Type.getArgumentTypes(minsn.desc);
          if (argTypes.length == 0) {
            next = new MyThrowable(insnIndex, prev.type, new MyString(insnIndex, ""), null);
          } else if (argTypes.length == 1 && argTypes[0].getInternalName().equals("java/lang/String")) {
            assert values.size() == 2;
            next = new MyThrowable(insnIndex, prev.type, (MyString) values.get(1), null);
          } else
            assert false;
          
          break;
        }
        default:
          assert false;
      }
      
      invokeOutput = List.of(output);
      SSAFrame frame = output;
      while (frame != null) {
        for (int i = 0; i < frame.getLocals(); i++) {
          if (frame.getLocal(i) == prev) 
            frame.setLocal(i, next);
        }
        for (int i = 0; i < frame.getMaxStackSize(); i++) {
          if (frame.getStack(i) == prev) 
            frame.setStack(i, next);
        }
        frame = frame.parent;
      }
      
      return null;
    }
    
    Value interpretAny(int insnIndex, MethodInsnNode minsn, List<? extends Value> values) {
      assert values.stream().noneMatch(v -> v instanceof Object);

      Type returnType = Type.getReturnType(minsn.desc);
      switch(returnType.getSort()) {
        case Type.INT:
          if (assignmentSymbol[insnIndex] == null)
            assignmentSymbol[insnIndex] = (Expr) mkFreshModel(ctx, "I", minsn.name, Integer.toString(insnIndex + beginOffset), insnIndex + beginOffset);
          break;
        case Type.VOID:
          return null;
        case Type.OBJECT:
        default:
          assert false;
      }
      
      assert assignmentSymbol[insnIndex] != null;
      
      SSAFrame output = new SSAFrame(current, values.size(), 0, null);
      for (int i = 0; i < values.size(); i++) {
        output.setLocal(i, values.get(i));
      }
      
      output.result = assignmentSymbol[insnIndex];
      invokeOutput = List.of(output);
      
      return null;
    }
    
    Value interpretInteger(int insnIndex, MethodInsnNode minsn, List<? extends Value> values) {
      assert invokeOutput == null;
      
      MyPrimitive prev = (MyPrimitive) values.get(0), next = prev;
      
      SSAFrame output = new SSAFrame(current, values.size(), 0, null);
      for (int i = 0; i < values.size(); i++) {
        output.setLocal(i, values.get(i));
      }
      
      output.result = null;
      
      switch (minsn.name) {
        case "<init>": {
          Type[] argTypes = Type.getArgumentTypes(minsn.desc);
          if (argTypes.length == 1 && argTypes[0] == Type.INT_TYPE) {
            assert values.size() == 2;
            next = new MyPrimitive(insnIndex, ((Expr) values.get(1)).expr);
          } else
            assert false;
          
          break;
        }
        default:
          assert false;
      }
      
      invokeOutput = List.of(output);
      SSAFrame frame = output;
      while (frame != null) {
        for (int i = 0; i < frame.getLocals(); i++) {
          if (frame.getLocal(i) == prev) 
            frame.setLocal(i, next);
        }
        for (int i = 0; i < frame.getMaxStackSize(); i++) {
          if (frame.getStack(i) == prev) 
            frame.setStack(i, next);
        }
        frame = frame.parent;
      }
      
      return null;
    }
    
    @Override
    public Value naryOperation(AbstractInsnNode insn, List<? extends Value> values) throws AnalyzerException {
      final int insnIndex = instructions.indexOf(insn);
      assert insn instanceof MethodInsnNode;
      MethodInsnNode minsn = (MethodInsnNode) insn;
      
      if (minsn.owner.equals("java/lang/StringBuilder")) 
        return interpretStringBuilder(insnIndex, minsn, values);
      else if (minsn.owner.equals("java/lang/String"))
        return interpretConst(insnIndex, minsn, values);
      else if (minsn.owner.equals("java/lang/Class"))
        return interpretClass(insnIndex, minsn, values);
//      else if (minsn.owner.equals("java/lang/Integer"))
//        return interpretInteger(insnIndex, minsn, values);
      
      String className = null;
      switch (insn.getOpcode()) {
        case Opcodes.INVOKESTATIC:
        case Opcodes.INVOKESPECIAL: 
          className = minsn.owner;
          break;
        case Opcodes.INVOKEVIRTUAL: 
        case Opcodes.INVOKEINTERFACE:
          assert values.size() > 0;
          if (values.get(0) instanceof Any) {
            return interpretAny(insnIndex, minsn, values);
          }
          assert values.get(0) instanceof Object;
          className = ((Object)values.get(0)).type.getInternalName();
          break;
        default:
          assert false;
      }

      ClassNode classNode;
      MethodNode methodNode;
      try {
        classNode = reader.resolveOwner(className, minsn.name, minsn.desc);
        methodNode = reader.resolveMethodNode(classNode.name, minsn.name, minsn.desc);
      } catch (ClassNotFoundException | NoSuchMethodException e) {
        throw new AnalyzerException(insn, e.getMessage(), e);
      }
    
      {
        Class<?> clazz;
        try {
          clazz = reader.cl.loadClass(className.replace("/", "."));
        } catch (ClassNotFoundException e) {
          throw new AnalyzerException(insn, e.getMessage(), e);
        }
        if (Throwable.class.isAssignableFrom(clazz)) {
          return interpretThrowable(insnIndex, minsn, values);
        } 
      }
      
      CallNode call = openCall(trace, minsn);
      SSAEncoder p = analyzeInvoke(insnIndex, className, methodNode, values, call.offset);
      call.close(p);
      if (p != null && endOffset == call.offset)
        endOffset = p.interpreter.endOffset;

      callEncoder[insnIndex] = p;
      
      List<SSAFrame> results = (p != null) ? p.getReturned() : null;
      assert Type.getReturnType(minsn.desc) == Type.VOID_TYPE || results.size() >= 1;

      return null;
    }

    @Override
    public void returnOperation(AbstractInsnNode insn, Value value, Value expected)
        throws AnalyzerException {
      assert value != null;
      assert expected == null;
    }

    public Expr merge(Value oldValue, Expr newValue, int pos, String prefix) {
      // extend an existing merge and stop
      if (oldValue instanceof Phi && oldValue.creationIndex == pos + beginOffset) { 
//        assert label != null;
//        assert instructions.indexOf(label) == oldValue.creationIndex;
        merges[oldValue.creationIndex - beginOffset].get(oldValue.toString()).add(newValue.toString());
        return (Expr)oldValue;
      }
      
      // propagate fresh constants forward
      if (oldValue.creationIndex == newValue.creationIndex)
        return newValue;
      
      // propagate fresh merges forward
      if (newValue instanceof Phi && oldValue.creationIndex == ((Phi) newValue).oldIndex)
        return newValue;
      
      // create fresh merge
      return mkMerge(oldValue, newValue, pos, prefix);
    }
    
    public Expr merge(Expr oldValue, Expr newValue) {
      assert oldValue != null && newValue != null;
      assert oldValue.creationIndex == newValue.creationIndex ||
          newValue instanceof Phi && oldValue.creationIndex == ((Phi) newValue).oldIndex;

      return newValue;
    }

    public Array merge(Array oldValue, Array newValue) {
      assert false;
      return null;
      //return new Array(oldValue.creationIndex, merge(oldValue.length, newValue.length));
    }
    
    @Override
    public Value merge(Value oldValue, Value newValue) {
      assert oldValue != null && newValue != null;
      assert (oldValue instanceof Expr) == (newValue instanceof Expr);
      assert (oldValue instanceof Object) == (newValue instanceof Object);
      assert (oldValue instanceof Array) == (newValue instanceof Array);
      assert (oldValue instanceof Any) == (newValue instanceof Any);
      assert (oldValue instanceof None) == (newValue instanceof None);
      assert false;
      
      if (oldValue instanceof Expr) {
        return merge((Expr)oldValue, (Expr) newValue);
      } else if (oldValue instanceof Object) {
        return merge((Object)oldValue, (Object) newValue);
      } else if (oldValue instanceof Array){
        return merge((Array)oldValue, (Array) newValue);
      } else if (oldValue instanceof Any) {
        return oldValue;
      } else {
        assert false;
        return null;
      }
    }

  };

  static class Jump {
    BoolExpr condition;
    Value[] output;
    int dst;

    public Jump(BoolExpr condition, Value[] output, int dst) {
      this.condition = condition;
      this.output = output;
      this.dst = dst;
    }

    @Override
    public String toString() {
      return "jump " + dst + " : " + condition + " -> " + Arrays.toString(output);
    }
  }
  
  static class Call {
    int offset;
    Value[] input, output;
    Value result;
    List<Throw> exceptions;
    
    public Call(int offset, Value[] input, Value[] output, Value result, List<Throw> exceptions) {
      this.offset = offset;
      this.input = input;
      this.output = output;
      this.result = result;
      this.exceptions = exceptions;
    }
    
    @Override
    public String toString() {
      return "call " + offset + " : " + Arrays.toString(input) + 
          " -> " + Arrays.toString(output) + ", " + result + 
          " throws " + exceptions.stream().map(e -> e.exception).collect(Collectors.toList());
    }
  }
  
  static class Return {
    BoolExpr condition;
    Value[] output;
    Value result;
    
    public Return(BoolExpr condition, Value[] output, Value result) {
      this.condition = condition;
      this.output = output;
      this.result = result;
    }

    @Override
    public String toString() {
      return "return " + condition + " -> " + Arrays.toString(output) + ", " + result;
    }
  }
  
  static class Throw {
    BoolExpr condition;
    Value[] output;
    MyThrowable exception;
    
    public Throw(BoolExpr condition, Value[] output, MyThrowable result) {
      this.condition = condition;
      this.output = output;
      this.exception = result;
    }

    @Override
    public String toString() {
      return "throw " + condition + " -> " + Arrays.toString(output) + ", " + exception;
    }
  }
  
  static class Block {
    String title;
    Value[] input;
    LinkedList<BoolExpr> statements = new LinkedList<>();
    LinkedList<Jump> jumps = new LinkedList<>();
    LinkedList<Call> calls = new LinkedList<>();
    LinkedList<Return> returned = new LinkedList<>();
    LinkedList<Throw> thrown = new LinkedList<>();
    Map<Throw, Integer> catches = new HashMap<>();
    
    public Block(String title, Value[] input) {
      this.title = title;
      this.input = input;
    }
    
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(title + "\n" + Arrays.toString(input) + "\n");
      statements.forEach(a -> sb.append("  " + a + "\n"));
      calls.forEach(a -> sb.append("  " + a + "\n"));
      jumps.forEach(a -> sb.append("  " + a + "\n"));
      returned.forEach(a -> sb.append("  " + a + "\n"));
      thrown.forEach(a -> sb.append("  " + a + "\n"));
      catches.forEach((a,b) -> sb.append("  catch " + a + "->" + b + "\n"));
      return sb.toString();
    }
  }

  static class Procedure {
    Value[] args;
    ArrayList<Block> controlFlow;
    
    int [] insnIndexToBlock;
    String[] varNames;
    String[] varDescriptors;

    
    public Procedure(Value[] args, ArrayList<Block> controlFlow, int [] insnIndexToBlock) {
      this.args = args;
      this.controlFlow = controlFlow;
      this.insnIndexToBlock = insnIndexToBlock;
    }
    
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < controlFlow.size(); i++) {
        sb.append(i + ": " + controlFlow.get(i) + "\n");
      }
      return sb.toString();
    }
  }
  
  static class Program {
    Call main;
    Map<Call, Procedure> linkage;
    
    public Program(Call main, Map<Call, Procedure> linkage) {
      this.main = main;
      this.linkage = linkage;
    }
  }
  
  static class SSAEncoder extends Analyzer<Value> {
    
    final SSAInterpreter interpreter;
    final Context ctx;
    final Map<String, Map<String, Value>> initGlobal;
    
    InsnList instructions = null;
    
    boolean[] breakPoint;
    boolean[] continues;
    int[] jumpTarget;

    static Set<Integer> havocIndices = new HashSet<>();
    
    MethodNode node;
    ClassReader reader;
    String owner;
    
    public SSAEncoder(SSAInterpreter interpreter, ClassReader reader) {
      super(interpreter);
      this.interpreter = interpreter;
      this.ctx = interpreter.ctx;
      this.reader = reader;
      this.initGlobal = Collections.emptyMap();
    }

    public SSAEncoder(SSAInterpreter interpreter, ClassReader reader, Map<String, Map<String, Value>> initGlobal) {
      super(interpreter);
      this.interpreter = interpreter;
      this.ctx = interpreter.ctx;
      this.reader = reader;
      this.initGlobal = initGlobal;
    }
    
    @Override
    protected Frame<Value> newFrame(int numLocals, int numStack) {
      if (interpreter.input != null)
        return new SSAFrame(interpreter.input, numLocals, numStack, getFrames());
      else
        return new SSAFrame(initGlobal, numLocals, numStack, getFrames());
    }

    @Override
    protected Frame<Value> newFrame(Frame<? extends Value> _prev) {
      if (caught != null) {
        SSAFrame res = caught;
        caught = null;
        return res;
      }
      assert _prev instanceof SSAFrame;
      SSAFrame prev = (SSAFrame) _prev;
      SSAFrame result = new SSAFrame(prev);
      return result;
    }

    TryCatchBlockNode getTightest(List<TryCatchBlockNode> handlers, Type exception) {
      try {
        Class<?> clazz = reader.cl.loadClass(exception.getInternalName().replace("/", "."));
        TryCatchBlockNode tightest = null;
        Class<?> tightestClass = null;
        for (TryCatchBlockNode handler : handlers) {
          Class<?> catchClass = handler.type != null ? reader.cl.loadClass(handler.type.replace("/", ".")) : Throwable.class;
          if (catchClass.isAssignableFrom(clazz)) {
            if (tightestClass != null) {
              if (tightestClass.isAssignableFrom(catchClass)) {
                tightestClass = catchClass;
                tightest = handler;
              }
            } else {
              tightestClass = catchClass;
              tightest = handler;
            }
          }
        }
        return tightest;
      } catch (ClassNotFoundException e) {
        assert false;
        return null;
      }
    }
    
    SSAFrame caught = null;
    
    @Override
    protected boolean newControlFlowExceptionEdge(int insnIndex, TryCatchBlockNode tryCatchBlock) {
      List<SSAFrame> thrown = interpreter.thrown[insnIndex];
      if (thrown != null) {
        List<SSAFrame> caught = new LinkedList<>();
        for (SSAFrame frame : thrown) {
          assert frame.result != null;
          MyThrowable exception = (MyThrowable) frame.result;
          TryCatchBlockNode tightest = getTightest(getHandlers(insnIndex), exception.type);
          if (tightest == tryCatchBlock) {
            caught.add(frame);
          }
        }
        assert caught.size() <= 1;
        if (caught.isEmpty()) {
          return false;
        } else {
          this.caught = caught.get(0); 
          return true;
        }
      } else {
        return false;
      }
    }
    
    @Override
    protected void init(String owner, MethodNode method) throws AnalyzerException {
      instructions = method.instructions;
      interpreter.init(method);
      this.node = method;
      this.owner = owner;
      
      breakPoint = new boolean[instructions.size()];
      continues = new boolean[instructions.size()];
      jumpTarget = new int[instructions.size()];
      
      Arrays.fill(breakPoint, false);
      Arrays.fill(continues, false);
      Arrays.fill(jumpTarget, -1);
     
//      Frame<Value>[] frames = getFrames();
//      for (int i = 0; i < instructions.size(); i++) {
//        if (havocIndices.contains(i)) {
//          Frame<Value> f = frames[i] = newFrame(method.maxLocals, method.maxStack);
//          for (int var = 0, end = method.maxLocals; var != end; var++)
//            f.setLocal(var, mkEmpty(var, i));
//        }
//      }
    }

    @Override
    protected void newControlFlowEdge(int insnIndex, int successorIndex) {
      if (successorIndex == insnIndex + 1) {
        continues[insnIndex] = true;
      } else {
        jumpTarget[insnIndex] = successorIndex;
        if (successorIndex > 0)
          breakPoint[successorIndex - 1] = true;
        breakPoint[insnIndex] = true;
      }
    }

    Value[] mkInsnInput(int insnIndex) {
//      Frame<Value> frame = getFrames()[insnIndex];
//      Value[] input = new Value[frame.getLocals() + frame.getStackSize()];
//      int i = 0;
//      for (int j = 0; j < frame.getLocals(); i++, j++) {
//        input[i] = frame.getLocal(j);
//      }
//      
//      return input;
      return frameToVector((SSAFrame) getFrames()[insnIndex]);
    }

    static void addLeafs(List<Value> l, Value v) {
      if (v instanceof Object) {
        for (Value u : ((Object)v).fields.values()) {
          addLeafs(l, u);
        }
      } else {
        l.add(v);
      }
    }
    
    static Value[] frameToVector(SSAFrame frame) {
      Stack<SSAFrame> frames = new Stack<>();
      frames.push(frame);
      while (frame.parent != null) {
        frame = frame.parent;
        frames.push(frame);
      }
      
//      LinkedList<Value> leafs = new LinkedList<>();
//      while (!frames.isEmpty()) {
//        frame = frames.pop();
//        for (int var = 0; var < frame.getLocals(); var++) {
//          addLeafs(leafs, frame.getLocal(var));
//        }
//        for (int var = 0; var < frame.getStackSize(); var++) {
//          addLeafs(leafs, frame.getStack(var));
//        }
//      }
//      return leafs.toArray(new Value[leafs.size()]);

      LinkedList<Value> vals = new LinkedList<>();
      while (!frames.isEmpty()) {
        frame = frames.pop();
        for (int var = 0; var < frame.getLocals(); var++) {
          vals.add(frame.getLocal(var));
        }
        for (int var = 0; var < frame.getStackSize(); var++) {
          vals.add(frame.getStack(var));
        }
      }
      return vals.toArray(new Value[vals.size()]);
    }
    
    Value[] mkInsnOutput(int insnIndex) {
//      Value[] output = mkInsnInput(insnIndex);
//      if (interpreter.assignmentVar[insnIndex] >= 0)
//        output[interpreter.assignmentVar[insnIndex]] = interpreter.assignmentSymbol[insnIndex];
//      
      return frameToVector(interpreter.output[insnIndex]);
    }

//    Call mkCall(int insnIndex) {
//      MethodInsnNode callNode = (MethodInsnNode) instructions.get(insnIndex);
//      return new Call(callNode.owner, callNode.name, callNode.desc, interpreter.callArgs[insnIndex], interpreter.callResult[insnIndex]);
//    }
    
    boolean mayBeReachable(int index) {
      return !((SSAFrame)getFrames()[index]).unreachable;
    }
    
    Block mkBasicBlock(int entryIndex, int exitIndex, int[] insnToBlock) {
      Block b = new Block(this.owner + "." + node.name + ":" + entryIndex, mkInsnInput(entryIndex));
      
      for (int index = entryIndex; index <= exitIndex; index++) {
        assert index == exitIndex || jumpTarget[index] < 0;
        //assert index == exitIndex || interpreter.result[index] == null;
        
        // temporary solution; the interpreter should be responsible of making statements while the encoder just collects them
        if (interpreter.assignmentExpr[index] != null)
          b.statements.add(ctx.mkEq(interpreter.assignmentSymbol[index].expr, interpreter.assignmentExpr[index]));
//        if (interpreter.callArgs[index] != null)
//          b.calls.add(mkCall(index));
        if (interpreter.jumpCondition[index] != null)
          b.statements.add(ctx.mkEq(interpreter.jumpSymbol[index], interpreter.jumpCondition[index]));
      }
      
      Value[] output = mkInsnOutput(exitIndex);

      if (continues[exitIndex] && jumpTarget[exitIndex] >= 0) {
        if (mayBeReachable(exitIndex + 1))
          b.jumps.add(new Jump(ctx.mkNot(interpreter.jumpSymbol[exitIndex]), output, insnToBlock[exitIndex + 1]));
        if (mayBeReachable(jumpTarget[exitIndex]))
          b.jumps.add(new Jump(interpreter.jumpSymbol[exitIndex], output, insnToBlock[jumpTarget[exitIndex]]));
      } else if (continues[exitIndex]) {
        if (mayBeReachable(exitIndex + 1))
          b.jumps.add(new Jump(ctx.mkTrue(), output, insnToBlock[exitIndex + 1]));
      } else if (jumpTarget[exitIndex] >= 0) {
        if (mayBeReachable(jumpTarget[exitIndex]))
          b.jumps.add(new Jump(ctx.mkTrue(), output, insnToBlock[jumpTarget[exitIndex]]));
      } else if (interpreter.returned[exitIndex] != null) {
        assert interpreter.returned[exitIndex].size() == 1;
        b.returned.add(new Return(ctx.mkTrue(), output, interpreter.returned[exitIndex].get(0).result));
      } else if (interpreter.returned[exitIndex - 1] != null) { 
        assert instructions.get(exitIndex).getType() == AbstractInsnNode.LABEL;
        assert interpreter.returned[exitIndex-1].size() == 1;
        b.returned.add(new Return(ctx.mkTrue(), output, interpreter.returned[exitIndex-1].get(0).result));
      } else if (interpreter.thrown[exitIndex] != null){
        for (SSAFrame thrown : interpreter.thrown[exitIndex]) {
          assert thrown.result instanceof MyThrowable;
          b.thrown.add(new Throw(ctx.mkTrue(), output, (MyThrowable) thrown.result));
        }
      } else {
        assert false;
        b.returned.add(new Return(ctx.mkTrue(), output, null)); //void
      }

      return b;
    }

    int[] mkInsnIndexToBlock(int offset) {
      int [] insnToBlock = new int[instructions.size()];
      int blockIndex = offset;
      for (int i = 0; i != instructions.size(); i++) {
        insnToBlock[i] = blockIndex;
        if (breakPoint[i])
          blockIndex++;
      }
      return insnToBlock;
    }
    
    int nBlocks() {
      int n = 1;
      for (int i = 0; i != instructions.size(); i++) {
        if (breakPoint[i])
          n++;
      }
      for (int i = 0; i != instructions.size(); i++) {
        if (interpreter.callEncoder[i] != null)
          n += interpreter.callEncoder[i].nBlocks();
      }
      return n;
    }
    
    void mkProcedure(Block[] cfg, int offset) {
      int [] insnToBlock = mkInsnIndexToBlock(offset);
      
      int blockIndex = offset;
      int entryIndex = 0;
      for (int i = 1; i != instructions.size(); i++) {
        if (breakPoint[i]) {
          if (!((SSAFrame) getFrames()[i]).unreachable) {
            cfg[blockIndex] = mkBasicBlock(entryIndex, i, insnToBlock);
          }
          entryIndex = i + 1;
          blockIndex++;
        }
      }
      int exitIndex = instructions.size() - 1;
      if (getFrames()[exitIndex] == null) exitIndex--;
      cfg[blockIndex++] = mkBasicBlock(entryIndex, exitIndex, insnToBlock);
      
      for (int i = 1; i != instructions.size(); i++) {
        if (interpreter.callEncoder[i] != null) {
          interpreter.callEncoder[i].mkProcedure(cfg, blockIndex);
          List<Throw> exceptions = interpreter.callThrows[i].stream()
              .map(f -> new Throw(ctx.mkTrue(), frameToVector(f), (MyThrowable) f.result)).collect(Collectors.toList());
          cfg[insnToBlock[i]].calls.add(new Call(blockIndex, interpreter.callInput[i], interpreter.callOutput[i], 
              interpreter.callResult[i], exceptions));
          blockIndex += interpreter.callEncoder[i].nBlocks();
        }
      }
    }
    
    Procedure mkProcedure() {
      int [] insnToBlock = mkInsnIndexToBlock(0);
      
      Block[] cfg = new Block[nBlocks()];
      
      mkProcedure(cfg, 0);
      
      return new Procedure(interpreter.args, new ArrayList<>(Arrays.asList(cfg)), insnToBlock);
    }
    
//    Set<Value> getResult() {
//      Set<Value> result = new HashSet<>();
//      for (int i = 0; i < instructions.size(); i++) {
//        if (interpreter.result[i] != null)
//          result.add(interpreter.result[i]);
//      }
//      return result;
//    }
    
//    Set<SSAFrame> getOutput() {
//      Set<SSAFrame> result = new HashSet<>();
//      for (int i = 0; i < instructions.size(); i++) {
//        if (breakPoint[i] && !continues[i] && jumpTarget[i] < 0) {
//          SSAFrame ssaframe = (SSAFrame) getFrames()[i];
//          if (!ssaframe.unreachable)
//            result.add((SSAFrame) getFrames()[i]);
//        }
//      }
//      int exitIndex = instructions.size() - 1;
//      if (getFrames()[exitIndex] == null) exitIndex--;
//      result.add((SSAFrame) getFrames()[exitIndex]);
//      return result;
//    }
    
    List<SSAFrame> getReturned() {
      List<SSAFrame> result = new LinkedList<>();
      for (int i = 0; i < instructions.size(); i++) {
        if (interpreter.returned[i] != null) {
          for (SSAFrame returned : interpreter.returned[i]) {
            result.add(returned);
          }
        }
      }
      return result;
    }
    
    List<SSAFrame> getThrown() {
      List<SSAFrame> result = new LinkedList<>();
      for (int i = 0; i < instructions.size(); i++) {
        if (interpreter.thrown[i] != null) {
          for (SSAFrame thrown : interpreter.thrown[i]) {
            result.add(thrown);
          }
        }
        if (interpreter.callThrows[i] != null) {
          result.addAll(interpreter.callThrows[i]);
        }
        // remove catches here
      }
      return result;
    }
    
    Value[] getInput() {
      return mkInsnInput(0);
    }
    
    @Override
    public String toString() {
      try {
        ClassReader newReader = new ClassReader(reader.cl);
        final MethodNode node = newReader.resolveMethodNode(owner, this.node.name, this.node.desc);

        Textifier textifier = new Textifier(Opcodes.ASM8) {
          Map<Label, Integer> labelToIndex = IntStream.range(0, node.instructions.size()).boxed()
              .filter(i -> node.instructions.get(i).getType() == AbstractInsnNode.LABEL)
              .collect(Collectors.toMap(i -> ((LabelNode) node.instructions.get(i)).getLabel(),
                  Function.identity()));

          @Override
          protected void appendLabel(Label l) {
            if (labelNames == null)
              labelNames = new HashMap<>();
            String name = labelNames.get(l);
            if (name == null) {
              name = "L" + labelToIndex.get(l);
              labelNames.put(l, name);
            }
            super.appendLabel(l);
          }
        };
        TraceMethodVisitor traceMethodVisitor = new TraceMethodVisitor(textifier);
        node.accept(traceMethodVisitor);

        Frame<Value>[] frames = getFrames();

        StringBuilder sb = new StringBuilder();
        sb.append(owner + "." + this.node.name + "\n");
        for (int i = 0; i < node.instructions.size(); ++i) {
          sb.append(i + interpreter.beginOffset + "|" + i);
          String is = insnToStr(textifier, traceMethodVisitor, node.instructions.get(i), newReader);
          sb.append(is);
          sb.append(" ".repeat(Math.max(30 - sb.length(), 1)));
          sb.append(frames[i]);
          sb.append(' ');
          if (interpreter.assignmentSymbol[i] != null) {
            sb.append(interpreter.assignmentSymbol[i] + "=" + interpreter.assignmentExpr[i] + " ");
          }
          if (interpreter.jumpCondition[i] != null) {
            sb.append(interpreter.jumpSymbol[i] + "=" + interpreter.jumpCondition[i]);
          }
          if (interpreter.merges[i] != null)
            interpreter.merges[i].forEach((k, v) -> sb.append(k + "=" + v + " "));
          // if (interpreter.callArgs[i] != null)
          // sb.append(Arrays.toString(interpreter.callArgs[i]));
          // if (interpreter.callResult[i] != null)
          // sb.append(" -> " + interpreter.callResult[i]);
          sb.append("\n");
        }
        for (Map.Entry<Integer, SSAEncoder> entry : interpreter.encoders.entrySet()) {
          sb.append("\n");
          sb.append(entry.getValue().toString());
        }
        return sb.toString();
      } catch (ClassNotFoundException | NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
  };

  static void addEncodeEqIf(Context ctx, Value dst, Value src, BoolExpr condition, List<BoolExpr> list, Set<Value> done) {
    if (done.contains(dst))
      return;
    
    if (dst instanceof Expr && src instanceof Expr)
      list.add(ctx.mkImplies(condition, ctx.mkEq(((Expr)dst).expr, ((Expr)src).expr)));
    else if (dst instanceof Array && src instanceof Array)
      assert false;
      //return ctx.mkImplies(condition, ctx.mkEq(((Array)output).length.expr, ((Array)input).length.expr));
    else if (dst instanceof Any) 
      return;
    else if (src instanceof None)
      return;
    else if (dst instanceof Object && src instanceof Object) {
      Object ooutput = (Object) dst, oinput = (Object) src;
      assert ooutput.type.equals(oinput.type);
      for (Map.Entry<String, Value> e : ooutput.fields.entrySet()) {
        Value lVar = e.getValue(), rVar = oinput.fields.get(e.getKey());
        if (!lVar.equals(rVar)) {
          addEncodeEqIf(ctx, lVar, rVar, condition, list, done);
        }
      }
    } else assert false;
    
    done.add(dst);
  }
  
  static LinkedList<BoolExpr> encodeEqIf(Context ctx, Value[] dst, Value[] src, BoolExpr condition) {
    assert dst.length <= src.length;
    
    HashSet<Value> done = new HashSet<>();
    LinkedList<BoolExpr> stmt = new LinkedList<>();
    for (int var = 0, nVars = dst.length; var < nVars; var++) {
      Value lVar = dst[var];
      Value rVar = src[var];
      if (!lVar.equals(rVar)) {
        addEncodeEqIf(ctx, lVar, rVar, condition, stmt, done);
      }
    }
    
    return stmt;
  }
  
  static List<BoolExpr> encodeEqIf(Context ctx, Value dst, Value src, BoolExpr condition) {
    LinkedList<BoolExpr> stmt = new LinkedList<>();
    HashSet<Value> done = new HashSet<>();
    if (!dst.equals(src))
      addEncodeEqIf(ctx, dst, src, condition, stmt, done);
    return stmt;
  }
  
  static void summarize(Context ctx, List<Block> cfg, List<Integer> sortedBlockIndices, 
      List<BoolExpr> statements, List<Jump> jumps, List<Return> returned, List<Throw> thrown) {
    assert !sortedBlockIndices.isEmpty();
 
    Map<Integer, LinkedList<Jump>> inputs = sortedBlockIndices.stream()
        .collect(Collectors.toMap(Function.identity(), i -> new LinkedList<Jump>()));
        
    int stackBase = cfg.get(sortedBlockIndices.get(0)).input.length;
    
    for (int blockIndex : sortedBlockIndices) {
      Block block = cfg.get(blockIndex);

      ArrayList<BoolExpr> entryDisjuncts = new ArrayList<>();
      while (!inputs.get(blockIndex).isEmpty()) {
        Jump input = inputs.get(blockIndex).removeFirst();
        
        assert input.output.length == block.input.length;
        for (BoolExpr eqIf : encodeEqIf(ctx, block.input, input.output, input.condition))
          statements.add(eqIf);
        
        entryDisjuncts.add(input.condition);
      }
      assert !entryDisjuncts.isEmpty() || blockIndex == sortedBlockIndices.get(0);
      
      // make entry condition
      BoolExpr entryCondition = null;
      switch(entryDisjuncts.size()) {
        case 0:
          //statements.add(ctx.mkEq(entryCondition, ctx.mkTrue()));
          entryCondition = ctx.mkTrue();
          break;
        case 1:
          //statements.add(ctx.mkEq(entryCondition, entryDisjuncts.get(0)));
          entryCondition = entryDisjuncts.get(0);
          break;
        default: {
          BoolExpr bigOr = ctx.mkOr(entryDisjuncts.toArray(BoolExpr[]::new));
          bigOr = (BoolExpr) bigOr.simplify();
          if (bigOr.isTrue())
            entryCondition = bigOr;
          else {
            entryCondition = ctx.mkBoolConst("B" + blockIndex);
            statements.add(ctx.mkEq(entryCondition, bigOr));
          }
        } 
      }
  
      // inherit statements and calls
      statements.addAll(block.statements);
      for (Call call : block.calls) {
        LinkedList<Return> callReturns = new LinkedList<>();
        LinkedList<Throw> callThrown = new LinkedList<>();
        summarize(ctx, cfg, topologicalSorting(cfg, call.offset), statements, Collections.emptyList(), callReturns, callThrown);
        
        for (Return ret : callReturns) {
          statements.addAll(encodeEqIf(ctx, call.output, ret.output, ret.condition));
          if (call.result != null) 
            statements.addAll(encodeEqIf(ctx, call.result, ret.result, ret.condition));
        }
        
        assert block.catches.isEmpty();
        for (Throw thr : callThrown) {
          thrown.add(new Throw(ctx.mkAnd(thr.condition, entryCondition), Arrays.copyOfRange(thr.output, 0, stackBase), thr.exception));          
        }
      }
      
      // push exit conditions
      for (Jump outJump : block.jumps) {
        BoolExpr jumpCondition = ctx.mkAnd(entryCondition, outJump.condition);
        jumpCondition = (BoolExpr) jumpCondition.simplify();
        
        if (inputs.containsKey(outJump.dst))
          inputs.get(outJump.dst).add(new Jump(jumpCondition, outJump.output, outJump.dst));
        else
          jumps.add(new Jump(jumpCondition, outJump.output, outJump.dst));
      }
      
      for (Return ret : block.returned) 
        returned.add(new Return(ctx.mkAnd(ret.condition, entryCondition), ret.output, ret.result));
      
      for (Throw thr : block.thrown)
        thrown.add(new Throw(ctx.mkAnd(thr.condition, entryCondition), thr.output, thr.exception));
    }
    
    assert inputs.values().stream().allMatch(l -> l.stream().allMatch(j -> j.dst == sortedBlockIndices.get(0)));
    
    for (LinkedList<Jump> lasso : inputs.values()) {
      if (!lasso.isEmpty())
        jumps.addAll(lasso);
    }
    
    assert jumps.stream().allMatch(o -> o.output.length == stackBase);
    assert returned.stream().allMatch(o -> o.output.length == stackBase);
    assert thrown.stream().allMatch(o -> o.output.length == stackBase);
  }
  
static Block summarize(Context ctx, List<Block> cfg, List<Integer> sortedBlockIndices) {
  if (sortedBlockIndices == null || sortedBlockIndices.isEmpty()) {
    throw new IllegalArgumentException("summarize: empty block list (no path / DAG found)");
  }
  int src = sortedBlockIndices.get(0);

  Block summary = new Block("summary", cfg.get(src).input);

  summarize(ctx, cfg, sortedBlockIndices, summary.statements, summary.jumps, summary.returned, summary.thrown);

  return summary;
}


  static Block summarize(Context ctx, List<Block> cfg, int src, int dst, Set<Integer> noDst) {
     List<Integer> dag = topologicallySortedMaxDAG(cfg, src, dst, noDst);
     if (dag == null || dag.isEmpty()) {
       return new Block("summary_unreachable", cfg.get(src).input);
     }
     return summarize(ctx, cfg, dag);
  }
 
  static Procedure encodeProcedure(Context ctx, ClassReader reader, 
      String owner, String name, String desc, Value[] args) throws ClassNotFoundException, NoSuchMethodException {
    MethodNode methodNode = reader.resolveMethodNode(owner, name, desc);
    
    SSAInterpreter interpreter = new SSAInterpreter(reader, ctx, args, null, 0, List.of(name));
    SSAEncoder encoder = new SSAEncoder(interpreter, reader);
    try {
      encoder.analyze(owner, methodNode);
    } catch (AnalyzerException e) {
      throw new RuntimeException(e);
    }
//    printEncoder(methodNode, encoder, reader);
    
    Procedure proc = encoder.mkProcedure();
    proc.varNames = interpreter.varNames;
    proc.varDescriptors = interpreter.varDescriptors;

    return proc;
  }

  static Value mkFreshModel(Context ctx, String desc, String prefix, String postfix, int creationIndex) {
    switch(desc) {
      case "I":
        return new Expr(creationIndex, ctx.mkIntConst(prefix + postfix));
      case "[I":
        assert false;
        return null;
        //return new Array(creationIndex, new Expr(creationIndex, ctx.mkIntConst(prefix + "length!" + Integer.toString(creationIndex))));
      case "Ljava/util/LinkedList;":
        Object obj = new Object(creationIndex, Type.getType(desc));
        obj.fields.put("size", new Expr(creationIndex, ctx.mkIntConst("size!" + postfix)));
        obj.fields.put("modCount", new Expr(creationIndex, ctx.mkIntConst("modCount!" + postfix)));
        obj.fields.put("first", new Any(creationIndex));
        obj.fields.put("last", new Any(creationIndex));
        return obj;
      case "[Ljava/lang/String;":
        return new None(0);
      default:
        assert false;
        return null;
    }
  }
  
  static Value[] encodeArgs(Context ctx, MethodNode methodNode) {
  Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
  Value[] args = new Value[argumentTypes.length];

  // instance 方法 slot0 是 this；static 方法 slot0 就是第一个形参
  boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) != 0;
  int baseSlot = isStatic ? 0 : 1;

  for (int i = 0; i < args.length; i++) {
    int slot = baseSlot + i;

    // 默认名（没有调试信息时也能跑）
    String name = "arg" + i;

    // 尽量从 LocalVariableTable 里找 index==slot 的那个变量名
    // 同一个 slot 可能有多个条目（作用域不同），优先选 start 最靠前的
    LocalVariableNode chosen = null;
    int bestStart = Integer.MAX_VALUE;

    if (methodNode.localVariables != null && methodNode.instructions != null) {
      for (LocalVariableNode v : methodNode.localVariables) {
        if (v == null) continue;
        if (v.index != slot) continue;
        if (v.name == null) continue;
        if ("this".equals(v.name)) continue;

        int startIdx = methodNode.instructions.indexOf(v.start);
        if (startIdx >= 0 && startIdx < bestStart) {
          bestStart = startIdx;
          chosen = v;
        }
      }
    }

    if (chosen != null && chosen.name != null && !chosen.name.isEmpty()) {
      name = chosen.name;
    }

    args[i] = mkFreshModel(ctx, argumentTypes[i].getDescriptor(), name, "!0", 0);
  }

  return args;
}

  
  static Procedure encodeProgram(Context ctx, ClassReader reader, String className, String methodName) throws NoSuchMethodException, ClassNotFoundException {

    MethodNode methodNode = reader.resolveMethodNode(className, methodName);
    Value[] args = encodeArgs(ctx, methodNode);

    Procedure procedure = encodeProcedure(ctx, reader, className, methodName, methodNode.desc, args);

    return procedure;
  }
  
  // =========================================================================
  // CFG utils
  // =========================================================================

  static boolean isAcyclic(Block[] cfg, boolean[] view, int entry) {
    assert view.length == cfg.length;

    int[] color = new int[cfg.length];
    Arrays.fill(color, 0);

    Stack<Integer> next = new Stack<>();
    next.push(2 * entry);
    while (!next.isEmpty()) {
      int index = next.pop();
      // if odd mark that the subgraph has been visited (that's color 2)
      if (index % 2 > 0) {
        color[index / 2] = 2;
        continue;
      }
      index = index / 2;
      
      if (color[index] == 2)
        continue;
      if (color[index] == 1)
        return false;

      // mark that the visit has began (that's color 1) 
      color[index] = 1;
      next.push(2 * index + 1);
      for (Jump edge : cfg[index].jumps) {
        if (view[edge.dst])
          next.push(2 * edge.dst);
      }
    }
    
    return true;
  }

  static List<Integer> topologicalSorting(List<Block> cfg, int entry) {
    LinkedList<Integer> sorting = new LinkedList<>();
        
    int[] color = new int[cfg.size()];
    Arrays.fill(color, 0);

    Stack<Integer> next = new Stack<>();
    next.push(2 * entry);
    while (!next.isEmpty()) {
      int indexPlusBit = next.pop();
      int index = indexPlusBit / 2; 
      // if odd mark that the subgraph has been visited (that's color 2)
      if (indexPlusBit % 2 > 0) {
        color[index] = 2;
        sorting.addFirst(index);
        continue;
      }

      if (color[index] == 2)
        continue;
      
      assert color[index] != 1 : "Sorting a cyclic CFG";

      // mark that the visit has began (that's color 1)
      color[index] = 1;
      next.push(2 * index + 1);
      for (Jump edge : cfg.get(index).jumps) {
        next.push(2 * edge.dst);
      }
    }

//    assert sorting.stream().distinct().count() == cfg.length;
    
    return sorting;
  }
  
  static int maxDAG(Block[] cfg, int src, int dst, int[] color) {
    if (color[src] != 0)
      return color[src];
    
    color[src] = 1;
    int endColor = 0;
    for (Jump edge : cfg[src].jumps) {
      int adjColor = edge.dst != dst ? maxDAG(cfg, edge.dst, dst, color) : 2;
      endColor = Math.max(endColor, adjColor);
    }
    
    return color[src] = endColor;
  }
  
  /* 
   * max DAG between src and dst (dst excluded) 
   */
  static boolean[] maxDAG(Block[] cfg, int src, int dst) {
    int[] color = new int[cfg.length];
    Arrays.fill(color, 0);
    maxDAG(cfg, src, dst, color);
    
    boolean[] res = new boolean[cfg.length];
    for (int i = 0; i < cfg.length; ++i)
      res[i] = color[i] > 1 ? true : false;
      
    return res;
  }

static boolean[] canReachDst(List<Block> cfg, int dst, Set<Integer> noDst) {

     int n = cfg.size();
     boolean[] blocked = new boolean[n];
     if (noDst != null) {
       for (Integer v : noDst) {
         if (v != null && v >= 0 && v < n) blocked[v] = true;
       }
     }
     if (dst >= 0 && dst < n) blocked[dst] = false;

     ArrayList<ArrayList<Integer>> preds = new ArrayList<>(n);
     for (int i = 0; i < n; i++) preds.add(new ArrayList<>());
     for (int u = 0; u < n; u++) {
       Block b = cfg.get(u);
       if (b == null) continue;
       for (Jump e : b.jumps) {
         int v = e.dst;
         if (v < 0 || v >= n) continue;
         preds.get(v).add(u);
       }
     }

     boolean[] can = new boolean[n];
     java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
     if (dst >= 0) {
       if (dst >= 0 && dst < n) {
         can[dst] = true;
         q.add(dst);
       }
     } else {
       for (int i = 0; i < n; i++) {
         Block b = cfg.get(i);
         if (b == null) continue;
         if (!blocked[i] && (b.jumps == null || b.jumps.isEmpty())) {
           can[i] = true;
           q.add(i);
         }
       }
     }

     while (!q.isEmpty()) {
       int v = q.removeFirst();
       for (int u : preds.get(v)) {
         if (u < 0 || u >= n) continue;
         if (blocked[u]) continue;
         if (!can[u]) {
           can[u] = true;
           q.add(u);
         }
       }
     }

     for (int i = 0; i < n; i++) if (blocked[i]) can[i] = false;
     if (dst >= 0 && dst < n) can[dst] = true;
     return can;
   }

static void topologicallySortedMaxDAG(
    List<Block> cfg, int src, int dst, Set<Integer> noDst,
    boolean[] canReach, int[] color, LinkedList<Integer> sorting) {

  if (src < 0 || src >= cfg.size()) return;
  if (color[src] != 0) return;
  if (dst >= 0 && src != dst && !canReach[src]) return;

  color[src] = 1; // visiting
  int endColor = 0;

  // C) 根治：src==dst(lasso总结)时，如果存在“非平凡路径”(经由其它节点也能到dst)，
  // 则跳过 v==dst 的直接回边，避免 0-step 空转摘要
  boolean skipDirectToDst = false;
  List<Jump> jumps = cfg.get(src).jumps;
  if (src == dst && jumps != null) {
    for (Jump e2 : jumps) {
      int w = e2.dst;
      if (w == dst) continue;
      if (w < 0 || w >= cfg.size()) continue;
      if (noDst != null && noDst.contains(w)) continue;
      if (!canReach[w]) continue;
      skipDirectToDst = true;
      break;
    }
  }

  if (jumps != null) {
    for (Jump edge : jumps) {
      int v = edge.dst;

      if (v == dst) {
        if (skipDirectToDst) continue;  // 关键：有非平凡路径时忽略 0-step 直接回到 dst
        endColor = 2;
        continue;
      }

      if (v < 0 || v >= cfg.size()) continue;
      if (noDst != null && noDst.contains(v)) continue;
      if (!canReach[v]) continue;

      if (color[v] == 0) {
        topologicallySortedMaxDAG(cfg, v, dst, noDst, canReach, color, sorting);
      }

      // 只要存在 successor 可达 dst，就认为 src 可达 dst
      endColor = 2;
    }
  }

  if ((cfg.get(src).jumps == null || cfg.get(src).jumps.isEmpty()) && dst < 0) endColor = 2;

  if (endColor == 2) sorting.addFirst(src);
  color[src] = endColor;
}




static List<Integer> topologicallySortedMaxDAG(List<Block> cfg, int src, int dst, Set<Integer> noDst) {
     int n = cfg.size();
     int[] color = new int[n];
     Arrays.fill(color, 0);
     boolean[] canReach = canReachDst(cfg, dst, noDst);
     LinkedList<Integer> sorting = new LinkedList<>();
     topologicallySortedMaxDAG(cfg, src, dst, noDst, canReach, color, sorting);
     assert sorting.stream().distinct().count() == sorting.size();
     return sorting;
   }
  
  // =========================================================================
  // Printer (debug stuff)
  // =========================================================================

  static String insnToStr(Textifier textifier, TraceMethodVisitor traceMethodVisitor, AbstractInsnNode insn, ClassReader reader) {
    textifier.getText().clear();
    insn.accept(traceMethodVisitor);
    StringWriter sw = new StringWriter();
    textifier.print(new PrintWriter(sw));
    if (insn instanceof LabelNode) {
      LabelNode l = (LabelNode) insn;
      sw.append(": " + reader.getOffset(l.getLabel()));
    }
    String str = sw.toString();
    return str.replaceFirst("\n", "");
  }

  static String printEncoder(MethodNode methodNode, SSAEncoder encoder, ClassReader reader) {
    InsnList instructions = encoder.instructions;
    
    Textifier textifier = new Textifier(Opcodes.ASM8) {
      Map<Label, Integer> labelToIndex = IntStream.range(0, instructions.size()).boxed()
          .filter(i -> instructions.get(i).getType() == AbstractInsnNode.LABEL).collect(Collectors
              .toMap(i -> ((LabelNode) instructions.get(i)).getLabel(), Function.identity()));
      
      @Override
      protected void appendLabel(Label l) {
        if (labelNames == null)
          labelNames = new HashMap<>();
        String name = labelNames.get(l);
        if (name == null) {
          name = "L" + labelToIndex.get(l);
          labelNames.put(l, name);
        }
        super.appendLabel(l);
      }
    };
    TraceMethodVisitor traceMethodVisitor = new TraceMethodVisitor(textifier);
    methodNode.accept(traceMethodVisitor);
    
    Frame<Value>[] frames = encoder.getFrames();
    SSAInterpreter interpreter = encoder.interpreter;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < instructions.size(); ++i) {
      sb.append(i);
      String is = insnToStr(textifier, traceMethodVisitor, instructions.get(i), reader);
      sb.append(is);
      sb.append(" ".repeat(Math.max(30 - sb.length(), 1)));
      sb.append(frames[i]);
      sb.append(' ');
      if (interpreter.assignmentSymbol[i] != null) {
        sb.append(interpreter.assignmentSymbol[i] + "=" + interpreter.assignmentExpr[i] + " ");
      }
      if (interpreter.jumpCondition[i] != null) {
        sb.append(interpreter.jumpSymbol[i] + "=" + interpreter.jumpCondition[i]);
      }
      if (interpreter.merges[i] != null)
        interpreter.merges[i].forEach((k, v) -> sb.append(k + "=" + v + " "));
//      if (interpreter.callArgs[i] != null)
//        sb.append(Arrays.toString(interpreter.callArgs[i]));
//      if (interpreter.callResult[i] != null)
//        sb.append(" -> " + interpreter.callResult[i]);
      sb.append("\n");
    }
    return sb.toString();
  }

  // =========================================================================
  // Main (dirty code down here)
  // =========================================================================

  static ArithExpr linearCombination(Context ctx, List<LocalVariableNode> vars, Iterable<String> args, 
      Iterable<Integer> coefficients, Value[] frame) {
    ArithExpr expr = ctx.mkInt(0);
    Iterator<Integer> i = coefficients.iterator();
    Iterator<String> j = args.iterator();
    assert i.hasNext();
    while (j.hasNext()) {
      assert j.hasNext();
      Integer coeff = i.next();
      String arg = j.next();
      if (coeff != 0) {
        IntExpr var = getField(ctx, vars, frame, arg.split("\\."));
        expr = ctx.mkAdd(expr, ctx.mkMul(ctx.mkInt(coeff), var));
      }
    }
    expr = ctx.mkAdd(expr, ctx.mkInt(i.next()));
    
    assert !i.hasNext();
    
    return (ArithExpr) expr.simplify();
  }
  
  static ArithExpr relu(Context ctx, ArithExpr arg) {
    return (ArithExpr) ctx.mkITE(ctx.mkGe(arg, ctx.mkInt(0)), arg, ctx.mkInt(0));
  }
  
  static ArithExpr weightedSumOfRelus(Context ctx, List<LocalVariableNode> vars, Iterable<String> args, 
      List<Integer> out,
      List<List<Integer>> hidden, Value[] frame) {
    assert out.size() == hidden.size();
    
    ArithExpr res = ctx.mkInt(0);
    Iterator<Integer> out_i = out.iterator();
    for (List<Integer> w : hidden) {
      ArithExpr summand = relu(ctx, linearCombination(ctx, vars, args, w, frame));
      res = ctx.mkAdd(res, ctx.mkMul(ctx.mkInt(out_i.next()), summand));
    }
    return res;
  }

  static class CallNode {
    boolean open = true;
    SSAEncoder procedure;
    List<String> trace;
    int offset;
    
    public CallNode(List<String> trace) {
      this.trace = trace;
    }
    
    void close(SSAEncoder procedure) {
      open = false;
      this.procedure = procedure;
    }
  }
  
  static Map<MethodInsnNode, CallNode> calls = new HashMap<>();
  
  public static Boolean[] check(String className, String methodName, 
      Integer offset, List<String> args, List<Integer> coeffs) throws Exception {
    return check(ClassLoader.getSystemClassLoader(), className, methodName, offset, args, coeffs);
  }
  
  static Value merge(Context ctx, List<Value> values, String prefix, String postfix) {

    if (values.get(0) instanceof Expr) {
      assert values.stream().allMatch(v -> v instanceof Expr);
      if (values.stream().allMatch(v -> values.get(0).equals(v)))
        return values.get(0);
      else 
        return mkFreshModel(ctx, "I", prefix, postfix, -1);
    } else if (values.get(0) instanceof Object) {
      Object result = new Object(-1, ((Object)values.get(0)).type);
      for (String key : ((Object)values.get(0)).fields.keySet()) {
        List<Value> next = new LinkedList<>();
        for (Value val : values) 
          next.add(((Object)val).fields.get(key));
        // parent and name missing
        result.fields.put(key, merge(ctx, next, prefix + "." + key, postfix));
      }
      return result;
    } else if (values.get(0) instanceof Array) {
//      List<Value> next = new LinkedList<>();
//      for (Value val : values) 
//        next.add(((Array)val).length);
//      return new Array(-1, (Expr)merge(ctx, next, prefix + ".length"));
      assert false;
      return null;
    } else if (values.stream().anyMatch(v -> v instanceof Any)) {
      return new Any(-1);
    } else if (values.get(0) instanceof None) {
      assert values.stream().allMatch(v -> v instanceof None);
      return new None(-1);
    } else {
      assert false;
      return null;
    }
  }
  
  static Value[] mkSummaryVars(Context ctx, List<Value[]> values, String prefix) {
    Value[] result = new Value[values.get(0).length];
    for (int i = 0; i < result.length; i++) {
      LinkedList<Value> valuesi = new LinkedList<>();
      for (Value[] v : values)
        valuesi.add(v[i]);
      result[i] = merge(ctx, valuesi, prefix + i, "");
    }
    return result;
  }
  
  static IntExpr getField(Context ctx, Object val, String[] path, int depth) {
  assert depth < path.length;
  Value child = val.fields.get(path[depth]);

  // missing field / unknown -> create an unconstrained int symbol
  if (child == null || child instanceof Any || child instanceof None) {
    String nm = String.join("_", java.util.Arrays.copyOfRange(path, 0, depth + 1))
               + "!ANY" + (child == null ? "N" : child.creationIndex);
    Expr e = new Expr(child == null ? -1 : child.creationIndex, ctx.mkIntConst(nm));
    val.fields.put(path[depth], e);
    return e.expr;
  }

  if (child instanceof Object) {
    return getField(ctx, (Object) child, path, depth + 1);
  } else {
    assert depth == path.length - 1;
    if (!(child instanceof Expr)) {
      // fallback: treat as unknown int
      String nm = String.join("_", java.util.Arrays.copyOfRange(path, 0, depth + 1)) + "!ANYX";
      Expr e = new Expr(-1, ctx.mkIntConst(nm));
      val.fields.put(path[depth], e);
      return e.expr;
    }
    return ((Expr) child).expr;
  }
}

static IntExpr getField(Context ctx, List<LocalVariableNode> vars, Value[] frame, String[] path) {
  LocalVariableNode var = vars.stream().filter(v -> v.name.equals(path[0])).findFirst().orElseThrow();
  Value v = frame[var.index];

  if (v == null || v instanceof Any || v instanceof None) {
    String nm = path[0] + "!ANY" + (v == null ? "N" : v.creationIndex) + "_" + var.index;
    Expr e = new Expr(v == null ? -1 : v.creationIndex, ctx.mkIntConst(nm));
    frame[var.index] = e;
    return e.expr;
  }

  if (v instanceof Object) {
    return getField(ctx, (Object) v, path, 1);
  } else {
    assert path.length == 1;
    if (!(v instanceof Expr)) {
      String nm = path[0] + "!ANYX_" + var.index;
      Expr e = new Expr(-1, ctx.mkIntConst(nm));
      frame[var.index] = e;
      return e.expr;
    }
    return ((Expr) v).expr;
  }
}

  static LinkedList<BoolExpr>[] minedStableInitInvar(
    Context ctx, Procedure procedure, int head, Block loop, Value[] summaryVars) {

  LinkedList<BoolExpr> invBeforeConjs = new LinkedList<>();
  LinkedList<BoolExpr> notInvAfter = new LinkedList<>();

  int n = Math.min(loop.input.length, summaryVars.length);

  // 取所有回边（dst=head）
  List<Jump> backs = loop.jumps.stream().filter(j -> j.dst == head).collect(Collectors.toList());
  if (backs.isEmpty()) return new LinkedList[]{invBeforeConjs, notInvAfter};

  // 判断哪些位置是“稳定变量”：所有回边上 output[i] 都等于 input[i]
  boolean[] stable = new boolean[n];
  Arrays.fill(stable, true);

  for (int i = 0; i < n; i++) {
    if (!(loop.input[i] instanceof Expr) || !(summaryVars[i] instanceof Expr)) {
      stable[i] = false;
      continue;
    }
    com.microsoft.z3.Expr in = ((Expr) loop.input[i]).expr;
    for (Jump j : backs) {
      if (!(j.output[i] instanceof Expr)) { // None/Any 都视作不稳定
        stable[i] = false;
        break;
      }
      com.microsoft.z3.Expr out = ((Expr) j.output[i]).expr;
      if (!out.equals(in)) {
        stable[i] = false;
        break;
      }
    }
  }

  // 收集稳定的 int 变量
  ArrayList<IntExpr> stableInts = new ArrayList<>();
  ArrayList<Integer> stableIdx = new ArrayList<>();
  for (int i = 0; i < n; i++) {
    if (!stable[i]) continue;
    com.microsoft.z3.Expr e = ((Expr) loop.input[i]).expr;
    if (e.isInt()) {
      stableInts.add((IntExpr) e);
      stableIdx.add(i);
    }
  }
  if (stableInts.isEmpty()) return new LinkedList[]{invBeforeConjs, notInvAfter};

  // 构造“入口到 head”的约束：把 head 的变量直接用 loop.input 表示（非常关键）
  Block tail = summarize(ctx, procedure.controlFlow, 0, head, Collections.emptySet());
  Solver reach = ctx.mkSolver();
  encodeAndSummarise(ctx, reach, tail, head, loop.input);

  // 收集一些候选常数（从 tail 里抓 + 小常数兜底）
  HashSet<Integer> consts = new HashSet<>();
  consts.add(-1); consts.add(0); consts.add(1); consts.add(2);

  Stack<com.microsoft.z3.Expr> st = new Stack<>();
  st.addAll(tail.statements);
  for (Jump j : tail.jumps) st.push(j.condition);
  while (!st.isEmpty()) {
    com.microsoft.z3.Expr ex = st.pop();
    if (ex == null) continue;
    if (ex.isIntNum()) {
      consts.add(((com.microsoft.z3.IntNum) ex).getInt());
    }
    for (com.microsoft.z3.Expr a : ex.getArgs()) st.push(a);
  }

  ArrayList<Integer> K = new ArrayList<>(consts);
  Collections.sort(K);

  // 生成简单 guard：True，以及 u>=k / u<k / u==k（u 为稳定 int 变量）
  ArrayList<BoolExpr> guards = new ArrayList<>();
  guards.add(ctx.mkTrue());
  for (IntExpr u : stableInts) {
    for (int k : K) {
      guards.add(ctx.mkGe(u, ctx.mkInt(k)));
      guards.add(ctx.mkLt(u, ctx.mkInt(k)));
      guards.add(ctx.mkEq(u, ctx.mkInt(k)));
    }
  }

  // 用 Z3 挖蕴含：reach ∧ guard ⊨ (v == c)
  final int MAX_CHECKS = 600; // 控制开销，避免影响整体性能
  int checks = 0;

  for (BoolExpr g : guards) {
    // 先判断 guard 在 reach 下是否可达（不可达就跳过）
    reach.push();
    reach.add(g);
    Status sg = reach.check();
    reach.pop();
    if (sg != Status.SATISFIABLE) continue;

    for (IntExpr v : stableInts) {
      for (int c : K) {
        if (++checks > MAX_CHECKS) break;

        reach.push();
        reach.add(g);
        reach.add(ctx.mkNot(ctx.mkEq(v, ctx.mkInt(c))));
        Status s = reach.check();
        reach.pop();

        if (s == Status.UNSATISFIABLE) {
          invBeforeConjs.add(ctx.mkImplies(g, ctx.mkEq(v, ctx.mkInt(c))));
        }
      }
      if (checks > MAX_CHECKS) break;
    }
    if (checks > MAX_CHECKS) break;
  }

  if (invBeforeConjs.isEmpty()) return new LinkedList[]{invBeforeConjs, notInvAfter};

  // 构造 ¬Inv(after)：把稳定变量用 after(=summaryVars 对应位置) 替换
  com.microsoft.z3.Expr[] bef = new com.microsoft.z3.Expr[stableIdx.size()];
  com.microsoft.z3.Expr[] aft = new com.microsoft.z3.Expr[stableIdx.size()];
  for (int p = 0; p < stableIdx.size(); p++) {
    int i = stableIdx.get(p);
    bef[p] = ((Expr) loop.input[i]).expr;
    aft[p] = ((Expr) summaryVars[i]).expr;
  }

  BoolExpr invBefore = ctx.mkAnd(invBeforeConjs.toArray(new BoolExpr[0]));
  BoolExpr invAfter  = (BoolExpr) invBefore.substitute(bef, aft);
  notInvAfter.add(ctx.mkNot(invAfter));

  return new LinkedList[]{invBeforeConjs, notInvAfter};
}

  
  static boolean containsAny(com.microsoft.z3.Expr root, Set<com.microsoft.z3.Expr> needles) {
  if (needles.isEmpty()) return false;
  Stack<com.microsoft.z3.Expr> st = new Stack<>();
  st.push(root);
  while (!st.isEmpty()) {
    com.microsoft.z3.Expr e = st.pop();
    if (needles.contains(e)) return true;
    for (com.microsoft.z3.Expr a : e.getArgs()) st.push(a);
  }
  return false;
}

static LinkedList<BoolExpr>[] preconditionAsInvar(Context ctx, Procedure procedure, int head,
    Block loop, Value[] summaryVars) {

  LinkedList<BoolExpr> invBeforeList = new LinkedList<>();
  LinkedList<BoolExpr> notInvAfterList = new LinkedList<>();

  // 只在 loop.input 都是 Expr 时启用（与你原逻辑一致）
  if (!Arrays.stream(loop.input).allMatch(v -> (v instanceof Expr))) {
    return new LinkedList[]{invBeforeList, notInvAfterList};
  }

  // -------- 1) 计算 stable 位置：在所有回边上都保持不变 --------
  int n = Math.min(loop.input.length, summaryVars.length);

  List<Jump> backs = loop.jumps.stream().filter(j -> j.dst == head).collect(Collectors.toList());
  if (backs.isEmpty()) return new LinkedList[]{invBeforeList, notInvAfterList};

  boolean[] stable = new boolean[n];
  Arrays.fill(stable, true);

  for (int i = 0; i < n; i++) {
    if (!(loop.input[i] instanceof Expr) || !(summaryVars[i] instanceof Expr)) {
      stable[i] = false;
      continue;
    }
    com.microsoft.z3.Expr in = ((Expr) loop.input[i]).expr;
    for (Jump j : backs) {
      if (!(j.output[i] instanceof Expr)) { stable[i] = false; break; }
      com.microsoft.z3.Expr out = ((Expr) j.output[i]).expr;
      if (!out.equals(in)) { stable[i] = false; break; }
    }
  }

  int stableCnt = 0;
  for (boolean b : stable) if (b) stableCnt++;
  if (stableCnt == 0) return new LinkedList[]{invBeforeList, notInvAfterList};

  // unstable head vars（用于过滤 tail.statements，避免把 x 这类会变的变量绑死）
  Set<com.microsoft.z3.Expr> unstableHead = new HashSet<>();
  for (int i = 0; i < n; i++) {
    if (!stable[i] && loop.input[i] instanceof Expr) {
      unstableHead.add(((Expr) loop.input[i]).expr);
    }
  }

  // -------- 2) 入口到 head 的 tail summary，取所有入边 --------
  Block tail = summarize(ctx, procedure.controlFlow, 0, head, Collections.emptySet());
  List<Jump> inJumps = tail.jumps.stream().filter(j -> j.dst == head).collect(Collectors.toList());
  if (inJumps.isEmpty()) return new LinkedList[]{invBeforeList, notInvAfterList};

  // -------- 3) 组装 Inv(before) 的 conjuncts --------
  ArrayList<BoolExpr> conjs = new ArrayList<>();

  // (a) 只保留 tail.statements 中“不提 unstable head vars”的语句
  for (BoolExpr s : tail.statements) {
    if (!containsAny((com.microsoft.z3.Expr) s, unstableHead)) {
      conjs.add(s);
    }
  }

  // (b) 加入可达性：OR_{in} cond
  BoolExpr reach = ctx.mkFalse();
  for (Jump j : inJumps) reach = ctx.mkOr(reach, j.condition);
  conjs.add((BoolExpr) reach.simplify());

  // (c) 对每条入边，加入 stable 变量的“定义关系”：cond -> (headStable == outStable)
  Value[] dstStable = new Value[stableCnt];
  int p = 0;
  for (int i = 0; i < n; i++) if (stable[i]) dstStable[p++] = loop.input[i];

  for (Jump j : inJumps) {
    Value[] srcStable = new Value[stableCnt];
    p = 0;
    for (int i = 0; i < n; i++) if (stable[i]) srcStable[p++] = j.output[i];

    for (BoolExpr eqIf : encodeEqIf(ctx, dstStable, srcStable, j.condition)) {
      // 同样过滤：避免把不稳定 head var 混进来
      if (!containsAny((com.microsoft.z3.Expr) eqIf, unstableHead)) {
        conjs.add(eqIf);
      }
    }
  }

  BoolExpr invBefore = ctx.mkAnd(conjs.toArray(new BoolExpr[0]));
  invBefore = (BoolExpr) invBefore.simplify();

  // -------- 4) 生成 ¬Inv(after)：对 stable 变量做 before->after 替换 --------
  com.microsoft.z3.Expr[] bef = new com.microsoft.z3.Expr[stableCnt];
  com.microsoft.z3.Expr[] aft = new com.microsoft.z3.Expr[stableCnt];
  p = 0;
  for (int i = 0; i < n; i++) {
    if (stable[i]) {
      bef[p] = ((Expr) loop.input[i]).expr;
      aft[p] = ((Expr) summaryVars[i]).expr;
      p++;
    }
  }
  BoolExpr invAfter = (BoolExpr) invBefore.substitute(bef, aft);
  BoolExpr notInvAfter = ctx.mkNot(invAfter);

  invBeforeList.add(invBefore);
  notInvAfterList.add(notInvAfter);

  return new LinkedList[]{invBeforeList, notInvAfterList};
}


  
  static LinkedList<LinkedList<BoolExpr>[]> builtInInvars(Context ctx, Value[] input, Value[] output) {
    assert input.length == output.length;
    LinkedList<LinkedList<BoolExpr>[]> res = new LinkedList<>();
    
    //x > 0
    for (int i = 0; i < input.length; ++i){
      LinkedList<BoolExpr> invarBefore = new LinkedList<BoolExpr>();
      LinkedList<BoolExpr> invarAfter = new LinkedList<BoolExpr>(); 
      
      if (input[i] instanceof Expr && output[i] instanceof Expr) {
        invarBefore.add(ctx.mkGt(((Expr)input[i]).expr, ctx.mkInt(0)));
        invarAfter.add(ctx.mkLe(((Expr)output[i]).expr, ctx.mkInt(0)));
      }
      
      res.add(new LinkedList[]{invarBefore, invarAfter});
    }
    
    // x >= 1
    for (int i = 0; i < input.length; ++i){
      LinkedList<BoolExpr> invarBefore = new LinkedList<BoolExpr>();
      LinkedList<BoolExpr> invarAfter = new LinkedList<BoolExpr>();

      if (input[i] instanceof Expr && output[i] instanceof Expr) {
        invarBefore.add(ctx.mkGe(((Expr)input[i]).expr, ctx.mkInt(1)));
        // not(x >= 1)  等价于  x < 1
        invarAfter.add(ctx.mkLt(((Expr)output[i]).expr, ctx.mkInt(1)));
      }

      res.add(new LinkedList[]{invarBefore, invarAfter});
    }

    // x >= 2  （LogMult 里 y=2 初始化会让该模板通过“前置条件筛选”，并且对 y=y*y 归纳保持）
    for (int i = 0; i < input.length; ++i){
      LinkedList<BoolExpr> invarBefore = new LinkedList<BoolExpr>();
      LinkedList<BoolExpr> invarAfter = new LinkedList<BoolExpr>();

      if (input[i] instanceof Expr && output[i] instanceof Expr) {
        invarBefore.add(ctx.mkGe(((Expr)input[i]).expr, ctx.mkInt(2)));
        // not(x >= 2)  等价于  x < 2
        invarAfter.add(ctx.mkLt(((Expr)output[i]).expr, ctx.mkInt(2)));
      }

      res.add(new LinkedList[]{invarBefore, invarAfter});
    }
    
    return res;
  }
  
  class Obligation {
    int head, tail;
    List<String> args;
    
    List<List<Integer>> ranking;
    List<List<Integer>> unaffecting;
  }
  
  private static void encodeAndSummarise(Context ctx, Solver solver, Block block, int dst, Value[] summaryVars) {
    for (BoolExpr stmt : block.statements)
      solver.add(stmt);
    
    LinkedList<BoolExpr> exitDisjuncts = new LinkedList<>();
    for (Jump out : block.jumps) {
      if (out.dst == dst) {
        exitDisjuncts.add(out.condition);
      } 
    }
    
    for (Jump out : block.jumps) {
      if (out.dst == dst) {
        for (BoolExpr eqIf : encodeEqIf(ctx, summaryVars, out.output, out.condition))
          solver.add(eqIf);
      }
    }
    solver.add(ctx.mkOr(exitDisjuncts.toArray(BoolExpr[]::new)));

    for (Throw thr : block.thrown) {
      solver.add(ctx.mkNot(thr.condition));
    }
  }




// =========================
// Step3 helpers
// =========================
private static boolean envOn(String key) {
  String v = System.getenv(key);
  if (v == null) return false;
  v = v.trim();
  return v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes");
}

private static Value freshLike(Context ctx, Value tpl, String name) {
  if (tpl instanceof Expr) {
    // RankChecker.Expr (int)
    return new Expr(-1, ctx.mkIntConst(name));
  }
  if (tpl instanceof Object) {
    Object o = (Object) tpl;
    Object r = new Object(-1, o.type);
    for (Map.Entry<String, Value> e : o.fields.entrySet()) {
      r.fields.put(e.getKey(), freshLike(ctx, e.getValue(), name + "." + e.getKey()));
    }
    return r;
  }
  if (tpl instanceof Any)  return new Any(-1);
  if (tpl instanceof None) return new None(-1);
  if (tpl instanceof Array) {
    // 你当前实现里 Array 也基本没建模（mkFreshModel 直接 assert false）
    throw new IllegalStateException("Array is not supported in freshLike");
  }
  throw new IllegalStateException("Unknown Value kind: " + tpl);
}

private static Value[] freshFrameLike(Context ctx, Value[] tpl, String prefix) {
  Value[] r = new Value[tpl.length];
  for (int i = 0; i < tpl.length; i++) {
    r[i] = freshLike(ctx, tpl[i], prefix + "_" + i);
  }
  return r;
}

private static void fillCexFromFrame(
    Context ctx,
    Solver solver,
    List<LocalVariableNode> vars,
    Value[] frame,
    List<String> args,
    Map<String, Integer> cex
) {
  if (ctx == null || solver == null || vars == null || frame == null || args == null || cex == null) return;
  Model m = solver.getModel();
  if (m == null) return;

  for (String a : args) {
    if (a == null) continue;
    if (cex.containsKey(a)) continue;
    try {
      IntExpr v = getField(ctx, vars, frame, a.split("\\."));
      com.microsoft.z3.Expr<?> ev = m.evaluate(v, true);
      if (ev != null && ev.isIntNum()) {
        cex.put(a, ((com.microsoft.z3.IntNum) ev).getInt());
      }
    } catch (Throwable ignore) {
      // 忽略不可取值/非 int/None/Any 等
    }
  }
}

// =========================
// SCC (Tarjan) for CFG blocks
// =========================
private static final class SCCInfo {
  final int[] comp;          // node -> compId
  final List<int[]> comps;   // compId -> nodes[]
  SCCInfo(int[] comp, List<int[]> comps) { this.comp = comp; this.comps = comps; }
}

private static SCCInfo tarjanScc(List<Block> cfg) {
  int n = cfg.size();
  int[] idx = new int[n];
  int[] low = new int[n];
  int[] comp = new int[n];
  Arrays.fill(idx, -1);
  Arrays.fill(comp, -1);

  int[] stack = new int[n];
  boolean[] on = new boolean[n];
  int[] sp = new int[]{0};
  int[] time = new int[]{0};

  List<int[]> comps = new ArrayList<>();

  java.util.function.IntConsumer dfs = new java.util.function.IntConsumer() {
    @Override public void accept(int v) {
      idx[v] = low[v] = time[0]++;
      stack[sp[0]++] = v;
      on[v] = true;

      Block b = cfg.get(v);
      if (b != null) {
        for (Jump e : b.jumps) {
          int w = e.dst;
          if (w < 0 || w >= n) continue;
          if (cfg.get(w) == null) continue;
          if (idx[w] == -1) {
            this.accept(w);
            low[v] = Math.min(low[v], low[w]);
          } else if (on[w]) {
            low[v] = Math.min(low[v], idx[w]);
          }
        }
      }

      if (low[v] == idx[v]) {
        int id = comps.size();
        ArrayList<Integer> nodes = new ArrayList<>();
        while (true) {
          int w = stack[--sp[0]];
          on[w] = false;
          comp[w] = id;
          nodes.add(w);
          if (w == v) break;
        }
        comps.add(nodes.stream().mapToInt(x -> x).toArray());
      }
    }
  };

  for (int i = 0; i < n; i++) {
    if (cfg.get(i) == null) continue;
    if (idx[i] == -1) dfs.accept(i);
  }

  return new SCCInfo(comp, comps);
}

// =========================
// Step3 main: checkVecBySccStep
// =========================
private static Boolean[] checkVecBySccStep(
    Context ctx, List<LocalVariableNode> vars, Procedure procedure, List<Integer> heads,
    List<String> args,
    List<List<Integer>> out, List<List<List<Integer>>> hidden,
    boolean areRelus, int delta,
    Map<String, Integer> cex) throws Exception {

  int rankdim = out.size();
  if (rankdim <= 0) throw new IllegalArgumentException("rankdim must be >= 1");
  if (hidden.size() != rankdim) throw new IllegalArgumentException("out/hidden rankdim mismatch");

  // 只对包含 loop head 的 SCC 做检查
  SCCInfo scc = tarjanScc(procedure.controlFlow);
  Set<Integer> targetCompIds = new java.util.LinkedHashSet<>();
  for (int h : heads) {
    if (h < 0 || h >= scc.comp.length) continue;
    int cid = scc.comp[h];
    if (cid >= 0) targetCompIds.add(cid);
  }

  boolean allOk = true;
  boolean usedInvar = false;

  for (int cid : targetCompIds) {
    int[] nodes = scc.comps.get(cid);
    if (nodes == null || nodes.length == 0) continue;

    // 判断是否真的存在 SCC 内部边（否则不是循环）
    boolean hasInternalEdge = false;
    boolean[] in = new boolean[procedure.controlFlow.size()];
    for (int v : nodes) in[v] = true;
    for (int v : nodes) {
      Block b = procedure.controlFlow.get(v);
      if (b == null) continue;
      for (Jump e : b.jumps) {
        if (e.dst >= 0 && e.dst < in.length && in[e.dst]) { hasInternalEdge = true; break; }
      }
      if (hasInternalEdge) break;
    }
    if (!hasInternalEdge) continue;

    Solver solver = ctx.mkSolver();

    // 选一个模板 frame（同一 procedure 的 block.input 形状应一致）
    Value[] tpl = procedure.controlFlow.get(nodes[0]).input;
    Value[] pre  = freshFrameLike(ctx, tpl, "scc" + cid + "_pre");
    Value[] post = freshFrameLike(ctx, tpl, "scc" + cid + "_post");

    IntExpr pc = ctx.mkIntConst("scc" + cid + "_pc");

    // pc ∈ SCC
    BoolExpr pcDom = ctx.mkFalse();
    for (int v : nodes) pcDom = ctx.mkOr(pcDom, ctx.mkEq(pc, ctx.mkInt(v)));
    solver.add(pcDom);

    // 编码 SCC 内“一步”语义：选择一个 block 执行 + 选择一条 SCC 内边
    BoolExpr stepTaken = ctx.mkFalse();

    for (int u : nodes) {
      Block bu = procedure.controlFlow.get(u);
      if (bu == null) continue;

      BoolExpr atU = ctx.mkEq(pc, ctx.mkInt(u));

      // pre 绑定到当前 block 的输入态
      for (BoolExpr eq : encodeEqIf(ctx, bu.input, pre, atU)) solver.add(eq);

      // 只在 atU 时启用该 block 的语句
      for (BoolExpr st : bu.statements) {
        solver.add(ctx.mkImplies(atU, st));
      }

      // 不允许抛异常路径（与 encodeAndSummarise 保持一致）
      for (Throw thr : bu.thrown) {
        solver.add(ctx.mkImplies(atU, ctx.mkNot(thr.condition)));
      }

      for (Jump e : bu.jumps) {
        int v = e.dst;
        if (v < 0 || v >= in.length || !in[v]) continue; // 只看 SCC 内边

        BoolExpr take = ctx.mkAnd(atU, e.condition);

        // post 绑定到该边的输出态（下一步状态）
        for (BoolExpr eq : encodeEqIf(ctx, post, e.output, take)) solver.add(eq);

        stepTaken = ctx.mkOr(stepTaken, take);
      }
    }

    solver.add(stepTaken); // 必须真的走了一步

    // 计算 rank(pre), rank(post)
    IntExpr[] before = new IntExpr[rankdim];
    IntExpr[] after  = new IntExpr[rankdim];

    for (int p = 0; p < rankdim; p++) {
      IntExpr b2 = (IntExpr) ctx.mkFreshConst("scc" + cid + "_b2_p" + p, ctx.mkIntSort());
      IntExpr a2 = (IntExpr) ctx.mkFreshConst("scc" + cid + "_a2_p" + p, ctx.mkIntSort());
      IntExpr b  = (IntExpr) ctx.mkFreshConst("scc" + cid + "_b_p"  + p, ctx.mkIntSort());
      IntExpr a  = (IntExpr) ctx.mkFreshConst("scc" + cid + "_a_p"  + p, ctx.mkIntSort());

      List<Integer> outW = out.get(p);
      List<List<Integer>> hidW = hidden.get(p);

      if (areRelus) {
        solver.add(ctx.mkEq(b2, weightedSumOfRelus(ctx, vars, args, outW, hidW, pre)));
        solver.add(ctx.mkEq(a2, weightedSumOfRelus(ctx, vars, args, outW, hidW, post)));

        // clamp to ReLU>=0
        solver.add(ctx.mkImplies(ctx.mkGe(b2, ctx.mkInt(0)), ctx.mkEq(b, b2)));
        solver.add(ctx.mkImplies(ctx.mkLt(b2, ctx.mkInt(0)), ctx.mkEq(b, ctx.mkInt(0))));
        solver.add(ctx.mkImplies(ctx.mkGe(a2, ctx.mkInt(0)), ctx.mkEq(a, a2)));
        solver.add(ctx.mkImplies(ctx.mkLt(a2, ctx.mkInt(0)), ctx.mkEq(a, ctx.mkInt(0))));
      } else {
        throw new IllegalArgumentException("SCC-step currently expects ReLU ranking (areRelus=true)");
      }

      before[p] = b;
      after[p]  = a;
    }

    // lexDec（沿用你现有 checkVec 的“prefix 非增 + 当前分量严格降 delta”）
    ArrayList<BoolExpr> disj = new ArrayList<>();
    for (int p = 0; p < rankdim; p++) {
      ArrayList<BoolExpr> conj = new ArrayList<>();
      for (int q = 0; q < p; q++) conj.add(ctx.mkLe(after[q], before[q]));
      conj.add(ctx.mkLe(after[p], (ArithExpr) ctx.mkSub(before[p], ctx.mkInt(delta))));
      disj.add(ctx.mkAnd(conj.toArray(new BoolExpr[0])));
    }
    BoolExpr lexDec = (rankdim == 1) ? disj.get(0) : ctx.mkOr(disj.toArray(new BoolExpr[0]));

    // 违反：存在一步使得 not lexDec
    solver.push();
    solver.add(ctx.mkNot(lexDec));
    Status st = solver.check();

    if (st == Status.UNSATISFIABLE) {
      solver.pop();
      continue; // 该 SCC OK
    }

    if (st == Status.SATISFIABLE) {
      // 尝试用内建 invariants 收紧（通用的，不是特例）
      for (LinkedList<BoolExpr>[] inv : builtInInvars(ctx, pre, post)) {
        for (BoolExpr b : inv[0]) solver.add(b);
      }
      Status st2 = solver.check();

      if (st2 == Status.UNSATISFIABLE) {
        usedInvar = true;
        solver.pop();
        continue;
      }

      if (st2 == Status.SATISFIABLE) {
        // 真反例（或不可达导致的伪反例；但这已经比 summarize 模式少很多）
        fillCexFromFrame(ctx, solver, vars, pre, args, cex);
        allOk = false;
        solver.pop();
        break;
      }

      throw new Exception("Z3 returned UNKNOWN in SCC-step (with invariants)");
    }

    throw new Exception("Z3 returned UNKNOWN in SCC-step");
  }

  return new Boolean[]{allOk, true, usedInvar};
}




  
  private static void getCex(Context ctx, Solver solver, Procedure procedure, Block loop,
                           int head_i, int head, Set<Integer> outer, Map<String, Integer> cex) {

  // =========================
  // 0) collect assumeVars (from entry inputs)
  // =========================
  java.util.ArrayList<String> assumeVars = new java.util.ArrayList<>();
  try {
    for (Value v : procedure.controlFlow.get(0).input) {
      if (v instanceof Expr) {
        String s = ((Expr) v).expr.toString();
        String name = s.contains("!") ? s.substring(0, s.indexOf("!")) : s;
        if (name != null && !name.isEmpty() && !assumeVars.contains(name)) {
          assumeVars.add(name);
        }
      }
    }
  } catch (Exception ignored) {}

  // =========================
  // 1) parse assume string -> assume0 over var!0
  // =========================
  String raw = System.getProperty("RANKCHECKER_ASSUME");
  if (raw == null || raw.trim().isEmpty()) raw = System.getenv("RANKCHECKER_ASSUME");
  if (raw == null) raw = "";
  raw = raw.trim();

  System.out.println("[getCex] raw assume = \"" + raw + "\"");
  System.out.println("[getCex] assumeVars = " + assumeVars);

  BoolExpr assume0 = ctx.mkTrue();

  if (!raw.isEmpty() && !assumeVars.isEmpty()) {
    java.util.HashSet<String> varset = new java.util.HashSet<>(assumeVars);
    java.util.ArrayList<BoolExpr> atoms = new java.util.ArrayList<>();

    // 注意：你的 assume 里是 "&&" 连接
    String[] parts = raw.split("&&");

    java.util.regex.Pattern pat = java.util.regex.Pattern.compile(
        "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(==|!=|>=|<=|>|<)\\s*([A-Za-z_][A-Za-z0-9_]*|[-]?[0-9]+)\\s*$"
    );

    for (String part : parts) {
      String t = part.trim();
      if (t.isEmpty()) continue;

      java.util.regex.Matcher m = pat.matcher(t);
      if (!m.matches()) {
        System.out.println("[getCex] skip unparsable assume atom: " + t);
        continue;
      }

      String lhs = m.group(1);
      String op  = m.group(2);
      String rhs = m.group(3);

      if (!varset.contains(lhs)) {
        System.out.println("[getCex] skip assume lhs not in varset: " + lhs);
        continue;
      }

      ArithExpr L = ctx.mkIntConst(lhs + "!0");
      ArithExpr R;

      if (rhs.matches("[-]?[0-9]+")) {
        R = ctx.mkInt(Integer.parseInt(rhs));
      } else {
        if (!varset.contains(rhs)) {
          System.out.println("[getCex] skip assume rhs var not in varset: " + rhs);
          continue;
        }
        R = ctx.mkIntConst(rhs + "!0");
      }

      BoolExpr atom;
      switch (op) {
        case "==": atom = ctx.mkEq(L, R); break;
        case "!=": atom = ctx.mkNot(ctx.mkEq(L, R)); break;
        case ">=": atom = ctx.mkGe(L, R); break;
        case "<=": atom = ctx.mkLe(L, R); break;
        case ">":  atom = ctx.mkGt(L, R); break;
        case "<":  atom = ctx.mkLt(L, R); break;
        default:   atom = ctx.mkTrue(); break;
      }
      atoms.add(atom);
      System.out.println("[getCex] add assume atom: " + atom);
    }

    if (!atoms.isEmpty()) {
      assume0 = ctx.mkAnd(atoms.toArray(new BoolExpr[0]));
    }
  }

  System.out.println("[getCex] assume0 = " + assume0);

  // =========================
  // 2) ALWAYS re-solve under (current assertions + assume0)
  //    and DO NOT fallback to old model if UNSAT.
  // =========================
  Solver baseCexSolver = ctx.mkSolver();
  setTimeout(ctx, baseCexSolver, 10000);

  for (BoolExpr a : solver.getAssertions()) {
    baseCexSolver.add(a);
  }
  baseCexSolver.add(assume0);

  Status st0 = baseCexSolver.check();
  System.out.println("[getCex] baseCexSolver.check() = " + st0);

  if (st0 != Status.SATISFIABLE) {
    // 说明“反例”只存在于不可达域（不满足 main 的 assume）
    System.out.println("[getCex] UNSAT under assume -> treat as unreachable cex, return empty.");
    return;
  }

  Model cexModel = baseCexSolver.getModel();

  // =========================
  // 3) OPTIONAL: head!=0 pullback (仍然要求 assume0)
  //    但你这个 case head==0，所以通常不会走。
  // =========================
  if (head != 0) {
    Model model = cexModel;

    Solver cexSolver = ctx.mkSolver();
    setTimeout(ctx, cexSolver, 10000);
    cexSolver.add(assume0);

    Block tail = null;
    try {
      tail = summarize(ctx, procedure.controlFlow, 0, head, outer);
    } catch (Exception e) {
      tail = null;
    }
    if (tail == null) {
      try {
        tail = summarize(ctx, procedure.controlFlow, 0, head, java.util.Collections.emptySet());
      } catch (Exception e) {
        tail = null;
      }
    }

    if (tail != null) {
      Value[] tailOutout = null;
      for (Jump j : tail.jumps) {
        if (j.dst == head) { tailOutout = j.output; break; }
      }

      if (tailOutout != null && tailOutout.length == loop.input.length) {
        for (int i = 0; i < loop.input.length; ++i) {
          if (!(tailOutout[i] instanceof Expr)) continue;

          com.microsoft.z3.Expr v = model.evaluate(((Expr) loop.input[i]).expr, true);
          if (v != null) {
            try {
              cexSolver.add(ctx.mkEq(((Expr) tailOutout[i]).expr, (ArithExpr) v));
            } catch (Exception ignored2) {}
          }
        }
        for (BoolExpr stmt : tail.statements) cexSolver.add(stmt);

        Status stBack = cexSolver.check();
        System.out.println("[getCex] pullback check = " + stBack);
        if (stBack == Status.SATISFIABLE) {
          cexModel = cexSolver.getModel();
        }
      }
    }
  }

  // =========================
  // 4) write entry vars: prefer var!0 (aligned with assume0)
  // =========================
  for (Value arg : procedure.controlFlow.get(0).input) {
    if (!(arg instanceof Expr)) continue;

    String str = ((Expr) arg).expr.toString();
    String name = str.contains("!") ? str.substring(0, str.indexOf("!")) : str;

    com.microsoft.z3.Expr v = null;
    try {
      v = cexModel.evaluate(ctx.mkIntConst(name + "!0"), true);
    } catch (Exception ignored) { v = null; }

    if (v == null) {
      try {
        v = cexModel.evaluate(((Expr) arg).expr, true);
      } catch (Exception ignored) { v = null; }
    }

    if (v != null && v.isIntNum()) {
      int iv = ((com.microsoft.z3.IntNum) v).getInt();
      cex.put(name, iv);
      System.out.println("[getCex] cex[" + name + "] = " + iv);
    }
  }
}




  private static Boolean[] check(Context ctx, List<LocalVariableNode> vars, Procedure procedure, List<Integer> heads, 
      List<String> args, List<List<Integer>> lexicographicOut, List<List<List<Integer>>> lexicographicHidden, boolean areRelus, Map<String, Integer> cex) throws Exception {
    //assert procedure.varNames.length + 1 == rank.size();
    assert lexicographicOut.size() == heads.size();
    assert lexicographicHidden.size() == heads.size();
    
    Boolean[] result = {false, areRelus, false}; // dec, bnd, invar
    Set<Integer> outer = new HashSet<Integer>(heads);
    for (int head_i = heads.size() - 1; head_i >= 0; head_i--) {
      int head = heads.get(head_i);
      outer.remove(head);
      Set<Integer> noDstUsed = new HashSet<>(outer);
      
    Block loop;
    try {
        loop = summarize(ctx, procedure.controlFlow, head, head, noDstUsed);
    } catch (IndexOutOfBoundsException e) {
        return new Boolean[]{false, true, false};
    }

      Solver solver = ctx.mkSolver();

      LinkedList<Value[]> exitValues = new LinkedList<>();
        for (Jump out : loop.jumps) {
          if (out.dst == head) {
            exitValues.add(out.output);
          }
        }
        if (exitValues.isEmpty()) {
          System.out.println("[checkVec] head=" + head + " has no back-edge outputs; treat as TRIVIAL OK");
          result[0] = true;
          continue;
        }
        Value[] summaryVars = mkSummaryVars(ctx, exitValues, "sum" + head + "var");

      
      encodeAndSummarise(ctx, solver, loop, head, summaryVars);

      solver.add(parseAssumeAtHead(ctx, args, loop.input));

      if ("1".equals(System.getenv("RANKCHECK_ASSUME_A_PLUS2"))) {
  try {
    IntExpr aBefore = getField(ctx, vars, loop.input,  new String[]{"a"});
    IntExpr aAfter  = getField(ctx, vars, summaryVars, new String[]{"a"});
    solver.add(ctx.mkGe(aAfter, ctx.mkAdd(aBefore, ctx.mkInt(2))));
    System.out.println("[assume] added: aAfter >= aBefore + 2");
  } catch (Exception ignored) {}
}

      
      Status status;
      
      
      LinkedList<LinkedList<BoolExpr>[]> cadidateInvars = new LinkedList<>();
      cadidateInvars.add(minedStableInitInvar(ctx, procedure, head, loop, summaryVars));
      cadidateInvars.add(preconditionAsInvar(ctx, procedure, head, loop, summaryVars));
      cadidateInvars.addAll(builtInInvars(ctx, loop.input, summaryVars));
      Iterator<LinkedList<BoolExpr>[]> it = cadidateInvars.iterator();
      while (it.hasNext()) {
        LinkedList<BoolExpr>[] candidate = it.next();
        // check precondition
        {
          Block tail = summarize(ctx, procedure.controlFlow, 0, head, Collections.emptySet());
          Solver preSolver = ctx.mkSolver();
          encodeAndSummarise(ctx, preSolver, tail, head, summaryVars);
          
          assert Stream.of(preSolver.getAssertions()).reduce(ctx.mkSolver(), (s, a) -> {
            s.add(a);
            return s;
          }, (s, t) -> s).check() == Status.SATISFIABLE;
          
          for (BoolExpr stmt : candidate[1])
            preSolver.add(stmt); //already negated
          
          status = preSolver.check();
          if (status == Status.SATISFIABLE) {
            it.remove();
            continue;
          }
        }
        // check invariant
        {
          Solver preSolver = ctx.mkSolver();
          encodeAndSummarise(ctx, preSolver, loop, head, summaryVars);
          
          assert Stream.of(preSolver.getAssertions()).reduce(ctx.mkSolver(), (s, a) -> {
            s.add(a);
            return s;
          }, (s, t) -> s).check() == Status.SATISFIABLE;
          
          for (BoolExpr stmt : candidate[0])
            preSolver.add(stmt);
          
          for (BoolExpr stmt : candidate[1])
            preSolver.add(stmt);
          
          status = preSolver.check();
          if (status == Status.SATISFIABLE) {
            it.remove();
            continue;
          }
        }
      }
      
      IntExpr before = (IntExpr) ctx.mkFreshConst("before", ctx.mkIntSort());
      IntExpr after = (IntExpr) ctx.mkFreshConst("after", ctx.mkIntSort());
      IntExpr before2 = (IntExpr) ctx.mkFreshConst("before2", ctx.mkIntSort());
      IntExpr after2 = (IntExpr) ctx.mkFreshConst("after2", ctx.mkIntSort());
      
      // begin lexicgraphic check
      int lexiMax = (heads.size() == 1)
    ? (lexicographicOut.size() - 1)
    : Math.min(head_i, lexicographicOut.size() - 1);
      for (int lexi_i = 0; lexi_i <= lexiMax; lexi_i++) {
        List<Integer> outWeights = lexicographicOut.get(lexi_i);
        List<List<Integer>> hiddenWeights = lexicographicHidden.get(lexi_i);

        solver.push(); // before and after definition
        
        if (areRelus) {
          solver.add(ctx.mkEq(before2, weightedSumOfRelus(ctx, vars, args, outWeights, hiddenWeights, loop.input)));
          solver.add(ctx.mkEq(after2, weightedSumOfRelus(ctx, vars, args, outWeights, hiddenWeights, summaryVars)));
          solver.add(ctx.mkImplies(ctx.mkGe(before2, ctx.mkInt(0)), ctx.mkEq(before, before2)));
          solver.add(ctx.mkImplies(ctx.mkLt(before2, ctx.mkInt(0)), ctx.mkEq(before, ctx.mkInt(0))));
          solver.add(ctx.mkImplies(ctx.mkGe(after2, ctx.mkInt(0)), ctx.mkEq(after, after2)));
          solver.add(ctx.mkImplies(ctx.mkLt(after2, ctx.mkInt(0)), ctx.mkEq(after, ctx.mkInt(0))));
        } else {
          assert outWeights.size() == 1;
          assert outWeights.get(0) == 1;
          assert hiddenWeights.size() == 1;
          solver.add(ctx.mkEq(before, linearCombination(ctx, vars, args, hiddenWeights.get(0), loop.input)));
          solver.add(ctx.mkEq(after, linearCombination(ctx, vars, args, hiddenWeights.get(0), summaryVars)));
        }
        
        solver.push(); // decrease w/0 invar
        
        if (lexi_i == head_i) {
          solver.add(ctx.mkGe(after, before));
        } else {
          solver.add(ctx.mkGt(after, before));
        }
        
        // checking that it decreases w/o invar
        status = solver.check();
        switch (status) {
          case UNSATISFIABLE:
            // System.out.println("Cool, it decreses."); ;
            // return true;
            result[0] = true;
            break;
          case SATISFIABLE:
            // System.out.println("I am afraid that doesn't decrease.");
            result[0] = false;
            break;
          case UNKNOWN:
          default:
            throw new Exception("I couldn't check it. Sorry about that.");
        }

        if (result[0] == false) {
          // checking that it decreases with invar
          for (LinkedList<BoolExpr>[] invar : cadidateInvars) {
            for (BoolExpr stmt : invar[0])
              solver.add(stmt);
          }
          status = solver.check();
          switch (status) {
            case UNSATISFIABLE:
              // System.out.println("Cool, it decreses."); ;
              // return true;
              result[0] = true;
              result[2] = true;
              break;
            case SATISFIABLE:
              // System.out.println("I am afraid that doesn't decrease.");
              // System.out.println(solver.getModel());
            
              getCex(ctx, solver, procedure, loop, head_i, head, outer, cex);
              result[0] = false;
              return result;
            case UNKNOWN:
            default:
              throw new Exception("I couldn't check it. Sorry about that.");
          }
        }
        
        solver.pop(); // decrease w/0 invar
        
        if (areRelus) {
          result[1] = true;
        } else {
          solver.push(); // bounded with invar
          for (LinkedList<BoolExpr>[] invar : cadidateInvars) {
            for (BoolExpr stmt : invar[0])
              solver.add(stmt);
          }
          solver.add(ctx.mkLt(before, ctx.mkInt(0)));
  
          status = solver.check();
          switch (status) {
            case UNSATISFIABLE:
              // System.out.println("It's also well founded. Well ranked!");
              result[1] = true;
              break;
            case SATISFIABLE:
              // System.out.println("I am afraid that's not well founded, though.");
              result[1] = false;
              return result;
            case UNKNOWN:
            default:
              throw new Exception("I couldn't check it. Sorry about that.");
          }
          solver.pop();// bounded with invar
        }
        
        solver.pop(); // before and after definition
      }
      // end lexicgraphic check



    }
    return result;
  }
  
private static boolean canReach(List<Block> cfg, int start, int target) {
  if (start == target) return true;

  boolean[] seen = new boolean[cfg.size()];
  LinkedList<Integer> q = new LinkedList<>();
  q.add(start);
  seen[start] = true;

  while (!q.isEmpty()) {
    int v = q.removeFirst();
    if (v < 0 || v >= cfg.size()) continue;

    Block b = cfg.get(v);
    if (b == null) continue;

    for (Jump j : b.jumps) {
      int u = j.dst;
      if (u == target) return true;
      if (u >= 0 && u < cfg.size() && !seen[u]) {
        seen[u] = true;
        q.add(u);
      }
    }
  }
  return false;
}

private static void dumpModelAtHead(
    Context ctx, Solver solver,
    List<LocalVariableNode> vars,
    Block loop, Value[] summaryVars,
    int head, String[] watchVars
) {
  if (!"1".equals(System.getenv("RANKCHECK_DUMP_MODEL"))) return;

  String only = System.getenv("RANKCHECK_DUMP_HEAD");
  if (only != null && !only.equals(Integer.toString(head))) return;

  Model m = solver.getModel();
  if (m == null) return;

  System.out.println("===== MODEL DUMP head=" + head + " =====");

  // watchVars 在 before(loop.input) 与 after(summaryVars) 的取值
  for (String v : watchVars) {
    try {
      IntExpr pre  = getField(ctx, vars, loop.input,  new String[]{v});
      IntExpr post = getField(ctx, vars, summaryVars, new String[]{v});
      System.out.println("VAR " + v
          + " : pre=" + m.evaluate(pre, true)
          + "  post(summary)=" + m.evaluate(post, true));
    } catch (Exception ignored) {}
  }

  // 打印每条回边在模型里是否被选中，以及该回边 output 里的 after 值
  for (int idx = 0; idx < loop.jumps.size(); idx++) {
    Jump j = loop.jumps.get(idx);
    if (j.dst != head) continue;

    System.out.println("BACKEDGE#" + idx
        + " dst=" + j.dst
        + " cond=" + m.evaluate(j.condition, true)
        + " output=" + java.util.Arrays.toString(j.output));

    for (String v : watchVars) {
      try {
        IntExpr aj = getField(ctx, vars, j.output, new String[]{v});
        System.out.println("  " + v + "After(jumpOutput)=" + m.evaluate(aj, true));
      } catch (Exception ignored) {}
    }
  }

  System.out.println("===== END DUMP =====");
}







private static Boolean[] checkVec(
    Context ctx, List<LocalVariableNode> vars, Procedure procedure, List<Integer> heads,
    List<String> args,
    List<List<Integer>> out, List<List<List<Integer>>> hidden,
    boolean areRelus, int delta,
    Map<String, Integer> cex) throws Exception {


  if (envOn("RANKCHECK_SCC_STEP")) {
  return checkVecBySccStep(ctx, vars, procedure, heads, args, out, hidden, areRelus, delta, cex);
}


  int rankdim = out.size();
  if (rankdim <= 0) throw new IllegalArgumentException("rankdim must be >= 1");
  if (hidden.size() != rankdim) throw new IllegalArgumentException("out/hidden rankdim mismatch");

  Boolean[] result = {false, true, false}; // [decreaseOK, placeholder, invarUsed]
  Set<Integer> outer = new HashSet<Integer>(heads);

  for (int head_i = heads.size() - 1; head_i >= 0; head_i--) {
    int head = heads.get(head_i);
    outer.remove(head);
    Set<Integer> noDstUsed = new HashSet<>(outer);

    Block loop;
    try {
      loop = summarize(ctx, procedure.controlFlow, head, head, noDstUsed);
    } catch (IndexOutOfBoundsException e) {
      return new Boolean[]{false, true, false};
    }

    Solver solver = ctx.mkSolver();

    // summaryVars: merge all back-edge outputs
    LinkedList<Value[]> exitValues = new LinkedList<>();
    for (Jump outJump : loop.jumps) {
      if (outJump.dst == head) {          // 只保留回到 head 的“迭代边”
        exitValues.add(outJump.output);
      }
    }
    if (exitValues.isEmpty()) {
      System.out.println("[checkVec] head=" + head + " has no back-edge outputs; treat as TRIVIAL OK");
      result[0] = true;
      continue;
    }
    Value[] summaryVars = mkSummaryVars(ctx, exitValues, "sum" + head + "var");


    // encode loop semantics
    encodeAndSummarise(ctx, solver, loop, head, summaryVars);

    if ("1".equals(System.getenv("RANKCHECK_ASSUME_A_PLUS2"))) {
  try {
    IntExpr aBefore = getField(ctx, vars, loop.input,  new String[]{"a"});
    IntExpr aAfter  = getField(ctx, vars, summaryVars, new String[]{"a"});
    solver.add(ctx.mkGe(aAfter, ctx.mkAdd(aBefore, ctx.mkInt(2))));
    System.out.println("[assume] added: aAfter >= aBefore + 2");
  } catch (Exception ignored) {}
}


    // candidate invariants (keep original behavior)
    LinkedList<LinkedList<BoolExpr>[]> cadidateInvars = new LinkedList<>();
    cadidateInvars.add(preconditionAsInvar(ctx, procedure, head, loop, summaryVars));
    cadidateInvars.addAll(builtInInvars(ctx, loop.input, summaryVars));

    // filter unusable invariants (keep original behavior)
    Iterator<LinkedList<BoolExpr>[]> it = cadidateInvars.iterator();
    while (it.hasNext()) {
      LinkedList<BoolExpr>[] candidate = it.next();

      // precondition check
      {
        Block tail = summarize(ctx, procedure.controlFlow, 0, head, Collections.emptySet());
        Solver preSolver = ctx.mkSolver();
        encodeAndSummarise(ctx, preSolver, tail, head, summaryVars);
        preSolver.add(parseAssumeAtHead(ctx, args, tail.input));
        for (BoolExpr stmt : candidate[1]) preSolver.add(stmt); // already negated
        if (preSolver.check() == Status.SATISFIABLE) {
          it.remove();
          continue;
        }
      }

      // inductiveness check
      {
        Solver invSolver = ctx.mkSolver();
        encodeAndSummarise(ctx, invSolver, loop, head, summaryVars);
        invSolver.add(parseAssumeAtHead(ctx, args, loop.input));
        for (BoolExpr stmt : candidate[0]) invSolver.add(stmt);
        for (BoolExpr stmt : candidate[1]) invSolver.add(stmt);
        if (invSolver.check() == Status.SATISFIABLE) {
          it.remove();
          continue;
        }
      }
    }

    // build before/after vectors (IMPORTANT: do NOT pop these constraints!)
    IntExpr[] before = new IntExpr[rankdim];
    IntExpr[] after  = new IntExpr[rankdim];

    System.out.println("[checkVec] args = " + args);
    for (int p = 0; p < out.size(); p++) {
      System.out.println("[checkVec] p=" + p + " out=" + out.get(p));
      System.out.println("[checkVec] p=" + p + " hidden=" + hidden.get(p));
    }

    for (int p = 0; p < rankdim; p++) {
      IntExpr b2 = (IntExpr) ctx.mkFreshConst("before2_p" + p, ctx.mkIntSort());
      IntExpr a2 = (IntExpr) ctx.mkFreshConst("after2_p"  + p, ctx.mkIntSort());
      IntExpr b  = (IntExpr) ctx.mkFreshConst("before_p"  + p, ctx.mkIntSort());
      IntExpr a  = (IntExpr) ctx.mkFreshConst("after_p"   + p, ctx.mkIntSort());

      List<Integer> outW = out.get(p);
      List<List<Integer>> hidW = hidden.get(p);

      if (areRelus) {
        solver.add(ctx.mkEq(b2, weightedSumOfRelus(ctx, vars, args, outW, hidW, loop.input)));
        solver.add(ctx.mkEq(a2, weightedSumOfRelus(ctx, vars, args, outW, hidW, summaryVars)));

        // clamp to ReLU>=0
        solver.add(ctx.mkImplies(ctx.mkGe(b2, ctx.mkInt(0)), ctx.mkEq(b, b2)));
        solver.add(ctx.mkImplies(ctx.mkLt(b2, ctx.mkInt(0)), ctx.mkEq(b, ctx.mkInt(0))));
        solver.add(ctx.mkImplies(ctx.mkGe(a2, ctx.mkInt(0)), ctx.mkEq(a, a2)));
        solver.add(ctx.mkImplies(ctx.mkLt(a2, ctx.mkInt(0)), ctx.mkEq(a, ctx.mkInt(0))));
      } else {
        // linear-only mode (legacy)
        if (!(outW.size() == 1 && outW.get(0) == 1 && hidW.size() == 1)) {
          throw new IllegalArgumentException("non-relu vec not supported");
        }
        solver.add(ctx.mkEq(b, linearCombination(ctx, vars, args, hidW.get(0), loop.input)));
        solver.add(ctx.mkEq(a, linearCombination(ctx, vars, args, hidW.get(0), summaryVars)));
      }

      before[p] = b;
      after[p]  = a;
    }

    // lexicographic strict decrease: OR_p (prefix non-increase && p decreases by >=delta)
    ArrayList<BoolExpr> disj = new ArrayList<>();
    for (int p = 0; p < rankdim; p++) {
      ArrayList<BoolExpr> conj = new ArrayList<>();
      for (int q = 0; q < p; q++) {
        conj.add(ctx.mkLe(after[q], before[q]));
      }
      conj.add(ctx.mkLe(after[p], (ArithExpr) ctx.mkSub(before[p], ctx.mkInt(delta))));
      disj.add(ctx.mkAnd(conj.toArray(new BoolExpr[0])));
    }
    BoolExpr lexDec = (rankdim == 1) ? disj.get(0) : ctx.mkOr(disj.toArray(new BoolExpr[0]));

    // restrict to staying transitions (back-edge)
    BoolExpr stay = ctx.mkFalse();
    for (Jump j : loop.jumps) {
      if (j.dst == head) {
        stay = ctx.mkOr(stay, j.condition);
      }
    }


    // 如果找不到回边，宁可不放宽到 true（否则会把退出边也算进来，极易误报 NO）
    if (stay.isFalse()) {
      throw new IllegalStateException("Cannot identify back-edge transitions (stay=false).");
    }

    System.out.println("[checkVec] checking lexDec at head=" + head + ", rankdim=" + rankdim);
    System.out.println("[checkVec] loop.jumps.size = " + loop.jumps.size());
    for (Jump jj : loop.jumps) System.out.println("[checkVec] " + jj);
    System.out.println("[checkVec] stay = " + stay);


// enabled(head): head 的“进入循环体”分支（能回到 head 的那条边）的条件 OR
BoolExpr enabled = ctx.mkFalse();
Block headBlock = procedure.controlFlow.get(head);

if (headBlock != null) {
  for (Jump j : headBlock.jumps) {
    if (canReach(procedure.controlFlow, j.dst, head)) {
      enabled = ctx.mkOr(enabled, j.condition);
    }
  }
}

// 如果推不出来 enabled（极少数 CFG 情况），就不额外限制，保持旧行为
// （也可以选择 throw，让你尽早发现 CFG 异常）
if (enabled.isFalse()) {
  enabled = stay;
}

    // check violation: exists step in loop with NOT lexDec
    solver.push();
    solver.add(parseAssume0(ctx, args));
    solver.add(parseAssumeAtHead(ctx, args, summaryVars));
    solver.add(enabled);
    solver.add(stay);
    solver.add(ctx.mkNot(lexDec));



    Status statusLex = solver.check();
    System.out.println("[checkVec] status(not lexDec) = " + statusLex);

    if (statusLex == Status.UNSATISFIABLE) {
      result[0] = true;
      solver.pop();
    } else if (statusLex == Status.SATISFIABLE) {
      // try again with invariants
      for (LinkedList<BoolExpr>[] inv : cadidateInvars) {
        for (BoolExpr stmt : inv[0]) solver.add(stmt);
      }
      Status statusInv = solver.check();
      if (statusInv == Status.UNSATISFIABLE) {
        result[0] = true;
        result[2] = true;
        solver.pop();
      } else if (statusInv == Status.SATISFIABLE) {
        System.out.println("[checkVec] SAT even with invariants -> extracting cex");
        dumpModelAtHead(ctx, solver, vars, loop, summaryVars, head, new String[]{"a","b"});
        getCex(ctx, solver, procedure, loop, head_i, head, outer, cex);
        result[0] = false;
        solver.pop();
        return result;
      } else {
        throw new Exception("Z3 returned UNKNOWN");
      }
    } else {
      throw new Exception("Z3 returned UNKNOWN");
    }


  }

  return result;
}







  private static void setTimeout(Context ctx, Solver s, int timeoutMs) {
  Params p = ctx.mkParams();
  p.add("timeout", timeoutMs);
  s.setParameters(p);
}

  public static Boolean[] check(ClassLoader cl, String className, String methodName,
      List<Integer> heads, List<String> args, List<List<Integer>> out, List<List<List<Integer>>> hidden, boolean areRelus, Map<String, Integer> cex) throws Exception {

    Context ctx = new Context();
    ClassReader reader = new ClassReader(cl);

    Procedure proc = encodeProgram(ctx, reader, className, methodName);

    MethodNode methodNode = reader.resolveMethodNode(className, methodName);

//    if (ranks.values().stream().anyMatch(r -> r.size() != methodNode.maxLocals + 1))
//      throw new IllegalArgumentException("Invalid ranking function size");

    int[] insnToBlock = proc.insnIndexToBlock;

    // find line number
    List<Integer> headBlocks = new LinkedList<>();
    for (int offset : heads) {
      LabelNode rankLabel = null;
      for (AbstractInsnNode insn : methodNode.instructions) {
        if (!(insn instanceof LabelNode))
          continue;

        LabelNode ln = (LabelNode) insn;

        if (reader.getOffset(ln.getLabel()).equals(offset)) {
          rankLabel = ln;
          break;
        }
      }
      if (rankLabel == null)
        throw new IllegalArgumentException(offset + " is an invalid offset");
      headBlocks.add(insnToBlock[methodNode.instructions.indexOf(rankLabel)]);
    }

    return check(ctx, methodNode.localVariables, proc, headBlocks, args, out, hidden, areRelus, cex);
  }

  public static Boolean[] checkVec(
    ClassLoader cl,
    String className,
    String methodName,
    List<Integer> heads,
    List<String> args,
    List<List<Integer>> out,
    List<List<List<Integer>>> hidden,
    boolean areRelus,
    int delta,
    Map<String, Integer> cex) throws Exception {

  Context ctx = new Context();
  ClassReader reader = new ClassReader(cl);

  Procedure proc = encodeProgram(ctx, reader, className, methodName);
  MethodNode methodNode = reader.resolveMethodNode(className, methodName);

  int[] insnToBlock = proc.insnIndexToBlock;

  // offsets -> blocks
  List<Integer> headBlocks = new LinkedList<>();
  for (int offset : heads) {
    LabelNode rankLabel = null;
    for (AbstractInsnNode insn : methodNode.instructions) {
      if (!(insn instanceof LabelNode)) continue;
      LabelNode ln = (LabelNode) insn;
      if (reader.getOffset(ln.getLabel()).equals(offset)) { rankLabel = ln; break; }
    }
    if (rankLabel == null) throw new IllegalArgumentException(offset + " is an invalid offset");
    headBlocks.add(insnToBlock[methodNode.instructions.indexOf(rankLabel)]);
  }

  return checkVec(ctx, methodNode.localVariables, proc, headBlocks, args, out, hidden, areRelus, delta, cex);
}







private static com.microsoft.z3.BoolExpr mkNotLexDecVector(
    com.microsoft.z3.Context ctx,
    com.microsoft.z3.IntExpr[] before,
    com.microsoft.z3.IntExpr[] after,
    int delta,
    int dim
) {
  // lexDec := OR_{t=0..dim-1} ( (AND_{i<t} after[i]==before[i]) AND (before[t]-after[t] >= delta) )
  com.microsoft.z3.BoolExpr lexDec = ctx.mkFalse();

  for (int t = 0; t < dim; t++) {
    com.microsoft.z3.BoolExpr prefixEq = ctx.mkTrue();
    for (int i = 0; i < t; i++) {
      prefixEq = ctx.mkAnd(prefixEq, ctx.mkEq(after[i], before[i]));
    }
    com.microsoft.z3.BoolExpr dec =
        ctx.mkGe(ctx.mkSub(before[t], after[t]), ctx.mkInt(delta));

    lexDec = ctx.mkOr(lexDec, ctx.mkAnd(prefixEq, dec));
  }

  // notLexDec
  return ctx.mkNot(lexDec);
}

private static void fillCexFromArgs(
    com.microsoft.z3.Context ctx,
    com.microsoft.z3.Solver solver,
    java.util.List<String> args,
    java.util.Map<String, Integer> cex
) {
  if (ctx == null || solver == null || args == null || cex == null) return;
  com.microsoft.z3.Model m = solver.getModel();
  if (m == null) return;

  for (String a : args) {
    if (a == null) continue;
    if (cex.containsKey(a)) continue;

    try {
      // 约定：输入态变量用 !0（你日志里也能看到 i!0, j!0 ...）
      com.microsoft.z3.IntExpr v0 = ctx.mkIntConst(a + "!0");
      com.microsoft.z3.Expr ev = m.evaluate(v0, true);
      if (ev != null && ev.isIntNum()) {
        cex.put(a, ((com.microsoft.z3.IntNum) ev).getInt());
      }
    } catch (Exception ignored) {
      // 忽略：某些变量名在当前模型里未出现
    }
  }
}





  // we add this because pyjnius complains with polymorphism
  public static Boolean[] _check(ClassLoader cl, String className, String methodName, Integer offset, List<String> args, List<Integer> coeffs) throws Exception {
    return check(cl, className, methodName, List.of(offset), args, List.of(List.of(1)), List.of(List.of(coeffs)), false, new HashMap<>());
  }
  
  public static Boolean[] check(ClassLoader cl, String className, String methodName, Integer offset, List<String> args, List<Integer> coeffs) throws Exception {
    return check(cl, className, methodName, List.of(offset), args, List.of(List.of(1)), List.of(List.of(coeffs)), false, new HashMap<>());
  }
  
  public static Boolean[] checkRelu(ClassLoader cl, String className, String methodName, Integer offset, List<String> args, List<List<Integer>> coeffs) throws Exception {
    return check(cl, className, methodName, List.of(offset), args, List.of(Collections.nCopies(coeffs.size(), 1)), List.of(coeffs), true, new HashMap<>());
  }
  
  public static Boolean[] checkRelu(String className, String methodName, Integer offset, List<String> args, List<List<Integer>> coeffs) throws Exception {
    return check(ClassLoader.getSystemClassLoader(), className, methodName, List.of(offset), args, List.of(Collections.nCopies(coeffs.size(), 1)), List.of(coeffs), true, new HashMap<>());
  }
  
  public static Boolean[] checkRelu(String className, String methodName, Integer offset, List<String> args, List<Integer> out, List<List<Integer>> hidden) throws Exception {
    return check(ClassLoader.getSystemClassLoader(), className, methodName, List.of(offset), args, List.of(out), List.of(hidden), true, new HashMap<>());
  }
  
  public static Boolean[] _checkRelu(ClassLoader cl, String className, String methodName, Integer offset, List<String> args, List<List<Integer>> coeffs) throws Exception {
    return check(cl, className, methodName, List.of(offset), args, List.of(Collections.nCopies(coeffs.size(), 1)), List.of(coeffs), true, new HashMap<>());
  }
  
  public static Boolean[] checkLexiRelu(String className, String methodName, List<Integer> heads, List<String> args, List<List<List<Integer>>> coeffs) throws Exception {
    return check(ClassLoader.getSystemClassLoader(), className, methodName, heads, args, 
        coeffs.stream().map(l -> Collections.nCopies(l.size(), 1)).collect(Collectors.toList()), 
        coeffs, true, new HashMap<>());
  }
  
  public static Boolean[] _checkLexiRelu(ClassLoader cl, String className, String methodName, List<Integer> heads, List<String> args, List<List<List<Integer>>> coeffs) throws Exception {
    return check(cl, className, methodName, heads, args, 
        coeffs.stream().map(l -> Collections.nCopies(l.size(), 1)).collect(Collectors.toList()), 
        coeffs, true, new HashMap<>());
  }
  
  public static Boolean[] _checkLexiReluOrCex(ClassLoader cl, String className, String methodName, List<Integer> heads, List<String> args, List<List<List<Integer>>> coeffs, Map<String,Integer> cex) throws Exception {
    return check(cl, className, methodName, heads, args, 
        coeffs.stream().map(l -> Collections.nCopies(l.size(), 1)).collect(Collectors.toList()), 
        coeffs, true, cex);
  }
  
  public static Boolean[] _checkLexiReluOrCex2(ClassLoader cl, String className, String methodName, List<Integer> heads, List<String> args, List<List<Integer>> out, List<List<List<Integer>>> hidden, Map<String,Integer> cex) throws Exception {
    return check(cl, className, methodName, heads, args, out, hidden, true, cex);
  }

  // =============================================================
  // Scheme B: rankdim 与 loop_heads 解耦 —— Vec Lex ReLU Checker
  // =============================================================
public static Boolean[] _checkLexiReluVecOrCex2(
    ClassLoader cl,
    String className,
    String methodName,
    List<Integer> heads,
    List<String> args,
    List<List<Integer>> out,
    List<List<List<Integer>>> hidden,
    Integer delta,
    Map<String, Integer> cex) throws Exception {

  int d = (delta == null) ? 1 : delta.intValue();
  return checkVec(cl, className, methodName, heads, args, out, hidden, true, d, cex);
}



  // NEW
    private static List<Integer> quantize1D(List<Double> xs, int scale) {
        List<Integer> r = new ArrayList<>(xs.size());
        for (double v : xs) r.add((int)Math.round(v * scale));
        return r;
    }

    private static List<List<Integer>> quantize2D(List<List<Double>> m, int scale) {
        List<List<Integer>> r = new ArrayList<>(m.size());
        for (List<Double> row : m) r.add(quantize1D(row, scale));
        return r;
    }

    //
    private static List<List<Integer>> packHidden(List<List<Integer>> W_int, List<Integer> b_int) {
        List<List<Integer>> hidden = new ArrayList<>(W_int.size());
        for (int i = 0; i < W_int.size(); i++) {
            List<Integer> row = new ArrayList<>(W_int.get(i));
            row.add(b_int.get(i));
            hidden.add(row);
        }
        return hidden;
    }

    // NEW
    private static Boolean[] checkAsymmetric(
        Context ctx,
        List<org.objectweb.asm.tree.LocalVariableNode> vars,
        Procedure procedure,
        List<Integer> heads,
        List<String> args,
        List<List<Integer>> outAfter,   // U: AFTER
        List<List<List<Integer>>> hidAfter,
        List<List<Integer>> outBefore,  // L: BEFORE
        List<List<List<Integer>>> hidBefore,
        Map<String,Integer> cex
    ) throws Exception {

        Boolean[] result = {false, true, false}; // dec, bounded/invar-needed, sat?
        Set<Integer> outer = new HashSet<>(heads);

        int nLexi = Math.min(
            Math.min(outAfter.size(), outBefore.size()),
            Math.min(hidAfter.size(), hidBefore.size())
        );
        if (nLexi == 0) {
            //
            return new Boolean[]{false, true, false};
        }

        for (int head_i = heads.size() - 1; head_i >= 0; head_i--) {
            int head = heads.get(head_i);

            outer.remove(head);

            Set<Integer> noDstUsed = new HashSet<>(outer);

            Block loop = summarize(ctx, procedure.controlFlow, head, head, noDstUsed);

            Solver solver = ctx.mkSolver();
            Value[] summaryVars = mkSummaryVars(ctx, Arrays.asList(loop.input, loop.input), "S");

            //

            IntExpr before = (IntExpr) ctx.mkFreshConst("before", ctx.mkIntSort());
            IntExpr after  = (IntExpr) ctx.mkFreshConst("after",  ctx.mkIntSort());
            IntExpr before2 = (IntExpr) ctx.mkFreshConst("before2", ctx.mkIntSort());
            IntExpr after2  = (IntExpr) ctx.mkFreshConst("after2",  ctx.mkIntSort());

            int lexiMax = (heads.size() == 1)
            ? (nLexi - 1)
            : Math.min(head_i, nLexi - 1);


                for (int lexi_i = 0; lexi_i <= lexiMax; lexi_i++) {
                if (!hasUsableHead(procedure.controlFlow, heads.get(head_i))) {
                    continue;
                }
                List<Integer> outW_B = outBefore.get(lexi_i);            // NEW
                List<List<Integer>> hidW_B = hidBefore.get(lexi_i);      // NEW
                List<Integer> outW_A = outAfter.get(lexi_i);             // NEW
                List<List<Integer>> hidW_A = hidAfter.get(lexi_i);       // NEW

                solver.push(); // define before & after

                //
                solver.add(ctx.mkEq(before2, weightedSumOfRelus(ctx, vars, args, outW_B, hidW_B, loop.input)));
                solver.add(ctx.mkEq(after2,  weightedSumOfRelus(ctx, vars, args, outW_A, hidW_A, summaryVars)));

                // ReLU output0
                solver.add(ctx.mkImplies(ctx.mkGe(before2, ctx.mkInt(0)), ctx.mkEq(before, before2)));
                solver.add(ctx.mkImplies(ctx.mkLt(before2, ctx.mkInt(0)), ctx.mkEq(before, ctx.mkInt(0))));
                solver.add(ctx.mkImplies(ctx.mkGe(after2,  ctx.mkInt(0)), ctx.mkEq(after,  after2)));
                solver.add(ctx.mkImplies(ctx.mkLt(after2,  ctx.mkInt(0)), ctx.mkEq(after,  ctx.mkInt(0))));

                //
                solver.push(); // decrease w/o invariant
                if (lexi_i == head_i) {
                    solver.add(ctx.mkGe(after, before));  //
                } else {
                    solver.add(ctx.mkGt(after, before));
                }

                Status status = solver.check();
                switch (status) {
                    case UNSATISFIABLE:
                        result[0] = true; // OK
                        break;
                    case SATISFIABLE:
                        getCex(ctx, solver, procedure, loop, head_i, head, outer, cex);
                        result[0] = false;
                        return result;
                    default:
                        throw new Exception("Unknown");
                }
                solver.pop(); // decrease w/o invariant

                //
                solver.pop(); // before/after definition
            }

            //

        }

        return result;
    }

    public static class Result {
    private final boolean decrease;
    private final boolean invar;
    private final java.util.Map<String, java.lang.Object> cex;

    public Result(boolean decrease, boolean invar, java.util.Map<String, java.lang.Object> cex) {
        this.decrease = decrease;
        this.invar = invar;
        this.cex = cex;
    }
    public boolean getDecrease() { return decrease; }
    public boolean getInvar()    { return invar; }
    public java.util.Map<String, java.lang.Object> getCex() { return cex; }
}

    @SuppressWarnings({"rawtypes","unchecked"})
    public static Result check_sum_of_softplus_envelope(
            String jarfile, String classname, String methodname,
            List loopHeadsRaw, List inputVarsRaw,
            List outURaw, List WURaw, List bURaw,
            List outLRaw, List WLRaw, List bLRaw
    ) {
        try {
            // --- 0)
            List<Integer> loopHeads = (List<Integer>) loopHeadsRaw;
            List<String>  inputVars = (List<String>)  inputVarsRaw;

            List<List<List<Double>>> outU = (List<List<List<Double>>>) outURaw;
            List<List<List<Double>>> WU   = (List<List<List<Double>>>)  WURaw;
            List<List<Double>>       bU   = (List<List<Double>>)        bURaw;

            List<List<List<Double>>> outL = (List<List<List<Double>>>) outLRaw;
            List<List<List<Double>>> WL   = (List<List<List<Double>>>)  WLRaw;
            List<List<Double>>       bL   = (List<List<Double>>)        bLRaw;

            // --- 1)
            Context ctx = new Context();
            java.net.URL jarUrl = new java.io.File(jarfile).toURI().toURL();
            java.net.URLClassLoader cl = new java.net.URLClassLoader(
                    new java.net.URL[]{jarUrl},
                    RankChecker.class.getClassLoader()
            );
            ClassReader reader = new ClassReader(cl);
            Procedure proc = encodeProgram(ctx, reader, classname, methodname);
            MethodNode methodNode = reader.resolveMethodNode(classname, methodname);

            // --- 1.5)
            List<String> argsFixed = new ArrayList<>();
            if (inputVars != null) argsFixed.addAll(inputVars);

            if (argsFixed.isEmpty() && methodNode.localVariables != null) {
                for (org.objectweb.asm.tree.LocalVariableNode lv : methodNode.localVariables) {
                    if (lv == null || lv.name == null) continue;
                    if ("this".equals(lv.name)) continue;
                    if (!argsFixed.contains(lv.name)) argsFixed.add(lv.name);
                }
            }

            if (argsFixed.isEmpty()) {
                argsFixed.add("x0");
            }

            System.out.printf("[RankChecker] args=%s heads=%s CFG=%d%n",
                argsFixed.toString(), loopHeads.toString(), proc.controlFlow.size());

            // --- 2)
            final int SCALE = 1024;
            List<List<Integer>>        outAfterInt  = new ArrayList<>();
            List<List<List<Integer>>>  hidAfterInt  = new ArrayList<>();
            List<List<Integer>>        outBeforeInt = new ArrayList<>();
            List<List<List<Integer>>>  hidBeforeInt = new ArrayList<>();

            int nHeads = loopHeads.size();
            for (int i = 0; i < nHeads; i++) {
                // AFTER(U)
                List<Integer> outU_i = quantize1D(outU.get(i).get(0), SCALE);
                List<List<Integer>> WU_i = quantize2D(WU.get(i), SCALE);
                List<Integer> bU_i = quantize1D(bU.get(i), SCALE);
                outAfterInt.add(outU_i);
                hidAfterInt.add(packHidden(WU_i, bU_i));

                // BEFORE(L)
                List<Integer> outL_i = quantize1D(outL.get(i).get(0), SCALE);
                List<List<Integer>> WL_i = quantize2D(WL.get(i), SCALE);
                List<Integer> bL_i = quantize1D(bL.get(i), SCALE);
                outBeforeInt.add(outL_i);
                hidBeforeInt.add(packHidden(WL_i, bL_i));
            }

            // loopHeads: offsets -> indices
            List<Integer> loopHeadIdx = new ArrayList<>(loopHeads.size());
            for (int h : loopHeads) {
                int idx = offsetToIndex(proc.controlFlow, h);
                if (idx < 0) {
                    continue;
                }
                if (!hasUsableHead(proc.controlFlow, idx)) {
                    int fallback = firstUsableHead(proc.controlFlow);
                    if (fallback >= 0) idx = fallback;
                    else continue;
                }
                loopHeadIdx.add(idx);
            }
            if (loopHeadIdx.isEmpty()) {
                return new Result(false, true, new java.util.HashMap<String, java.lang.Object>());

            }
            System.out.printf("[RankChecker] heads(offset)=%s -> heads(index)=%s, CFG=%d%n",
                    loopHeads.toString(), loopHeadIdx.toString(), proc.controlFlow.size());

            // --- 3)
            Map<String, Integer> cex = new HashMap<>();
            Boolean[] res = checkAsymmetric(
                    ctx, methodNode.localVariables, proc,
                    loopHeadIdx, argsFixed,
                    outAfterInt,  hidAfterInt,
                    outBeforeInt, hidBeforeInt,
                    cex
            );

            // --- 4)
            Map<String, java.lang.Object> cexObj = new HashMap<String, java.lang.Object>();
            for (Map.Entry<String, Integer> e : cex.entrySet()) {
                cexObj.put(e.getKey(), java.lang.Integer.valueOf(e.getValue()));
            }

            boolean decrease = res[0];
            boolean needInvarOrBounded = res[1];
            return new Result(decrease, needInvarOrBounded, cexObj);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Map a bytecode offset (e.g., 42) to the CFG block index by parsing Block.title.
    // It looks for any integer number inside the title string.
    private static int offsetToIndex(java.util.List<Block> cfg, int offset) {
    if (cfg == null || cfg.isEmpty()) return -1;

    // 1)
    for (int i = 0; i < cfg.size(); i++) {
        Block b = cfg.get(i);
        if (b == null || b.title == null) continue;
        Matcher m = Pattern.compile("(\\d+)").matcher(b.title);
        while (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1));
                if (v == offset) return i;
            } catch (NumberFormatException ignore) {}
        }
    }

    // 2)
    if (offset >= 0 && offset < cfg.size()) return offset;

    // 3)
    int bestIdx = 0;
    int bestDist = Integer.MAX_VALUE;
    for (int i = 0; i < cfg.size(); i++) {
        Block b = cfg.get(i);
        if (b == null || b.title == null) continue;
        Matcher m = Pattern.compile("(\\d+)").matcher(b.title);
        while (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1));
                int dist = Math.abs(v - offset);
                if (dist < bestDist) { bestDist = dist; bestIdx = i; }
            } catch (NumberFormatException ignore) {}
        }
    }
    return bestIdx;
}
    private static boolean hasUsableHead(java.util.List<Block> cfg, int idx) {
        if (cfg == null || idx < 0 || idx >= cfg.size()) return false;
        Block b = cfg.get(idx);
        if (b == null) return false;
        //
        if (b.input != null && b.input.length > 0) return true;
        if (b.jumps != null && !b.jumps.isEmpty()) return true;
        if (b.statements != null && !b.statements.isEmpty()) return true;
        return false;
    }

    private static int firstUsableHead(java.util.List<Block> cfg) {
        if (cfg == null) return -1;
        for (int i = 0; i < cfg.size(); i++) {
            if (hasUsableHead(cfg, i)) return i;
        }
        return -1;
    }

}
