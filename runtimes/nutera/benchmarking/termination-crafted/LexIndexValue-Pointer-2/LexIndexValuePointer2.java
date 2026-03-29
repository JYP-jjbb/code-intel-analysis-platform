
import java.util.Random;

public class LexIndexValuePointer2 {

    public static void loop(int n) {
        int[] p = new int[1048];
        int q;
        int i;
        int v;

        q = 0;
        i = 0;
        while (i < 1048) {
            v = n;
            n = n + 1;
            p[q + i] = v;
            i = i + 1;
        }

        q = 0;
        while (q < 1048 && p[q] >= 0) {
            v = n;
            n = n + 1;
            if (v != 0) {
                q = q + 1;
            } else {
                p[q] = p[q] - 1;
            }
        }

        return;
    }

    public static void main(String[] args) {
        int n = 0;
        if (args.length >= 1) n = args[0].length();
        loop(n);
    }
}
