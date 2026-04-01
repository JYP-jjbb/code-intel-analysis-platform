import java.util.Random;

public class Ps2botht {

    public static void loop(int k, int y, int x, int c) {
        while (true) {
            if (!(c + (y * y) - 2 * x + y < k))
                break;

            c = c + 1;
            y = y + 1;
            x = y + x;
        }
    }

    public static void main(String[] args) {
        int k = 0;
        int y = 0;

        if (args.length >= 2) {
            k = args[0].length();
            y = args[1].length();
        }

        int x = 0;
        int c = 0;

        loop(k, y, x, c);
    }
}

