
import java.util.Random;

public class Pure2Phase1 {

    public static void loop(int y, int z, int n) {

        if (!(-1073741823 <= y && y <= 1073741823)) return;
        if (!(z <= 1073741823)) return;

        while (z >= 0) {
            y = y - 1;
            if (y >= 0) {
                z = n;
                if (!(z <= 1073741823)) return;
                n = n + 1;
            } else {
                z = z - 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int y = 0;
        int z = 0;
        int n = 0;

        if (args.length >= 1) y = args[0].length();
        if (args.length >= 2) z = args[1].length();
        if (args.length >= 3) n = args[2].length();

        loop(y, z, n);
    }
}

