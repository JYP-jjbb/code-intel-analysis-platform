

import java.util.Random;

public class DivMinus {

    public static void loop(int x, int y) {
        int r;
        r = 0;
        while (x >= y && y > 0) {
            x = x - y;
            r = r + 1;
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
