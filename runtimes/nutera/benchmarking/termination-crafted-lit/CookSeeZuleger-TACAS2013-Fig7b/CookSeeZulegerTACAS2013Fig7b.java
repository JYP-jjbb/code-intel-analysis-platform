import java.util.Random;

public class CookSeeZulegerTACAS2013Fig7b {

    public static void loop(int x, int y, int z, int a, int b, int c, int d) {
        while (x > 0 && y > 0 && z > 0) {
            if (a != 0) {
                x = x - 1;
            } else {
                if (b != 0) {
                    y = y - 1;
                    z = c;
                } else {
                    z = z - 1;
                    x = d;
                }
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;
        int z = 0;
        int a = 0;
        int b = 0;
        int c = 0;
        int d = 0;

        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();
        if (args.length >= 3) z = args[2].length();
        if (args.length >= 4) a = args[3].length();
        if (args.length >= 5) b = args[4].length();
        if (args.length >= 6) c = args[5].length();
        if (args.length >= 7) d = args[6].length();

        loop(x, y, z, a, b, c, d);
    }
}
