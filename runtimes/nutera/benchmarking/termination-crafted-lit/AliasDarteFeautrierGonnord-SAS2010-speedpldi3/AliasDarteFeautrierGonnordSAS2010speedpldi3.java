import java.util.Random;

public class AliasDarteFeautrierGonnordSAS2010speedpldi3 {

    public static void loop(int n, int m) {
        int i, j;

        if (m > 0 && n > m) {
            i = 0;
            j = 0;
            while (i < n) {
                if (j < m) {
                    j = j + 1;
                } else {
                    j = 0;
                    i = i + 1;
                }
            }
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

