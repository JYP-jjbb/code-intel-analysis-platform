

import java.util.Random;

public class Geo3botht {

    public static void loop(int z, int a, int k, int x, int y, int c) {
        while (true) {
            if (!(z * x - x + a - a * z * y + c < k))
                break;

            c = c + 1;
            x = x * z + a;
            y = y * z;
        }
    }

    public static void main(String[] args) {
        int z = 0;
        int k = 0;

        if (args.length >= 2) {
            z = args[0].length();
            k = args[1].length();
        }

        int a = 0;
        int x = a;
        int y = 1;
        int c = 1;

        loop(z, a, k, x, y, c);
    }
}
