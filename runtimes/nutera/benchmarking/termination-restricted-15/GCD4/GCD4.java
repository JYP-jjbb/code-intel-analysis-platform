import java.util.Random;

public class GCD4 {

    public static void loop(int x, int y) {
        int t;
        int x1;
        while (y > 0 && x > 0) {
            t = y;
            x1 = x;

            while (x1 >= y && y > 0) {
                x1 = x1 - y;
            }

            y = x1;
            x = t;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 2) {
            int x = args[0].length();
            int y = args[1].length();
            loop(x, y);
        }
    }
}
