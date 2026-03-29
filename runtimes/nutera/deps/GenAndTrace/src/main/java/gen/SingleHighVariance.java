package gen;

import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.api.Randomizer;
import org.jeasy.random.randomizers.collection.ListRandomizer;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SingleHighVariance implements InputGenerator {



    final static int HIGH_VARIANCE = 100;
    final static int LOW_VARIANCE = 1;

    final private Random random;
    final private EasyRandom easyRandom;
    private long seed = 0;

    public SingleHighVariance(){
        random = new Random();
        easyRandom = new EasyRandom();

    }

    public SingleHighVariance(long seed){
        this.seed = seed;
        random = new Random(this.seed);

        EasyRandomParameters params = new EasyRandomParameters()
                .seed(seed);

        easyRandom = new EasyRandom(params);

        random.setSeed(seed);
        easyRandom.setSeed(seed);
    }

    private Object createRandomObject(Type t) {

        if (t instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) t;
            return createRandomParameterizedType(pType);
        }
        if (t instanceof GenericArrayType) {
            throw new Error("Unable to generate Arrays. Try Lists");
        }
        if (t instanceof Class) {
            if (t.equals(Boolean.class) || t.equals(boolean.class)) {
                return random.nextBoolean();
            }  else if (t.equals(Double.class)|| t.equals(double.class)) {
                return random.nextDouble();
            } else if (t.equals(Float.class)|| t.equals(float.class)) {
                return random.nextFloat();
            } else if (t.equals(Long.class)|| t.equals(long.class)) {
                return random.nextLong();
            } else if (((Class<?>) t).isArray()){
                return createRandomArray((Class<?>)t);
            } else {
                return easyRandom.nextObject((Class<?>) t);
            }
        }

        return null;
    }

    private static double[][] getAntiCorr(int num, int a){
        double[][] d = new double[num][num];
        for (double[] row: d)
            Arrays.fill(row, 0.0);
        for (int i = 0; i < num; i++) {
            d[i][i] = LOW_VARIANCE;
        }

        d[a][a] = HIGH_VARIANCE;

        return d;
    }

    private Double[] getRandom(int num) {

        // automatically initialised to zero
        double[] means = new double[num];

        int a = random.nextInt(num);

        double[][] covMatrix = getAntiCorr(num, a);

        MultivariateNormalDistribution mvND = new MultivariateNormalDistribution(means, covMatrix);
        mvND.reseedRandomGenerator(seed);
        seed++;
        double[] samples = mvND.sample();

        Double[] result = new Double[num];
        for (int i = 0; i < num; ++i) {
            result[i] = (samples[i]);
        }

        return result;
    }

    private static void addIntoFromAt(Type[] t, Object[] arr, Double[] obj, List<Integer> indices){
        assert obj.length == indices.size();

        for (int i = 0; i < obj.length; ++i) {
            int index = indices.get(i);
            if (t[index].equals(Integer.class) || t[index].equals(int.class))
                arr[index] = (int) Math.round(obj[i]);
            else if (t[index].equals(Double.class) || t[index].equals(double.class))
                arr[index] = obj[i];
            else if (t[index].equals(Float.class) || t[index].equals(float.class))
                arr[index] = obj[i].floatValue();
            else assert false;
        }

    }

    public Object[] nextSampleArguments(Type[] t) {
        Object[] res = new Object[t.length];

        List<Integer> numIndices = IntStream.range(0, t.length)
                .filter(i -> t[i].equals(Integer.class) || t[i].equals(int.class) ||
                        t[i].equals(Double.class) || t[i].equals(double.class) ||
                        t[i].equals(Float.class) || t[i].equals(float.class))
                .boxed()
                .collect(Collectors.toList());
        if (numIndices.size()>0) {
            addIntoFromAt(t, res, getRandom(numIndices.size()), numIndices);
        }

        //add other types
        for (int i = 0; i < res.length; ++i) {
            if (res[i] == null) {
                res[i] = this.createRandomObject(t[i]);
            }
        }

        return res;
    }

    private Object[] createRandomArray(Class<?> t) {
        List<?> lst = createRandomList(t.getComponentType());
        int size = lst.size();

        if (t.equals(String[].class)) { // TODO CHANGE THIS to something better and more generic
            EasyRandom random = new EasyRandom();

            String[] res = new String[size];
            for (int i = 0; i < size; ++i) {
                res[i] = random.nextObject(String.class);
            }
            return res;
        }
        return lst.toArray();
    }

    private Object createRandomParameterizedType(ParameterizedType t) {
        if (t.getRawType().equals(LinkedList.class)) {
            return createRandomLinkedList(t.getActualTypeArguments()[0]);
        } else if (t.getRawType().equals(List.class)) {
            return createRandomList(t.getActualTypeArguments()[0]);
        }
        return null;
    }

    private java.util.LinkedList<?> createRandomLinkedList(Type actualTypeArgument) {
        List<?> createRandomList = createRandomList(actualTypeArgument);
        return new LinkedList<>(createRandomList);
    }

    private java.util.List<?> createRandomList(Type c) {
        Randomizer<?> irnd = (Randomizer<Object>) (() -> createRandomObject(c));

        ListRandomizer<?> rnd= new ListRandomizer<>(irnd);
        return rnd.getRandomValue();
    }

}
