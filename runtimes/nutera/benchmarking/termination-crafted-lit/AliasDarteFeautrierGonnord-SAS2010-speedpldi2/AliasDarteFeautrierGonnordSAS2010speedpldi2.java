import java.util.Random;

public class AliasDarteFeautrierGonnordSAS2010speedpldi2 {

    public static void loop(int n, int m) {
        int v, v2;

        if (n >= 0 && m > 0) {
            v = n;
            v2 = 0;
            while (v > 0) {
                if (v2 < m) {
                    v2 = v2 + 1;
                    v = v - 1;
                } else {
                    v2 = 0;
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

