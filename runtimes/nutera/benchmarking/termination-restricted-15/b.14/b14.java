

import java.util.Random;

public class b14 {

    public static void loop(int x, int y) {
        int c;
        c = 0;
        while ((x == y) && (x > 0)) {
            while (y > 0) {
                x = x - 1;
                y = y - 1;
                c = c + 1;
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
