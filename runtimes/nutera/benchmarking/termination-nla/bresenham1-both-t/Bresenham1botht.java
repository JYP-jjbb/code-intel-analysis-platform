
import java.util.Random;

public class Bresenham1botht {

    public static void loop(int X, int Y, int v, int x, int y, int c, int k) {
        while (2 * Y * x - 2 * X * y - X + 2 * Y - v + c <= k) {
            if (v < 0) {
                v = v + 2 * Y;
            } else {
                v = v + 2 * (Y - X);
                y++;
            }
            x++;
            c++;
        }
    }

    public static void main(String[] args) {
        int n = 0;
        int y = 0;

        if (args.length >= 2) {
            n = args[0].length();
            y = args[1].length();
        }

        loop(0, n, 2 * y - n, 0, 0, 0, args.length);
    }
}
