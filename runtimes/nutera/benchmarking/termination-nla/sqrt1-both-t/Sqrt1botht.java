
import java.util.Random;

public class Sqrt1botht {

    public static void loop(int n, int k, int a, int s, int t, int c) {
        while (t * t - 4 * s + 2 * t + 1 + c <= k) {
            a = a + 1;
            t = t + 2;
            s = s + t;
            c = c + 1;
        }
    }

    public static void main(String[] args) {
        int n = 0;
        int k = 0;

        if (args.length >= 2) {
            n = args[0].length();
            k = args[1].length();
        }

        int a = 0;
        int s = 1;
        int t = 1;
        int c = 0;

        loop(n, k, a, s, t, c);
    }
}
