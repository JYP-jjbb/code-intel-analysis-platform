

import java.util.Random;

public class MinusUserDefined {

    public static void loop(int x, int y) {
        int r;
        r = 0;

        while (x > 0 && y > 0) {
            x = x - 1;
            y = y - 1;
        }

        while (x > 0) {
            y = y + 1;
            r = r + 1;
            while (x > 0 && y > 0) {
                x = x - 1;
                y = y - 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 2) {
            int x = args[0].length();
            int y = args[1].length();
            loop(x, y);
        }
    }
}
