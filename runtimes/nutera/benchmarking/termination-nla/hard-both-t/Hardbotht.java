

import java.util.Random;

public class Hardbotht {

    public static void loop(int a, int b, int r, int d, int p, int q) {
        while (true) {
            if (!(r >= b * p)) break;

            d = 2 * d;
            p = 2 * p;
        }

        while (true) {
            if (!(a - q * b - r + p != 1)) break;

            d = d / 2;
            p = p / 2;
            if (r >= d) {
                r = r - d;
                q = q + p;
            }
        }
    }

    public static void main(String[] args) {
        int a = 0;
        int b = 0;

        if (args.length >= 2) {
            a = args[0].length();
            b = args[1].length();
        }

        if (b < 1) return;

        int r = a;
        int d = b;
        int p = 1;
        int q = 0;

        loop(a, b, r, d, p, q);
    }
}
