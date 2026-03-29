

import java.util.Random;

public class javaContinue1 {

    public static void loop() {
        int i;
        int c;
        i = 0;
        c = 0;
        while (i < 20) {
            i = i + 1;
            if (i <= 10) {

            } else {
                c = c + 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 0) {
            loop();
        }
    }
}
