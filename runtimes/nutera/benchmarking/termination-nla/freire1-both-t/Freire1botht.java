import java.util.Random;

public class Freire1botht {

    public static void loop(int a, int x, int r, int c, int k) {
        while (r * r - a - r + 2 * x + c <= k) {
            x = x - r;
            r = r + 1;
        }
    }

    public static void main(String[] args) {
        int a = 0;
        int x = 0;
        int r = 0;
        int c = 0;
        int k = 0;

        if (args.length >= 1) {
            a = args[0].length();
        }
        if (args.length >= 2) {
            k = args[1].length();
        }

        x = a / 2;

        loop(a, x, r, c, k);
    }
}
