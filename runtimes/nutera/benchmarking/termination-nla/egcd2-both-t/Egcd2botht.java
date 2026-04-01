import java.util.Random;

public class Egcd2botht {

    public static void loop(int a, int b, int p, int q, int r, int s, int c, int k) {
        while (true) {
            if (!(b != 0)) {
                break;
            }
            c = a;
            k = 0;

            while (c >= a * q + b * s) {
                c = c - b;
                k = k + 1;
            }

            a = b;
            b = c;

            int temp;
            temp = p;
            p = q;
            q = temp - q * k;
            temp = r;
            r = s;
            s = temp - s * k;
        }
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;

        if (args.length >= 2) {
            x = args[0].length();
            y = args[1].length();
        }

        if (x < 1 || y < 1) {
            return;
        }

        int a = x;
        int b = y;
        int p = 1;
        int q = 0;
        int r = 0;
        int s = 1;
        int c = 0;
        int k = 0;

        loop(a, b, p, q, r, s, c, k);
    }
}
