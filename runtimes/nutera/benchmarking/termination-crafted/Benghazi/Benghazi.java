
import java.util.Random;

public class Benghazi {

    public static void loop(int x) {
        int d;
        int e;
        int o;

        d = 73;
        e = 74;
        while (x >= 0) {
            x = x - d;
            o = d;
            d = e + 1;
            e = o + 1;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        if (args.length >= 1) x = args[0].length();
        loop(x);
    }
}
