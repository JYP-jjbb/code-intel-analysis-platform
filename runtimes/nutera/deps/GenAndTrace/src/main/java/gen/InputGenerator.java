package gen;

import java.lang.reflect.Type;
import java.util.List;

public interface InputGenerator {

    public Object[] nextSampleArguments(Type[] t);

}
