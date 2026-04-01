import java.util.Random;

public class Collatzbounded {

    public static void loop(int y) {

        if (y >= 113383) {
            return;
        }

        while (y > 1) {
            if (y % 2 == 0) {
                y = y / 2;
            } else {
                y = 3 * y + 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int y = 0;
        if (args.length >= 1) y = args[0].length();
        loop(y);
    }
}

