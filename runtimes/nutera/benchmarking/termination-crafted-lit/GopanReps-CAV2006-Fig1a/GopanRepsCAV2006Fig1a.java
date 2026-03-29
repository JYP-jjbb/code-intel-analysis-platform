

import java.util.Random;

public class GopanRepsCAV2006Fig1a {

    public static void loop(int x, int y) {
        x = 0;
        y = 0;
        while (y >= 0) {
            if (x <= 50) {
                y = y + 1;
            } else {
                y = y - 1;
            }
            x = x + 1;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        if (args.length >= 1) x = args[0].length();
        loop(x, 0);
    }
}

