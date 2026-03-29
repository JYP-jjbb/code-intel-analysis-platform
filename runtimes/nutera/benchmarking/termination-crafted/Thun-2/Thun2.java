
import java.util.Random;

public class Thun2 {

    public static void loop(int x, int y) {

        if (!(-65535 <= x && x <= 65535)) return;
        if (!(-65535 <= y && y <= 65535)) return;

        while (x >= 0) {
            x = x + y;
            y = (-2) * y - 1;
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

