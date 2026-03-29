

import java.util.Random;

public class b03noinvassume {

    public static void loop(int x, int y) {
        if (x > 0) {
            while (x > y && y <= 2147483647 - x) {
                y = y + x;
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

