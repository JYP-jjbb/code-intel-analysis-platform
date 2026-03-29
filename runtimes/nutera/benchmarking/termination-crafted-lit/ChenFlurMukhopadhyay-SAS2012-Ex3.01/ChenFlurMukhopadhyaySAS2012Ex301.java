

import java.util.Random;

public class ChenFlurMukhopadhyaySAS2012Ex301 {

    public static void loop(int x, int y, int z) {
        while (x < y) {
            x = x + 1;
            y = z;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;
        int z = 0;
        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();
        if (args.length >= 3) z = args[2].length();
        loop(x, y, z);
    }
}

