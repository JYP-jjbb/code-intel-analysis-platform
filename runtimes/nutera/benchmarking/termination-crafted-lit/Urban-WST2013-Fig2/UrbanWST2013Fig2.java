import java.util.Random;

public class UrbanWST2013Fig2 {

    public static void loop(int x, int y) {
        while (x <= 10) {
            y = 10;
            while (y > 1) {
                y = y - 1;
            }
            x = x + 1;
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
