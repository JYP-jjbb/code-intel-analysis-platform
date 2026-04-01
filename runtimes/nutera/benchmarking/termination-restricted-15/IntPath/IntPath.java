import java.util.Random;

public class IntPath {

    public static void loop(int i) {
        int x;
        int y;
        x = 0;
        y = 0;

        if (i > 10) {
            x = 1;
        } else {
            y = 1;
        }
        while (x == y) {
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 1) {
            int i = args[0].length();
            loop(i);
        }
    }
}

