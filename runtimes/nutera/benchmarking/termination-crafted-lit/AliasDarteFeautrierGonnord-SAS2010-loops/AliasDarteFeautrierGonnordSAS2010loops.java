

import java.util.Random;

public class AliasDarteFeautrierGonnordSAS2010loops {

    public static void loop(int x, int y, int n) {
        x = n;
        if (x >= 0 && x <= 1073741823) {
            while (x >= 0) {
                y = 1;
                if (y < x) {
                    while (y < x) {
                        y = 2 * y;
                    }
                }
                x = x - 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int n = 0;
        int y = 0;

        if (args.length >= 2) {
            n = args[0].length();
            y = args[1].length();
        }

        loop(0, 0, n);
    }
}
