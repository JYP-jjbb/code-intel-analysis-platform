
import java.util.Random;

public class Geo2botht {

    public static void loop(int z, int k, int x, int y, int c) {
        while (true) {
            if (!(1 + x * z - x - z * y + c < k))
                break;

            c = c + 1;
            x = x * z + 1;
            y = y * z;
        }
    }

    public static void main(String[] args) {
        int z = 0;
        int k = 0;

        if (args.length >= 2) {
            z = args[0].length();
            k = args[1].length();
        }

        int x = 1;
        int y = 1;
        int c = 1;

        loop(z, k, x, y, c);
    }
}
