import java.util.Random;

public class Cohencu6botht {

    public static void loop(int a, int n, int x, int y, int z) {
        while (n <= a) {
            n = n + y - 3 * n * n - 3 * n;
            x = x + y;
            y = y + z;
            z = z + 6;
        }
    }

    public static void main(String[] args) {
        int n = 0;
        int y = 0;

        if (args.length >= 2) {
            n = args[0].length();
            y = args[1].length();
        }

        loop(0, n, 0, 1, 6);
    }
}
