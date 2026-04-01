import java.util.Random;

public class Bangalore2 {

    public static void loop(int x, int y) {

        if (y >= 1) {
            while (x >= 0) {
                x = x - y;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;

        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();

        loop(x, y);
    }
}

