

import java.util.Random;

public class PastaC9 {

    public static void loop(int x, int y, int r, int r1) {
        while (x > 0 && y > 0) {
            r = r1;
            if (r < 42) {
                x = x - 1;
                r = r1;
                y = r;
            } else {
                y = y - 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 4) {
            int x = args[0].length();
            int y = args[1].length();
            int r = args[2].length();
            int r1 = args[3].length();
            loop(x, y, r, r1);
        }
    }
}
