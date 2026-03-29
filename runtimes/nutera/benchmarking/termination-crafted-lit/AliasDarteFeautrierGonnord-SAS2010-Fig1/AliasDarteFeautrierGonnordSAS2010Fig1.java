
import java.util.Random;

public class AliasDarteFeautrierGonnordSAS2010Fig1 {

    public static void loop(int x, int y, int m, int n) {
        y = 0;
        x = m;
        while (x >= 0 && y >= 0) {
            if (n != 0) {
                while (y <= m && n != 0) {
                    y = y + 1;
                }
                x = x - 1;
            }
            y = y - 1;
        }
        return;
    }

    public static void main(String[] args) {
        int m = 0;
        int n = 0;

        if (args.length >= 2) {
            m = args[0].length();
            n = args[1].length();
        }

        loop(0, 0, m, n);
    }
}
