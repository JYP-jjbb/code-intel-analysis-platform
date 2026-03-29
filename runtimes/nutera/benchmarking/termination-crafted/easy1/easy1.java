

import java.util.Random;

public class easy1 {

    public static void loop(int z) {
        int x;
        int y;

        x = 0;
        y = 100;
        while (x < 40) {
            if (z == 0) {
                x = x + 1;
            } else {
                x = x + 2;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int z = 0;
        if (args.length >= 1) z = args[0].length();
        loop(z);
    }
}
