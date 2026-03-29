package gen;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.api.Randomizer;
import org.jeasy.random.randomizers.collection.ListRandomizer;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;


public class GaussianIntegersER implements InputGenerator{

    final static int SCALE = 100; // Not exact since we are working with normal distribution

    final private NormalDistribution random;
    final private EasyRandom easyRandom;

    public GaussianIntegersER(){
        random = new NormalDistribution();
        easyRandom = new EasyRandom();
    }

    public GaussianIntegersER(long seed){
        random = new NormalDistribution();

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
            if (t.equals(Integer.class) || t.equals(int.class))  {
                return Math.round((float) (random.sample() * SCALE));
            } else if (t.equals(Boolean.class) || t.equals(boolean.class)) {
                return random.sample() >= 0.0;
            }  else if (t.equals(Double.class)|| t.equals(double.class)) {
                return (random.sample() * SCALE);
            } else if (t.equals(Float.class)|| t.equals(float.class)) {
                return (float) (random.sample() * SCALE);
            } else if (t.equals(Long.class)|| t.equals(long.class)) {
                return  Math.round((random.sample() * SCALE));
            } else if (((Class<?>) t).isArray()){
                return createRandomArray((Class<?>)t);
            } else {
                return easyRandom.nextObject((Class<?>) t);
            }
        }

        return null;
    }

    public Object[] nextSampleArguments(Type[] t) {
        return Arrays.stream(t).map(this::createRandomObject).toArray();
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
