

import java.util.Random;

public class Cohencu3botht {

    public static void loop(int a, int n, int x, int y, int z, int k) {
        while (n * n * n <= k) {
            n = n + 1;
            x = x + y;
            y = y + z;
            z = z + 6;
        }
    }

    public static void main(String[] args) {
        int n = 0;
        int y = 0;

        if (args.length >= 2) {
            n = args[0].length();
            y = args[1].length();
        }

        loop(0, n, 0, 1, 6, args.length);
    }
}

