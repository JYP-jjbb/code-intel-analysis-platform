package gen;
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
import java.lang.reflect.TypeVariable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class CEGSGenerator implements InputGenerator{

    private final JSONObject obj;

    public CEGSGenerator(String fileName) throws IOException {
        String jsonString = Files.readString(Path.of(fileName));
        obj = new JSONObject(jsonString);

    }

    public Object[] nextSampleArguments(Type[] t) {

        JSONArray arr = obj.getJSONArray("arguments");
        Object[] res = new Object[arr.length()];

        for (int i = 0; i < arr.length(); i++){
            Object x = arr.get(i);
            res[i] = x;
            //if (x instanceof Integer) {...}
        }

        return res;
    }

}