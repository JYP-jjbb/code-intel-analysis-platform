

import java.util.Random;

public class UrbanMineESOP2014Fig3 {

    public static void loop(int x, int y, int a, int b, int c, int d) {
        while (x != 0 && y > 0) {
            if (x > 0) {
                if (a != 0) {
                    x = x - 1;
                    y = b;
                } else {
                    y = y - 1;
                }
            } else {
                if (c != 0) {
                    x = x + 1;
                } else {
                    y = y - 1;
                    x = d;
                }
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;
        int a = 0;
        int b = 0;
        int c = 0;
        int d = 0;

        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();
        if (args.length >= 3) a = args[2].length();
        if (args.length >= 4) b = args[3].length();
        if (args.length >= 5) c = args[4].length();
        if (args.length >= 6) d = args[5].length();

        loop(x, y, a, b, c, d);
    }
}

