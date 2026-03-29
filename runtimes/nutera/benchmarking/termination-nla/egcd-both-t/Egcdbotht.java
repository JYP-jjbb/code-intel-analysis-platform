

import java.util.Random;

public class Egcdbotht {

    public static void loop(int a, int b, int p, int q, int r, int s, int x, int y) {
        while (y * r + x * p != x * q + y * s) {
            if (a > b) {
                a = a - b;
                p = p - q;
                r = r - s;
            } else {
                b = b - a;
                q = q - p;
                s = s - r;
            }
        }
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;
        if (args.length >= 2) {
            x = args[0].length();
            y = args[1].length();
        }

        if (x >= 1 && y >= 1) {
            int a = x;
            int b = y;
            int p = 1;
            int q = 0;
            int r = 0;
            int s = 1;

            loop(a, b, p, q, r, s, x, y);
        }
    }
}
