
import java.util.Random;

public class c01noinv {

    public static void loop(int x, int y) {
        int c;
        c = 0;
        while (x >= 0) {
            y = 1;
            while (x > y && y < 1073741824) {
                y = 2 * y;
            }
            x = x - 1;
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

