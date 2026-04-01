import java.util.Random;

public class Gothenburgv21 {

    public static void loop(int a, int b, int x, int y) {

        if (!(-268435455 <= a && a <= 268435455)) return;
        if (!(-268435455 <= b && b <= 268435455)) return;
        if (!(-268435455 <= x && x <= 268435455)) return;
        if (!(-268435455 <= y && y <= 268435455)) return;

        if (a == b + 1 && x < 0) {
            while (x >= 0 || y >= 0) {
                x = x + a - b - 1;
                y = y + b - a - 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int a = 0;
        int b = 0;
        int x = 0;
        int y = 0;

        if (args.length >= 1) a = args[0].length();
        if (args.length >= 2) b = args[1].length();
        if (args.length >= 3) x = args[2].length();
        if (args.length >= 4) y = args[3].length();

        loop(a, b, x, y);
    }
}

