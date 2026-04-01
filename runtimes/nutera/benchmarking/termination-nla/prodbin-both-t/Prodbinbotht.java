import java.util.Random;

public class Prodbinbotht {

    public static void loop(int a, int b, int x, int y, int z) {
        while (true) {
            if (!(y + z + x * y - a * b != 0))
                break;

            if (y % 2 == 1) {
                z = z + x;
                y = y - 1;
            }
            x = 2 * x;
            y = y / 2;
        }
    }

    public static void main(String[] args) {
        int a = 0;
        int b = 0;

        if (args.length >= 2) {
            a = args[0].length();
            b = args[1].length();
        }

        if (b < 1) {
            return;
        }

        int x = a;
        int y = b;
        int z = 0;

        loop(a, b, x, y, z);
    }
}

