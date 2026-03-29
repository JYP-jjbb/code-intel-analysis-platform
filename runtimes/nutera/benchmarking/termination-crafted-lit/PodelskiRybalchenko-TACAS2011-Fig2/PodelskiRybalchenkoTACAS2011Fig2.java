
import java.util.Random;

public class PodelskiRybalchenkoTACAS2011Fig2 {

    public static void loop(int x) {
        int y;

        while (x >= 0) {
            y = 1;
            while (y < x) {
                y = y + 1;
            }
            x = x - 1;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        if (args.length >= 1) x = args[0].length();
        loop(x);
    }
}
