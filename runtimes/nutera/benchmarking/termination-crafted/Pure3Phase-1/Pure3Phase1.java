

import java.util.Random;

public class Pure3Phase1 {

    public static void loop(int x, int y, int z, int n) {

        if (!(x <= 65535)) return;
        if (!(-65535 <= y && y <= 65535)) return;
        if (!(-65535 <= z && z <= 65535)) return;

        while (x >= 0) {
            int t = n;
            n = n + 1;

            if (t != 0) {
                x = x + y;
            } else {
                x = x + z;
            }
            y = y + z;
            z = z - 1;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;
        int z = 0;
        int n = 0;

        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();
        if (args.length >= 3) z = args[2].length();
        if (args.length >= 4) n = args[3].length();

        loop(x, y, z, n);
    }
}

