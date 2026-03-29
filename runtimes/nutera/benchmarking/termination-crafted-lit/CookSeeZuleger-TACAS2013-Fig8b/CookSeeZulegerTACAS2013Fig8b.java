

import java.util.Random;

public class CookSeeZulegerTACAS2013Fig8b {

    public static void loop(int x, int m) {
        if (m > 0) {
            while (x != m) {
                if (x > m) {
                    x = 0;
                } else {
                    x = x + 1;
                }
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;
        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();
        loop(x, y);
    }
}
