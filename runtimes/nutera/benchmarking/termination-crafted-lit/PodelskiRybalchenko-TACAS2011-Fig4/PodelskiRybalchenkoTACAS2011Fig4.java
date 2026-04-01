import java.util.Random;

public class PodelskiRybalchenkoTACAS2011Fig4 {

    public static void loop(int x, int y, int a, int b) {
        while (x > 0 && y > 0) {
            if (a != 0) {
                x = x - 1;
                y = b;
            } else {
                y = y - 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;
        int a = 0;
        int b = 0;
        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();
        if (args.length >= 3) a = args[2].length();
        if (args.length >= 4) b = args[3].length();
        loop(x, y, a, b);
    }
}

