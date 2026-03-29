

import java.util.Random;

public class Log {

    public static void loop(int x) {
        int x1;
        int r;
        int r1;
        r = 0;

        while (x > 1) {
            x1 = x;
            r1 = 0;

            while (x1 > 1) {
                x1 = x1 - 2;
                r1 = r1 + 1;
            }

            x = x1;
            r = r + 1;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 1) {
            int x = args[0].length();
            loop(x);
        }
    }
}

