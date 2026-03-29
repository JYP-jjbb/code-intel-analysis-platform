

import java.util.Random;

public class b03assume {

    public static void loop(int x, int y) {
        while (x > 0 && x > y && y <= 2147483647 - x) {
            y = y + x;
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

