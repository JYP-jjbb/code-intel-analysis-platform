package javachecker;


import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

public class CFGAnalyzer {

  static List<Integer> loopHeaders(InsnList insnList) {
    int[] color = new int[insnList.size()];
    Arrays.fill(color, 0);

    Set<Integer> headers = new HashSet<>();
    LinkedList<Integer> sorted = new LinkedList<>();
    
    Stack<Integer> next = new Stack<>();
    next.push(0);
    while (!next.isEmpty()) {
      int index = next.pop();
      // if odd mark that the subgraph has been visited (that's color 2)
      if (index % 2 > 0) {
        index = index / 2;
        color[index] = 2;
        if (headers.contains(index)) 
          sorted.addFirst(index);
        continue;
      }
      index = index / 2;

      if (color[index] == 2) 
        continue;
      if (color[index] == 1) {
        headers.add(index);
        continue;
      }

      // mark that the visit has began (that's color 1)
      color[index] = 1;
      next.push(2 * index + 1);
      
      // push successors 
      AbstractInsnNode insn = insnList.get(index);
      if (insn.getType() == AbstractInsnNode.JUMP_INSN) 
        next.push(2 * insnList.indexOf(((JumpInsnNode) insn).label));
      
      switch (insn.getOpcode()) {
        case Opcodes.GOTO:
        case Opcodes.JSR:
        case Opcodes.IRETURN:
        case Opcodes.LRETURN:
        case Opcodes.FRETURN:
        case Opcodes.DRETURN:
        case Opcodes.ARETURN:
        case Opcodes.RETURN:
          continue;
      }
      
      if (insn.getNext() == null)
        continue;
      
      next.push(2 * (index + 1));
    }
    
    return sorted;
  }
  
  public static List<Integer> loopHeaders(String className, String methodName) throws NoSuchMethodException, ClassNotFoundException  {
    return loopHeaders(ClassLoader.getSystemClassLoader(), className, methodName);
  }
  
  public static boolean hasCalls(InsnList instructions) {
    return StreamSupport.stream(instructions.spliterator(), false)
        .anyMatch(i -> i.getType() == AbstractInsnNode.METHOD_INSN ||
            i.getType() == AbstractInsnNode.INVOKE_DYNAMIC_INSN);
  }
  
  public static List<Integer> loopHeaders(ClassLoader cl, String className, String methodName /* we should generalize to signature */) throws NoSuchMethodException, ClassNotFoundException {

    // load method
    ClassReader reader = new ClassReader(cl);
    MethodNode methodNode = reader.resolveMethodNode(className, methodName);
    
    InsnList instructions = methodNode.instructions;

    Map<Integer, Integer> indexToOffset = new HashMap<>();
    for (AbstractInsnNode insn : instructions) {
      if (insn instanceof LabelNode) {
        LabelNode l = (LabelNode)insn;
        indexToOffset.put(instructions.indexOf(insn), reader.getOffset(l.getLabel()));
      }
    }

    
    // compute loop headers indices (indices are not offsets)
    List<Integer> loopHeadersIndices = loopHeaders(instructions);
    
    // map to lines
    List<Integer> loopHeadersOffsets = loopHeadersIndices.stream().map(indexToOffset::get).collect(Collectors.toList());
    
    return loopHeadersOffsets;
  }
  

  public static Map<Integer, Integer> labelOffsetToLine(ClassLoader cl, String className, String methodName) throws NoSuchMethodException, ClassNotFoundException {
       
    // load method
    ClassReader reader = new ClassReader(cl);
    MethodNode methodNode = reader.resolveMethodNode(className, methodName);

    InsnList instructions = methodNode.instructions;
    
    Map<Integer, Integer> offsetToLine  = StreamSupport.stream(instructions.spliterator(), false)
        .filter(LineNumberNode.class::isInstance).map(LineNumberNode.class::cast)
        .collect(Collectors.toMap(n -> reader.getOffset(n.start.getLabel()), n -> n.line));
    
    int lastLine = -1;
    for (int i = 0; i < instructions.size(); ++i) {
      if (instructions.get(i).getType() == AbstractInsnNode.LABEL) {
        Label label = ((LabelNode)instructions.get(i)).getLabel();
        Integer line = offsetToLine.get(reader.getOffset(label));
        if (line != null)
          lastLine = line;
        offsetToLine.put(reader.getOffset(label), lastLine);
      }
    }
    
    return offsetToLine;
  }
  
  public static Map<Integer, Integer>  labelOffsetToLine(String className, String methodName) throws NoSuchMethodException, ClassNotFoundException {
    return labelOffsetToLine(ClassLoader.getSystemClassLoader(), className, methodName);
  }
  
  public static Map<Integer, SortedSet<Integer>> lineToLabelOffset(ClassLoader cl, String className, String methodName) throws NoSuchMethodException, ClassNotFoundException {
    Map<Integer, SortedSet<Integer>> lineToOffset = new HashMap<Integer, SortedSet<Integer>>();
    Map<Integer, Integer> offsetToLine = labelOffsetToLine(cl, className, methodName);
    for (Entry<Integer, Integer> entry : offsetToLine.entrySet()) {
      SortedSet<Integer> offsets = lineToOffset.get(entry.getValue());
      if (offsets == null) {
        offsets = new TreeSet<Integer>();
        lineToOffset.put(entry.getValue(), offsets);
      }
      offsets.add(entry.getKey());
    }
    return lineToOffset;
  }

  public static Map<Integer, SortedSet<Integer>> lineToLabelOffset(String className, String methodName) throws NoSuchMethodException, ClassNotFoundException {
    return lineToLabelOffset(ClassLoader.getSystemClassLoader(), className, methodName);
  }
}
