
import java.util.Random;

public class Dijkstra1botht {

    public static void loop(int n, int p, int q, int r, int h) {
        while (q <= n) {
            q = 4 * q;
        }

        while (r >= 2 * p + q) {
            q = q / 4;
            h = p + q;
            p = p / 2;
            if (r >= h) {
                p = p + q;
                r = r - h;
            }
        }
    }

    public static void main(String[] args) {
        int n = 0;
        int p = 0;
        int q = 1;
        int r = n;
        int h = 0;

        if (args.length >= 1) {
            n = args[0].length();
        }

        loop(n, p, q, r, h);
    }
}

