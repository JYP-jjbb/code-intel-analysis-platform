import java.util.Random;

public class Geo1botht {

    public static void loop(int z, int k, int x, int y, int c) {
        while (true) {
            if (!(x * z - x - y + 1 + c < k))
                break;

            c = c + 1;
            x = x * z + 1;
            y = y * z;
        }

        x = x * (z - 1);
    }

    public static void main(String[] args) {
        int z = 0;
        int k = 0;

        if (args.length >= 2) {
            z = args[0].length();
            k = args[1].length();
        }

        int x = 1;
        int y = z;
        int c = 1;

        loop(z, k, x, y, c);
    }
}
