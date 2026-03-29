

import java.util.Random;

public class b04 {

    public static void loop(int x, int y, int t) {
        while (x > y) {
            t = x;
            x = y;
            y = t;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 3) {
            int x = args[0].length();
            int y = args[1].length();
            int t = args[2].length();
            loop(x, y, t);
        }
    }
}
