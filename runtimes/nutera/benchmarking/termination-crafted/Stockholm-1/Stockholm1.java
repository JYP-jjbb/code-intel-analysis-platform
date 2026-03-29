

import java.util.Random;

public class Stockholm1 {

    public static void loop(int x, int a, int b) {

        if (!(-268435455 <= x && x <= 268435455)) return;
        if (!(-268435455 <= a && a <= 268435455)) return;
        if (!(-268435455 <= b && b <= 268435455)) return;

        if (a == b) {
            while (x >= 0) {
                x = x + a - b - 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int a = 0;
        int b = 0;

        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) a = args[1].length();
        if (args.length >= 3) b = args[2].length();

        loop(x, a, b);
    }
}

