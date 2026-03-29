package gen;

import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.api.Randomizer;
import org.jeasy.random.randomizers.collection.ListRandomizer;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class MultivariateGaussian implements InputGenerator{

    final static int VARIANCE = 1000; // Not exact since we are working with normal distribution
    final static int HIGH_VARIANCE = 100; // Not exact since we are working with normal distribution
    final static int LOW_VARIANCE = 25; // Not exact since we are working with normal distribution

    final private Random random;
    final private EasyRandom easyRandom;
    private long seed = 0;

    public MultivariateGaussian(){
        random = new Random();
        easyRandom = new EasyRandom();

    }

    public MultivariateGaussian(long seed){
        random = new Random();
        this.seed = seed;

        EasyRandomParameters params = new EasyRandomParameters()
                .seed(seed);

        easyRandom = new EasyRandom(params);
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

    private static double[][] getUnitMatrix(int num, int lowVarPos){
        double[][] d = new double[num][num];
        for (int i = 0; i < num; i++) {
            for (int j = 0; j < num; j++) {
                if (i == j) {
                    d[i][j] = HIGH_VARIANCE;
                } else {
                    d[i][j] = 0.0;
                }
            }
        }

        d[lowVarPos][lowVarPos] = LOW_VARIANCE;

        return d;
    }

    private Integer[] getRandomIntegers(int num) {

        // automatically initialised to zero
        double[] means = new double[num];

        double[][] covMatrix = getUnitMatrix(num, this.random.nextInt(num));

        MultivariateNormalDistribution mvND = new MultivariateNormalDistribution(means, covMatrix);
        mvND.reseedRandomGenerator(seed);
        seed++;
        double[] samples = mvND.sample();

        Integer[] result = new Integer[num];
        for (int i = 0; i < num; ++i) {
            result[i] = Math.round( (float) (samples[i]));
        }

        return result;
    }

    private Double[] getRandomDoubles(int num) {

        // automatically initialised to zero
        double[] means = new double[num];
        double[][] covMatrix = getUnitMatrix(num, this.random.nextInt(num));

        MultivariateNormalDistribution mvND = new MultivariateNormalDistribution(means, covMatrix);
        mvND.reseedRandomGenerator(seed);
        seed++;

        double[] samples = mvND.sample();

        Double[] result = new Double[num];
        for (int i = 0; i < num; ++i) {
            result[i] = samples[i];
        }

        return result;
    }

    private Float[] getRandomFloats(int num) {

        // automatically initialised to zero
        double[] means = new double[num];
        double[][] covMatrix = getUnitMatrix(num, this.random.nextInt(num));

        MultivariateNormalDistribution mvND = new MultivariateNormalDistribution(means, covMatrix);
        mvND.reseedRandomGenerator(seed);
        seed++;

        double[] samples = mvND.sample();

        Float[] result = new Float[num];
        for (int i = 0; i < num; ++i) {
            result[i] = (float) (samples[i]);
        }

        return result;
    }


    private static void addIntoFromAt(Object[] arr, Object[] obj, List<Integer> indices){
        assert obj.length == indices.size();

        for (int i = 0; i < obj.length; ++i) {
            arr[indices.get(i)] = obj[i];
        }

    }

    public Object[] nextSampleArguments(Type[] t) {
        Object[] res = new Object[t.length];

        List<Integer> intIndices = IntStream.range(0, t.length)
                .filter(i -> t[i].equals(Integer.class) || t[i].equals(int.class))
                .boxed()
                .collect(Collectors.toList());
        if (intIndices.size()>0) {
            addIntoFromAt(res, getRandomIntegers(intIndices.size()), intIndices);
        }

        List<Integer> doubleIndices = IntStream.range(0, t.length)
                .filter(i -> t[i].equals(Double.class) || t[i].equals(double.class))
                .boxed()
                .collect(Collectors.toList());
        if (doubleIndices.size() > 0) {
            addIntoFromAt(res, getRandomDoubles(doubleIndices.size()), doubleIndices);
        }

        List<Integer> floatIndices = IntStream.range(0, t.length)
                .filter(i -> t[i].equals(Float.class) || t[i].equals(float.class))
                .boxed()
                .collect(Collectors.toList());
        if (floatIndices.size() > 0) {
            addIntoFromAt(res, getRandomFloats(floatIndices.size()), floatIndices);
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
