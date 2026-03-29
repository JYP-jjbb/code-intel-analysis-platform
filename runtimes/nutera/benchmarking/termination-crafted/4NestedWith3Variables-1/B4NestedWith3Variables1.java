
import java.util.Random;

public class B4NestedWith3Variables1 {

    public static void loop(int q, int a, int b) {
        int o;

        if (!(-524287 <= q && q <= 524287)) return;
        if (!(-524287 <= a && a <= 524287)) return;
        if (!(-524287 <= b && b <= 524287)) return;

        while (q > 0) {
            q = q + a - 1;
            o = a;
            a = 3 * o - 4 * b;
            b = 4 * o + 3 * b;
        }
        return;
    }

    public static void main(String[] args) {
        int q = 0;
        int a = 0;
        int b = 0;

        if (args.length >= 1) q = args[0].length();
        if (args.length >= 2) a = args[1].length();
        if (args.length >= 3) b = args[2].length();

        loop(q, a, b);
    }
}
