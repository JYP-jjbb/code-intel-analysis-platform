import java.util.Random;

public class CookSeeZulegerTACAS2013Fig1 {

    public static void loop(int x, int y, int z) {
        while (x > 0 && y > 0) {
            if (z != 0) {
                x = x - 1;
            } else {
                x = z;
                y = y - 1;
            }
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

