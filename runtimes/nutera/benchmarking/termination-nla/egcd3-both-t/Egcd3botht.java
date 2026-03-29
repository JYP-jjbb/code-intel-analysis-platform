

import java.util.Random;

public class Egcd3botht {

    public static void loop(int a, int b, int p, int q, int r, int s) {
        while (true) {
            if (!(b != 0))
                break;

            int c, k;
            c = a;
            k = 0;

            while (true) {
                if (!(c >= b))
                    break;

                int d, v;
                d = 1;
                v = b;

                while (true) {
                    if (!(c >= 2 * b * d))
                        break;

                    d = 2 * d;
                    v = 2 * v;
                }

                c = c - v;
                k = k + d;
            }

            a = b;
            b = c;

            int temp;
            temp = p;
            p = q;
            q = temp - q * k;
            temp = r;
            r = s;
            s = temp - s * k;
        }
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;

        if (args.length >= 2) {
            x = args[0].length();
            y = args[1].length();
        }

        int a = x;
        int b = y;
        int p = 1;
        int q = 0;
        int r = 0;
        int s = 1;

        loop(a, b, p, q, r, s);
    }
}
