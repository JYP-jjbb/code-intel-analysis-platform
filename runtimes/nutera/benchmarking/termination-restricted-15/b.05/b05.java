

import java.util.Random;

public class b05 {

    public static void loop(int x, int t, int t1) {
        while ((x > 0) && (t < 1073741824) && (-1073741824 < t) && (x == 2 * t)) {
            x = x - 1;
            t = t1;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 3) {
            int x = args[0].length();
            int t = args[1].length();
            int t1 = args[2].length();
            loop(x, t, t1);
        }
    }
}

