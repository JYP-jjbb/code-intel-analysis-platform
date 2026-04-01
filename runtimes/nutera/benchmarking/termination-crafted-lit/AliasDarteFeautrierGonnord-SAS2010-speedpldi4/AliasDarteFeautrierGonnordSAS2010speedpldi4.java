import java.util.Random;

public class AliasDarteFeautrierGonnordSAS2010speedpldi4 {

    public static void loop(int n, int m) {
        int i;

        if (m > 0 && n > m) {
            i = n;
            while (i > 0) {
                if (i < m) {
                    i = i - 1;
                } else {
                    i = i - m;
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

