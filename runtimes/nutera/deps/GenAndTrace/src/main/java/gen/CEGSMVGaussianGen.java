package gen;

import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.api.Randomizer;
import org.jeasy.random.randomizers.collection.ListRandomizer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CEGSMVGaussianGen implements InputGenerator{

    private final JSONObject obj;

    final private Random random;
    final private EasyRandom easyRandom;
    private long seed = 0;

    public CEGSMVGaussianGen(String fileName) throws IOException {
        String jsonString = Files.readString(Path.of(fileName));
        obj = new JSONObject(jsonString);

        EasyRandomParameters params = new EasyRandomParameters();
        easyRandom = new EasyRandom(params);
        random = new Random();

    }

    public CEGSMVGaussianGen(String fileName, long seed) throws IOException {
        this.seed = seed;
        random = new Random(this.seed);

        EasyRandomParameters params = new EasyRandomParameters()
                .seed(seed);

        easyRandom = new EasyRandom(params);

        random.setSeed(seed);
        easyRandom.setSeed(seed);
        String jsonString = Files.readString(Path.of(fileName));
        obj = new JSONObject(jsonString);

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

    private double[] gerMeans(int num){

        double[] means = new double[num];

        JSONArray arr = obj.getJSONArray("arguments");
        Object[] res = new Object[arr.length()];

        for (int i = 0; i < arr.length(); i++){
            Object x = arr.get(i);
            res[i] = x;
            //if (x instanceof Integer) {...}
        }

        for (int i = 0; i < num; ++i) {
            means[i] = (Integer) res[i];
        }
        return means;
    }

    private Double[] getRandom(int num) {

        double[][] unit = new double[num][num];
        for (double[] row: unit)
            Arrays.fill(row, 0.0);
        for (int i = 0; i < num; i++) {
            unit[i][i] = 1.0;
        }

        // automatically initialised to zero
        double[] means = gerMeans(num);

        //int a = random.nextInt(num);
        //int b = random.nextInt(num);

        // double[][] covMatrix = getAntiCorr(num, a, b);

        MultivariateNormalDistribution mvND = new MultivariateNormalDistribution(means, unit);
        mvND.reseedRandomGenerator(seed);
        seed++;
        double[] samples = mvND.sample();

        Double[] result = new Double[num];
        for (int i = 0; i < num; ++i) {
            result[i] = (samples[i]);
        }

        return result;
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

    private Object createRandomParameterizedType(ParameterizedType t) {
        if (t.getRawType().equals(LinkedList.class)) {
            return createRandomLinkedList(t.getActualTypeArguments()[0]);
        } else if (t.getRawType().equals(List.class)) {
            return createRandomList(t.getActualTypeArguments()[0]);
        }
        return null;
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

    @Override
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
}
