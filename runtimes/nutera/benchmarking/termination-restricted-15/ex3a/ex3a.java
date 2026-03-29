

import java.util.Random;

public class ex3a {

    public static void loop(int x) {
        int c;
        c = 0;
        while ((x > 1) && (x < 100)) {
            x = x * x;
            c = c + 1;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 1) {
            int x = args[0].length();
            loop(x);
        }
    }
}
