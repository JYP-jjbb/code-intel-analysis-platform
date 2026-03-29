

import java.util.Random;

public class TelAvivAmirMinimum {

    public static void loop(int x, int y, int n) {
        int t;

        while (x > 0 && y > 0) {
            t = n;
            n = n + 1;

            if (t != 0) {
                if (x < y) {
                    y = x - 1;
                } else {
                    y = y - 1;
                }
                x = n;
                n = n + 1;
            } else {
                if (x < y) {
                    x = x - 1;
                } else {
                    x = y - 1;
                }
                y = n;
                n = n + 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;
        int n = 0;

        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();
        if (args.length >= 3) n = args[2].length();

        loop(x, y, n);
    }
}
