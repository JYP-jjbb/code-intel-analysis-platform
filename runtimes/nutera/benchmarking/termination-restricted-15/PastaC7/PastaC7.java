
import java.util.Random;

public class PastaC7 {

    public static void loop(int i, int j, int k) {
        int t;
        while (i <= 100 && j < k) {
            i = j;
            j = i + 1;
            k = k - 1;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 3) {
            int i = args[0].length();
            int j = args[1].length();
            int k = args[2].length();
            loop(i, j, k);
        }
    }
}
