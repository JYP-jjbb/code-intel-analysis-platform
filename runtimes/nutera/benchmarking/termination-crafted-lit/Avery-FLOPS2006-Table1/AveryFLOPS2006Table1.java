

import java.util.Random;

public class AveryFLOPS2006Table1 {

    public static void loop(int x, int y) {
        int z, i;

        z = 0;
        i = x;
        if (y > 0 && x > 0) {
            while (i > 0) {
                i = i - 1;
                z = z + 1;
            }
            while (i < y) {
                i = i + 1;
                z = z - 1;
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

