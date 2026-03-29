

import java.util.Random;

public class PodelskiRybalchenkoLICS2004Fig1 {

    public static void loop(int x) {
        int y;

        while (x >= 0 && x <= 1073741823) {
            y = 1;
            while (y < x) {
                y = 2 * y;
            }
            x = x - 1;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        if (args.length >= 1) x = args[0].length();
        loop(x);
    }
}

