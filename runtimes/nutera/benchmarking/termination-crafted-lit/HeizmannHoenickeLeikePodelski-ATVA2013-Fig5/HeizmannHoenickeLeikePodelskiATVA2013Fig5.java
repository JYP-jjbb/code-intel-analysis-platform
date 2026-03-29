

import java.util.Random;

public class HeizmannHoenickeLeikePodelskiATVA2013Fig5 {

    public static void loop(int x) {
        int y;

        y = 2;
        while (x >= 0) {
            x = x - y;
            y = (y + 1) / 2;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        if (args.length >= 1) x = args[0].length();
        loop(x);
    }
}
