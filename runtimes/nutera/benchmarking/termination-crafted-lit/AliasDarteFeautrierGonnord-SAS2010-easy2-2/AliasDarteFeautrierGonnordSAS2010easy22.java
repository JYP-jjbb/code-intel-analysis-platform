import java.util.Random;

public class AliasDarteFeautrierGonnordSAS2010easy22 {

    public static void loop(int x, int y, int z) {
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
        if (args.length >= 1) {
            z = args[0].length();
        }
        loop(0, 0, z);
    }
}

