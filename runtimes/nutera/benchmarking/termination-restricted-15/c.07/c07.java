
import java.util.Random;

public class c07 {

    public static void loop(int i, int j, int k, int t) {
        int c;
        c = 0;
        while ((i <= 100) && (j <= k) && (k > -2147483648)) {
            t = i;
            i = j;
            j = t + 1;
            k = k - 1;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 4) {
            int i = args[0].length();
            int j = args[1].length();
            int k = args[2].length();
            int t = args[3].length();
            loop(i, j, k, t);
        }
    }
}
