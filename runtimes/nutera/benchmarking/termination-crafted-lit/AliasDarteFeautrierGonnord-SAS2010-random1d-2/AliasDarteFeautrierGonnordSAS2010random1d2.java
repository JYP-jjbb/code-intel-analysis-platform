import java.util.Random;

public class AliasDarteFeautrierGonnordSAS2010random1d2 {

    public static void loop(int a, int x, int m, int n) {
        if (m > 0) {
            a = 0;
            x = 1;
            while (x < m) {
                if (n != 0) {
                    a = a + 1;
                } else {
                    a = a - 1;
                }
                x = x + 1;
            }
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
