import java.util.Random;

public class Mannadivbotht {

    public static void loop(int x, int y, int a, int b, int c) {
        while (true) {
            if (!(c != a * y + b + c - x)) break;

            if (b + 1 == y) {
                a = a + 1;
                b = 0;
                c = c - 1;
            } else {
                b = b + 1;
                c = c - 1;
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

        if (x < 0 || y == 0) {
            return;
        }

        int a = 0;
        int b = 0;
        int c = x;

        loop(x, y, a, b, c);
    }
}

