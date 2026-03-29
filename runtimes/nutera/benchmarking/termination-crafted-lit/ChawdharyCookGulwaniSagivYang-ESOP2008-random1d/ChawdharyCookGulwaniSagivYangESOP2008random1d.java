

import java.util.Random;

public class ChawdharyCookGulwaniSagivYangESOP2008random1d {

    public static void loop(int a, int x, int m, int n) {
        if (m > 0) {
            a = 0;
            x = 1;
            while (x <= m) {
                if (n != 0) {
                    a = a + 1;
                } else {
                    a = a - 1;
                }
                x = x + 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;
        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();
        loop(0, 0, x, y);
    }
}

