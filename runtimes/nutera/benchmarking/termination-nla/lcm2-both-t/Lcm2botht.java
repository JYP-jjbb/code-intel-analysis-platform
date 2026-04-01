import java.util.Random;

public class Lcm2botht {

    public static void loop(int a, int b, int x, int y, int u, int v) {
        while (true) {
            if (!(x != y + x * u + y * v - 2 * a * b))
                break;

            if (x > y) {
                x = x - y;
                v = v + u;
            } else {
                y = y - x;
                u = u + v;
            }
        }
    }

    public static void main(String[] args) {
        int a = 0;
        int b = 0;

        if (args.length >= 2) {
            a = args[0].length();
            b = args[1].length();
        }

        if (a < 1 || b < 1 || a > 65535 || b > 65535) {
            return;
        }

        int x = a;
        int y = b;
        int u = b;
        int v = a;

        loop(a, b, x, y, u, v);
    }
}

