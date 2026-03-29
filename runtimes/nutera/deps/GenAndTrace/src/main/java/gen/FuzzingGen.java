package gen;

import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

public class FuzzingGen implements InputGenerator {

    private final Random rnd;
    private final EasyRandom er;
    private long seed = 0L;

    // 语料池：保存“好用”的输入（例如能走更深/更长 trace 的）
    // 简化实现：仅做缓存与复用，若要“覆盖率反馈”可在 JVMTI 侧把长度/新位置数写回文件，再在 Python 外层挑选
    private final Deque<Object[]> corpus = new ArrayDeque<>();

    // 一些边界常量
    private static final int[] INT_EDGES = new int[]{
      0,1,-1,2,-2,3,-3,4,-4,5,-5,7,-7,8,-8,10,-10,16,-16,32,-32,
      63,-63,64,-64,127,-127,128,-128,
      Integer.MAX_VALUE, Integer.MIN_VALUE
    };
    private static final double[] DBL_EDGES = new double[]{
      0.0, 1.0, -1.0, 1e-9, -1e-9, 1e-6, -1e-6, 1e-3, -1e-3,
      0.5, -0.5, 2.0, -2.0, 10.0, -10.0, 1e3, -1e3, 1e6, -1e6,
      Double.MIN_NORMAL, Double.MIN_VALUE, Double.MAX_VALUE
    };

    public FuzzingGen() {
        this.rnd = new Random();
        this.er = new EasyRandom();
    }

    public FuzzingGen(long seed) {
        this.seed = seed;
        this.rnd = new Random(seed);
        EasyRandomParameters p = new EasyRandomParameters().seed(seed);
        this.er = new EasyRandom(p);
    }

    public void pushSeed(Object[] seed) {
    if (seed == null) return;
    corpus.addFirst(seed.clone());
    while (corpus.size() > 128) corpus.removeLast();
}

    @Override
    public Object[] nextSampleArguments(Type[] t) {
        // 1) 先随机/边界初始化一份
        Object[] base = makeOne(t);

        // 2) 如果语料池非空，则以一定概率复用并变异
        if (!corpus.isEmpty() && rnd.nextDouble() < 0.90) {   // 0.7 -> 0.90
            int k = Math.min(8, corpus.size());
            int pick = rnd.nextInt(k);
            Object[] from = corpus.stream().limit(k).toArray(Object[][]::new)[pick].clone();
            mutateInPlace(from, paramTypes);
            base = from;
        }

        // 3) 以一定概率把这份加入语料池（简单策略）
        if (rnd.nextDouble() < 0.5) {
            corpus.addFirst(base.clone());
            if (corpus.size() > 128) corpus.removeLast();
        }
        return base;
    }

    private Object[] makeOne(Type[] t) {
        Object[] arr = new Object[t.length];
        for (int i = 0; i < t.length; i++) {
            arr[i] = freshValue(t[i]);
        }
        return arr;
    }

    private void mutateInPlace(Object[] args, Type[] t) {
        for (int i = 0; i < args.length; i++) {
            if (rnd.nextDouble() < 0.6) { // 变异概率
                args[i] = mutateValue(args[i], t[i]);
            }
        }
    }

    private Object mutateValue(Object v, Type t) {
        if (t instanceof Class<?>) {
            Class<?> c = (Class<?>) t;
            if (c == int.class || c == Integer.class) {
                int x = (v == null) ? edgeInt() : (Integer) v;
                int kind = rnd.nextInt(4);
                if (kind == 0) return x + (rnd.nextBoolean() ? 1 : -1);
                if (kind == 1) return -x;
                if (kind == 2) return edgeInt();
                return rnd.nextInt();
            }
            if (c == double.class || c == Double.class) {
                double x = (v == null) ? edgeDbl() : (Double) v;
                int kind = rnd.nextInt(4);
                if (kind == 0) return x + (rnd.nextBoolean() ? 1e-3 : -1e-3);
                if (kind == 1) return -x;
                if (kind == 2) return edgeDbl();
                return rnd.nextDouble() * (rnd.nextBoolean() ? 1e6 : 1.0);
            }
            if (c == float.class || c == Float.class) {
                double d = (v == null) ? edgeDbl() : ((Float) v).doubleValue();
                return (float) mutateValue(d, double.class);
            }
            if (c == boolean.class || c == Boolean.class) {
                return (v == null) ? rnd.nextBoolean() : !((Boolean) v);
            }
            return freshValue(t);
        }
        if (t instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType) t;
            if (List.class.isAssignableFrom((Class<?>) p.getRawType())) {
                @SuppressWarnings("unchecked")
                List<Object> lst = (v instanceof List) ? new ArrayList<>((List<?>) v) : new ArrayList<>();
                Type elemT = p.getActualTypeArguments()[0];
                if (lst.isEmpty()) {
                    int n = 1 + rnd.nextInt(5);
                    for (int i = 0; i < n; i++) lst.add(freshValue(elemT));
                } else {
                    int idx = rnd.nextInt(lst.size());
                    lst.set(idx, mutateValue(lst.get(idx), elemT));
                    if (rnd.nextDouble() < 0.3 && lst.size() < 16) lst.add(freshValue(elemT));
                }
                return lst;
            }
        }
        return freshValue(t);
    }

    private Object freshValue(Type t) {
        if (t instanceof Class<?>) {
            Class<?> c = (Class<?>) t;
            if (c == int.class || c == Integer.class) return edgeOrRandInt();
            if (c == double.class || c == Double.class) return edgeOrRandDbl();
            if (c == float.class || c == Float.class)  return (float) ((double) edgeOrRandDbl());
            if (c == boolean.class || c == Boolean.class) return rnd.nextBoolean();
            return er.nextObject(c);
        }
        if (t instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType) t;
            if (List.class.isAssignableFrom((Class<?>) p.getRawType())) {
                Type elemT = p.getActualTypeArguments()[0];
                int n = 1 + rnd.nextInt(5);
                List<Object> lst = new ArrayList<>(n);
                for (int i = 0; i < n; i++) lst.add(freshValue(elemT));
                return lst;
            }
        }
        return er.nextObject(Object.class);
    }

    private int edgeOrRandInt() {
        return rnd.nextDouble() < 0.5 ? edgeInt() : rnd.nextInt();
    }
    private int edgeInt() { return INT_EDGES[rnd.nextInt(INT_EDGES.length)]; }

    private double edgeOrRandDbl() {
        return rnd.nextDouble() < 0.5 ? edgeDbl() : rnd.nextGaussian() * 10.0;
    }
    private double edgeDbl() { return DBL_EDGES[rnd.nextInt(DBL_EDGES.length)]; }
}
