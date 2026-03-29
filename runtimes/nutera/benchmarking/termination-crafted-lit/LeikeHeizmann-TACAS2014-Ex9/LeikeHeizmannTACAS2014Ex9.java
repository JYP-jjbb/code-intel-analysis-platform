
import java.util.Random;

public class LeikeHeizmannTACAS2014Ex9 {

    public static void loop(int p, int q) {
        while (q > 0 && p > 0 && p != q) {
            if (q < p) {
                q = q - 1;
            } else {
                if (p < q) {
                    p = p - 1;
                }
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;
        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();
        loop(x, y);
    }
}
