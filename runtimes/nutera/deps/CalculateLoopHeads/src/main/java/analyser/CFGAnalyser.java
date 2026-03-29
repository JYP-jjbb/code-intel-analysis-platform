package analyser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

public class CFGAnalyser {

    static Set<Integer> loopHeaders(InsnList insnList) {
        int[] color = new int[insnList.size()];
        Arrays.fill(color, 0);

        Set<Integer> headers = new TreeSet<>();

        Stack<Integer> next = new Stack<>();
        next.push(0);
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

        return headers;
    }

    public static List<Integer> loopHeaders(ClassLoader cl, String className, String methodName) throws IOException {
        InputStream stream = cl.getResourceAsStream(className.replace('.', '/') + ".class");
        List<Integer> result = loopHeaders(stream, methodName);
        stream.close();
        return result;
    }

    public static List<Integer> loopHeaders(String className, String methodName) throws IOException {
        return loopHeaders(ClassLoader.getSystemClassLoader(), className, methodName);
    }

    public static boolean hasCalls(InsnList instructions) {
        return StreamSupport.stream(instructions.spliterator(), false)
                .anyMatch(i -> i.getType() == AbstractInsnNode.METHOD_INSN ||
                        i.getType() == AbstractInsnNode.INVOKE_DYNAMIC_INSN);
    }

    public static List<Integer> loopHeaders(final InputStream inputStream, String methodName /* we should generalize to signature */) {
        // load class
        HashMap<Label, Integer> labelToOffset = new HashMap<>();
        ClassNode classNode;
        try {
            classNode = readClass(inputStream, labelToOffset);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }

        // load method
        MethodNode methodNode = classNode.methods.stream()
                .filter(n -> n.name.equals(methodName)).findFirst().orElse(null);
        if (methodNode == null) {
            throw new IllegalArgumentException("Method not found");
        }
        InsnList instructions = methodNode.instructions;

        //if (hasCalls(instructions)) // TODO CHECK IF needed I commented it out for now
        //    throw new UnsupportedOperationException("Invocations are unsupported");

        // index to offset
        assert(labelToOffset.keySet().containsAll(StreamSupport.stream(instructions.spliterator(), false)
                .filter(LabelNode.class::isInstance).map(LabelNode.class::cast).map(LabelNode::getLabel)
                .collect(Collectors.toSet())));
//    Map<Integer, Integer> indexToOffset = StreamSupport.stream(instructions.spliterator(), false)
//        .filter(LabelNode.class::isInstance).map(LabelNode.class::cast)
//        .collect(Collectors.toMap(n -> instructions.indexOf(n), n -> labelToOffset.get(n)));

        Map<Integer, Integer> indexToOffset = new HashMap<>();
        for (AbstractInsnNode insn : instructions) {
            if (insn instanceof LabelNode) {
                LabelNode l = (LabelNode)insn;
                indexToOffset.put(instructions.indexOf(insn), labelToOffset.get(l.getLabel()));
            }
        }

//    int lastLine = -1;
//    for (int i = 0; i < instructions.size(); ++i) {
//      Integer line = indexToLine.get(i);
//      if (line != null)
//        lastLine = line;
//
//      if (instructions.get(i).getType() == AbstractInsnNode.LABEL)
//        indexToLine.put(i, lastLine);
//    }

        // compute loop headers indices (indices are not offsets)
        Set<Integer> loopHeadersIndices = loopHeaders(instructions);

        // map to lines
        List<Integer> loopHeadersOffsets = loopHeadersIndices.stream().map(indexToOffset::get).collect(Collectors.toList());

        return loopHeadersOffsets;
    }

    public static ClassNode readClass(final InputStream inputStream, HashMap<Label, Integer> labelToOffset) throws IOException {
        ClassReader reader = new ClassReader(inputStream) {
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
        reader.accept(classNode, 0);

        return classNode;
    }

    public static Map<Integer, Integer> labelOffsetToLine(final InputStream inputStream, String methodName) throws IOException {
        HashMap<Label, Integer> labelToOffset = new HashMap<>();
        ClassNode classNode = readClass(inputStream, labelToOffset);

        // load method
        MethodNode methodNode = classNode.methods.stream()
                .filter(n -> n.name.equals(methodName)).findFirst().orElse(null);
        if (methodNode == null)
            throw new IllegalArgumentException("Method not found");
        InsnList instructions = methodNode.instructions;

        Map<Integer, Integer> offsetToLine  = StreamSupport.stream(instructions.spliterator(), false)
                .filter(LineNumberNode.class::isInstance).map(LineNumberNode.class::cast)
                .collect(Collectors.toMap(n -> labelToOffset.get(n.start.getLabel()), n -> n.line));

        int lastLine = -1;
        for (int i = 0; i < instructions.size(); ++i) {
            if (instructions.get(i).getType() == AbstractInsnNode.LABEL) {
                Label label = ((LabelNode)instructions.get(i)).getLabel();
                Integer line = offsetToLine.get(labelToOffset.get(label));
                if (line != null)
                    lastLine = line;
                offsetToLine.put(labelToOffset.get(label), lastLine);
            }
        }

        return offsetToLine;
    }

    public static Map<Integer, Integer> labelOffsetToLine(ClassLoader cl, String className, String methodName) throws IOException {
        InputStream stream = cl.getResourceAsStream(className.replace('.', '/') + ".class");
        Map<Integer, Integer> result = labelOffsetToLine(stream, methodName);
        stream.close();
        return result;
    }

    public static Map<Integer, Integer>  labelOffsetToLine(String className, String methodName) throws IOException {
        return labelOffsetToLine(ClassLoader.getSystemClassLoader(), className, methodName);
    }

    public static Map<Integer, SortedSet<Integer>> lineToLabelOffset(final InputStream inputStream, String methodName) throws IOException {
        Map<Integer, SortedSet<Integer>> lineToOffset = new HashMap<Integer, SortedSet<Integer>>();
        Map<Integer, Integer> offsetToLine = labelOffsetToLine(inputStream, methodName);
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

    public static Map<Integer, SortedSet<Integer>> lineToLabelOffset(ClassLoader cl, String className, String methodName) throws IOException {
        InputStream stream = cl.getResourceAsStream(className.replace('.', '/') + ".class");
        Map<Integer, SortedSet<Integer>> result = lineToLabelOffset(stream, methodName);
        stream.close();
        return result;
    }

    public static Map<Integer, SortedSet<Integer>> lineToLabelOffset(String className, String methodName) throws IOException {
        return lineToLabelOffset(ClassLoader.getSystemClassLoader(), className, methodName);
    }
}
