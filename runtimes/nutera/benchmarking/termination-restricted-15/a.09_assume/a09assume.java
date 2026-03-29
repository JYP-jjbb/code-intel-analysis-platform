

import java.util.Random;

public class a09assume {

    public static void loop(int x, int y, int z) {
        while (y > 0 && x >= z && z <= 2147483647 - y) {
            z = z + y;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 3) {
            int x = args[0].length();
            int y = args[1].length();
            int z = args[2].length();
            loop(x, y, z);
        }
    }
}

