import java.util.Random;

public class CookSeeZulegerTACAS2013Fig7a {

    public static void loop(int x, int y, int d, int z) {
        while (x > 0 && y > 0 && d > 0) {
            if (z != 0) {
                x = x - 1;
                d = z;
            } else {
                x = z;
                y = y - 1;
                d = d - 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;
        int d = 0;
        int z = 0;
        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();
        if (args.length >= 3) d = args[2].length();
        if (args.length >= 4) z = args[3].length();
        loop(x, y, d, z);
    }
}

