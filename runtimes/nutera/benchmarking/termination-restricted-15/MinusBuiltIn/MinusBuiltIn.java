
import java.util.Random;

public class MinusBuiltIn {

    public static void loop(int x, int y) {
        int r;
        r = 0;

        if (!(x < 2147483647)) {
            return;
        }
        while (x > y) {
            y = x + 1;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 2) {
            int x = args[0].length();
            int y = args[1].length();
            loop(x, y);
        }
    }
}

