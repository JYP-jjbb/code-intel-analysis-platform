

import java.util.Random;

public class CookSeeZulegerTACAS2013Fig8amodified {

    public static void loop(int k, int x) {
        while (x != k) {
            if (x > k) {
                x = x - 1;
            } else {
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
        loop(x, y);
    }
}

