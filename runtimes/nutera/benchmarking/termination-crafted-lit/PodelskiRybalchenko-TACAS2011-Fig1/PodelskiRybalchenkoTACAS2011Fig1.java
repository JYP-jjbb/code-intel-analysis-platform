
import java.util.Random;

public class PodelskiRybalchenkoTACAS2011Fig1 {

    public static void loop(int y) {
        while (y >= 0) {
            y = y - 1;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        if (args.length >= 1) x = args[0].length();
        loop(x);
    }
}

