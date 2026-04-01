import java.util.Random;

public class javaNested {

    public static void loop() {
        int i;
        int j;
        int c;
        c = 0;
        i = 0;
        while (i < 10) {
            j = 3;
            while (j < 12) {
                j = j - 1;
                c = c + 1;
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

