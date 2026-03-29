package javachecker;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

public class ClassReader {

  private HashMap<Label, Integer> labelToOffset = new HashMap<>();
  public ClassLoader cl;
  private Map<String, ClassNode> classNodes = new HashMap<>();
  
  public ClassReader(ClassLoader cl) {
    this.cl = cl;
  }
  
  private ClassNode read(String filename, final int parsingOptions) throws IOException {
    InputStream stream = cl.getResourceAsStream(filename);
    org.objectweb.asm.ClassReader asmClassReader = new org.objectweb.asm.ClassReader(stream) {
      @Override
      protected Label readLabel(int bytecodeOffset, Label[] labels) {
        Label l = super.readLabel(bytecodeOffset, labels);
        labelToOffset.put(l, bytecodeOffset);
        return l;
      }
    };
    
    ClassNode classNode = new ClassNode(Opcodes.ASM8) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
          String[] exceptions) {
        MethodNode method =
            new MethodNode(Opcodes.ASM8, access, name, descriptor, signature, exceptions) {
              @Override
              protected LabelNode getLabelNode(Label label) {
                if (!(label.info instanceof LabelNode)) {
                  label.info = new LabelNode(label);
                }
                return (LabelNode) label.info;
              }
            };
        methods.add(method);
        return method;
      }
    };
    asmClassReader.accept(classNode, parsingOptions);
    
    stream.close();
    return classNode;
  }

  ClassNode getClassNode(String className) throws ClassNotFoundException{
    ClassNode node = classNodes.get(className);
    if (node == null) {
      try {
        node = read(className.replace('.', '/') + ".class", 0);
      } catch (IOException e) {
        throw new ClassNotFoundException("Class " + className + " not found", e);
      }
      classNodes.put(className, node);
    }
    return node;
  }
  
  MethodNode resolveMethodNode(String owner, String name) throws NoSuchMethodException, ClassNotFoundException {
    ClassNode classNode = getClassNode(owner);
    if (classNode.methods.stream().filter(n -> n.name.equals(name)).count() != 1)
      throw new NoSuchMethodException("Invalid or ambiguous method name");
    return classNode.methods.stream().filter(n -> n.name.equals(name)).findFirst().get();
  }
  
  MethodNode resolveMethodNode(String owner, String name, String desc) throws ClassNotFoundException, NoSuchMethodException{
    ClassNode classNode = getClassNode(owner);
    MethodNode methodNode = null;
    
    while (methodNode == null && classNode != null) {
      methodNode = classNode.methods.stream()
      .filter(n -> n.name.equals(name) && n.desc.equals(desc)).findFirst().orElse(null);
      classNode = classNode.superName != null ? getClassNode(classNode.superName) : null;
    }
    
    //System.out.print(classNode.methods.stream().map(m -> m.name).collect(Collectors.toList()));
    if (methodNode == null)
      throw new NoSuchMethodException("Method " + name + " not found");
    return methodNode;
  }
  
  ClassNode resolveOwner(String clazz, String method, String desc) throws ClassNotFoundException, NoSuchMethodException{
    ClassNode classNode = getClassNode(clazz);
    
    while (classNode != null) {
      MethodNode methodNode = classNode.methods.stream()
      .filter(n -> n.name.equals(method) && n.desc.equals(desc)).findFirst().orElse(null);
      if (methodNode != null)
        return classNode;
      classNode = classNode.superName != null ? getClassNode(classNode.superName) : null;
    }
    
    throw new NoSuchMethodException("Method " + method + " not found");
  }
  
  Integer getOffset(Label label) {
    assert labelToOffset.containsKey(label);
    return labelToOffset.get(label);
  }
  
  public String classToString(String className) throws ClassNotFoundException {
    StringWriter sw = new StringWriter();
    Textifier textifier = new Textifier(Opcodes.ASM8) {
      @Override
      protected Textifier createTextifier() {
        return new Textifier(Opcodes.ASM8) {
          @Override
          public void visitLabel(Label label) {
            stringBuilder.setLength(0);
            stringBuilder.append(ltab);
            appendLabel(label);
            stringBuilder.append(" offset: " + getOffset(label) + '\n');
            text.add(stringBuilder.toString());
          };
        };
      };
    };

    TraceClassVisitor traceClassVisitor = new TraceClassVisitor(null, textifier, new PrintWriter(sw));
    getClassNode(className).accept(traceClassVisitor);
    
    return sw.toString();
  }
}
