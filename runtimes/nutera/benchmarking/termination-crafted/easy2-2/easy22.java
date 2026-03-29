
import java.util.Random;

public class easy22 {

    public static void loop(int z) {
        int x;
        int y;

        x = 0;
        y = 0;
        while (z > 0) {
            x = x + 1;
            y = y - 1;
            z = z - 1;
        }
        return;
    }

    public static void main(String[] args) {
        int z = 0;
        if (args.length >= 1) z = args[0].length();
        loop(z);
    }
}

