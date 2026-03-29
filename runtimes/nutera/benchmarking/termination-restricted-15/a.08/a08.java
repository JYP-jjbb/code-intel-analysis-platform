

import java.util.Random;

public class a08 {

    public static void loop(int x, int y) {
        int c;
        c = 0;
        while (x > y && x < 2147483647) {
            x = x + 1;
            y = y + 2;
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

