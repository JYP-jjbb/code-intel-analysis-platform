

import java.util.Random;

public class PastaB7 {

    public static void loop(int x, int y, int z) {
        while (x > z && y > z) {
            x = x - 1;
            y = y - 1;
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

