

import java.util.Random;

public class PastaC3 {

    public static void loop(int x, int y, int z) {
        while (x < y) {
            if (x < z) {
                x = x + 1;
            } else {
                z = z + 1;
            }
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

