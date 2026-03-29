

import java.util.Random;

public class ToulouseMultiBranchesToLoop2 {

    public static void loop(int y, int z, int n) {
        int x;
        int t;

        if (!(-268435455 <= y)) return;
        if (!(-268435455 <= z)) return;

        t = n;
        n = n + 1;
        if (t != 0) {
            x = 1;
        } else {
            x = -1;
        }

        if (x > 0) {
            x = x + 1;
        } else {
            x = x - 1;
        }
        if (x > 0) {
            x = x + 1;
        } else {
            x = x - 1;
        }
        if (x > 0) {
            x = x + 1;
        } else {
            x = x - 1;
        }
        if (x > 0) {
            x = x + 1;
        } else {
            x = x - 1;
        }
        if (x > 0) {
            x = x + 1;
        } else {
            x = x - 1;
        }
        if (x > 0) {
            x = x + 1;
        } else {
            x = x - 1;
        }
        if (x > 0) {
            x = x + 1;
        } else {
            x = x - 1;
        }
        if (x > 0) {
            x = x + 1;
        } else {
            x = x - 1;
        }

        while (y < 100 && z < 100) {
            y = y + x;
            z = z - x;
        }
        return;
    }

    public static void main(String[] args) {
        int y = 0;
        int z = 0;
        int n = 0;

        if (args.length >= 1) y = args[0].length();
        if (args.length >= 2) z = args[1].length();
        if (args.length >= 3) n = args[2].length();

        loop(y, z, n);
    }
}

