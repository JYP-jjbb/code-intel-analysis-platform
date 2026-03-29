

import java.util.Random;

public class KroeningSharyginaTsitovichWintersteigerCAV2010Fig1 {

    public static void loop(int x) {
        int d;

        d = 0;
        while (x < 255) {
            if (x % 2 != 0) {
                x = x - 1;
            } else {
                x = x + 2;
            }
            if (d != 0) {
                x = 0;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        if (args.length >= 1) x = args[0].length();
        loop(x);
    }
}

