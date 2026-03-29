
import java.util.Random;

public class LobnyaBooleanReordered2 {

    public static void loop(int x, int b) {
        int n;

        if (!(x >= -2147483647)) return;

        while (b != 0) {
            n = b;
            b = n + 1;
            x = x - 1;
            if (x >= 0) {
                b = 1;
            } else {
                b = 0;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int b = 0;

        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) b = args[1].length();

        loop(x, b);
    }
}
