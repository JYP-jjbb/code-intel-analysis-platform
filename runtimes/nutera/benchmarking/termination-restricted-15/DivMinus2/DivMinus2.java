import java.util.Random;

public class DivMinus2 {

    public static void loop(int x, int y) {
        int y1;
        int r;
        r = 0;
        while (x >= y && y > 0) {
            y1 = y;
            while (y1 != 0) {
                if (y1 > 0) {
                    y1 = y1 - 1;
                    x = x - 1;
                } else {
                    y1 = y1 + 1;
                    x = x + 1;
                }
            }
            r = r + 1;
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
