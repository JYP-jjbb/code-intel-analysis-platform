import java.util.Random;

public class reccounter3 {
    public static void loop(int x) {
        if (x <= 0) {
            return;
        }
        int r = 0;
        int a = 1;
        while (a <= x) {
            int rc = r;
            while (rc > 0) {
                rc--;
            }
            r = r + 1;
            a = a + 1;
        }
        while (r < 1) {
        }
    }

    public static void main(String[] args) {
        if (args.length >= 1) {
            int x = args[0].length();
            loop(x);
        }
    }

    }
