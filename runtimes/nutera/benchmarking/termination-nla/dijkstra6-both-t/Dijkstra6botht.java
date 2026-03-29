
import java.util.Random;

public class Dijkstra6botht {

    public static void loop(int n, int p, int q, int r, int h, int c, int k) {
        while (q <= n) {
            q = 4 * q;
        }

        while (p * p - n * q + q * r + c <= k) {
            q = q / 4;
            h = p + q;
            p = p / 2;
            if (r >= h) {
                p = p + q;
                r = r - h;
            }
            c++;
        }
    }

    public static void main(String[] args) {
        int n = 0;
        int p = 0;
        int q = 1;
        int r = n;
        int h = 0;
        int c = 0;
        int k = 0;

        if (args.length >= 1) {
            n = args[0].length();
        }
        if (args.length >= 2) {
            k = args[1].length();
        }

        loop(n, p, q, r, h, c, k);
    }
}
