

import java.util.Random;

public class Nyala2lex2 {

    public static void loop(int x, int y, int n) {

        while (x >= 0 && y >= 0) {
            y = y - 1;
            if (y < 0) {
                x = x - 1;
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

