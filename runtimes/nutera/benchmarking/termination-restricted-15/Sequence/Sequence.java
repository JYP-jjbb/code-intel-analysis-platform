
import java.util.Random;

public class Sequence {

    public static void loop() {
        int i;
        int j;
        i = 0;
        j = 5;

        while (i < 100) {
            i = i + 1;
        }

        while (j < 21) {
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
