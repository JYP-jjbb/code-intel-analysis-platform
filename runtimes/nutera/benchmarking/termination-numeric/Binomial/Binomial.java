import java.util.Random;

public class Binomial {
    public static void loop(int x, int y, int z) {
        if (x < 0) {
            return;
        }
        if (y < 0) {
            return;
        }
        for (int n = 0; n <= x; n++) {
            for (int k = 0; k <= x; k++) {
                if (k <= n) {
                    int f1 = 1;
                    int t = n;
                    while (t > 0) {
                        f1 = f1 * t;
                        t = t - 1;
                    }
                    int f2 = 1;
                    t = k;
                    while (t > 0) {
                        f2 = f2 * t;
                        t = t - 1;
                    }
                    int d = n - k;
                    int f3 = 1;
                    t = d;
                    while (t > 0) {
                        f3 = f3 * t;
                        t = t - 1;
                    }
                    int den = f2 * f3;
                    int r = f1 / den;
                } else {
                    int f1 = 1;
                    int t = k;
                    while (t > 0) {
                        f1 = f1 * t;
                        t = t - 1;
                    }
                    int f2 = 1;
                    t = n;
                    while (t > 0) {
                        f2 = f2 * t;
                        t = t - 1;
                    }
                    int d = k - n;
                    int f3 = 1;
                    t = d;
                    while (t > 0) {
                        f3 = f3 * t;
                        t = t - 1;
                    }
                    int den = f2 * f3;
                    int r = f1 / den;
                }
            }
        }
    }

    public static void main(String[] args) {
        if (args.length >= 3) {
            int x = args[0].length();
            int y = args[1].length();
            int z = args[2].length();
            loop(x, y, z);
        }
    }
}
