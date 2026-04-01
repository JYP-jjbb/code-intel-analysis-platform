import java.util.Random;

public class HarrisLalNoriRajamaniSAS2010Fig1 {

    public static void loop(int a, int x, int y, int k, int b, int c) {
        int d;
        int z;

        if (a != 0) {
            d = 1;
        } else {
            d = 2;
        }

        z = 1;
        if (k > 1073741823) {
            return;
        }

        while (z < k) {
            z = 2 * z;
        }

        while (x > 0 && y > 0) {
            if (b != 0) {
                x = x - d;
                y = c;
                z = z - 1;
            } else {
                y = y - d;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int a = 0;
        int x = 0;
        int y = 0;
        int k = 0;
        int b = 0;
        int c = 0;

        if (args.length >= 1) a = args[0].length();
        if (args.length >= 2) x = args[1].length();
        if (args.length >= 3) y = args[2].length();
        if (args.length >= 4) k = args[3].length();
        if (args.length >= 5) b = args[4].length();
        if (args.length >= 6) c = args[5].length();

        loop(a, x, y, k, b, c);
    }
}
