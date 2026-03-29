

import java.util.Random;

public class Piecewise {

    public static void loop(int q, int p, int n) {

        while (q > 0 && p > 0 && p != q) {
            if (q < p) {
                q = q - 1;
                p = n;
                n = n + 1;
            } else {
                if (p < q) {
                    p = p - 1;
                    q = n;
                    n = n + 1;
                }
            }
        }
        return;
    }

    public static void main(String[] args) {
        int q = 0;
        int p = 0;
        int n = 0;

        if (args.length >= 1) q = args[0].length();
        if (args.length >= 2) p = args[1].length();
        if (args.length >= 3) n = args[2].length();

        loop(q, p, n);
    }
}

