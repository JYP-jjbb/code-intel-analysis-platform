import java.util.Random;

public class Prod4brbotht {

    public static void loop(int x, int y, int a, int b, int p, int q) {
        while (true) {

            if (!(a != 0 && b + q + a * b * p - x * y != 0))
                break;

            if (a % 2 == 0 && b % 2 == 0) {
                a = a / 2;
                b = b / 2;
                p = 4 * p;
            } else if (a % 2 == 1 && b % 2 == 0) {
                a = a - 1;
                q = q + b * p;
            } else if (a % 2 == 0 && b % 2 == 1) {
                b = b - 1;
                q = q + a * p;
            } else {
                a = a - 1;
                b = b - 1;
                q = q + (a + b + 1) * p;
            }
        }
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;

        if (args.length >= 2) {
            x = args[0].length();
            y = args[1].length();
        }

        if (y < 1) {
            return;
        }

        int a = x;
        int b = y;
        int p = 1;
        int q = 0;

        loop(x, y, a, b, p, q);
    }
}

