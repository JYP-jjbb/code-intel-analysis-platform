

import java.util.Random;

public class Nested2 {

    public static void loop() {
        int i;
        int j;
        i = 0;
        j = 3;

        while (i < 10) {
            while (j < 12) {
                j = j - 1;
                j = j + 2;
            }
            i = i + 1;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 0) {
            loop();
        }
    }
}
