import java.util.Random;

public class Fermat1botht {

    public static void loop(int A, int R, int u, int v, int r, int cc, int kk) {
        while (u * u - v * v - 2 * u + 2 * v - 4 * (A + r) + cc < kk) {
            int c = 0;
            int k = kk;

            while (u * u - v * v - 2 * u + 2 * v - 4 * (A + r) + c <= k) {
                r = r - v;
                v = v + 2;
                c++;
            }

            while (4 * (A + r) - u * u - v * v - 2 * u + 2 * v + c <= k) {
                r = r + u;
                u = u + 2;
                c++;
            }
        }
    }

    public static void main(String[] args) {
        int A = 0;
        int R = 0;

        if (args.length >= 2) {
            A = args[0].length();
            R = args[1].length();
        }

        if ((R - 1) * (R - 1) >= A) return;
        if (A % 2 != 1) return;

        int u = 2 * R + 1;
        int v = 1;
        int r = R * R - A;
        int cc = 0;
        int kk = args.length;

        loop(A, R, u, v, r, cc, kk);
    }
}
