

import java.util.Random;

public class javaSequence {

    public static void loop() {
        int i;
        int j;
        int c;
        c = 0;
        i = 0;
        while (i < 100) {
            c = c + 1;
            i = i + 1;
        }
        j = 5;
        while (j < 21) {
            c = c + 1;
            j = j + 3;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 0) {
            loop();
        }
    }
}
