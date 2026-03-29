

import java.util.Random;

public class c02 {

    public static void loop(int x, int y) {
        int c;
        c = 0;
        while (x >= 0 && x < 2147483647) {
            x = x + 1;
            y = 1;
            while (x > y) {
                y = y + 1;
            }
            x = x - 2;
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

