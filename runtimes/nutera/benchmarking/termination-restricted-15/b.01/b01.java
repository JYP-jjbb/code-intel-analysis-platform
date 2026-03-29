

import java.util.Random;

public class b01 {

    public static void loop(int x, int y) {
        int c;
        c = 0;
        while (x > y) {
            x = x - 1;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 2) {
            int x = args[0].length();
            int y = args[1].length();
            loop(x, y);
        }
    }
}
