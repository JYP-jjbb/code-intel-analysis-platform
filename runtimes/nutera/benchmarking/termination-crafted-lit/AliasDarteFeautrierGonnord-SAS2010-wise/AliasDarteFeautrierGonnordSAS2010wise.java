
import java.util.Random;

public class AliasDarteFeautrierGonnordSAS2010wise {

    public static void loop(int x, int y) {
        if (x >= 0 && y >= 0) {
            while (x - y > 2 || y - x > 2) {
                if (x < y) {
                    x = x + 1;
                } else {
                    y = y + 1;
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

