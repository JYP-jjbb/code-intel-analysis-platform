import java.util.Random;

public class LeikeHeizmannTACAS2014Ex1 {

    public static void loop(int q, int y) {
        while (q > 0) {
            if (y > 0) {
                q = q - y - 1;
            } else {
                q = q + y - 1;
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

