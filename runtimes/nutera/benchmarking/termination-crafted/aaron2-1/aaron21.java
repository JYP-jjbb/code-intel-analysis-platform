import java.util.Random;

public class aaron21 {

    public static void loop(int t, int x, int y) {
        Random r = new Random();

        if (!(t <= 1073741823)) return;
        if (!(x <= 1073741823)) return;
        if (!(y >= -1073741823)) return;

        while (x >= y && t >= 0) {
            if (r.nextInt() != 0) {
                x = x - 1 - t;
            } else {
                y = y + 1 + t;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int t = 0;
        int x = 0;
        int y = 0;

        if (args.length >= 1) t = args[0].length();
        if (args.length >= 2) x = args[1].length();
        if (args.length >= 3) y = args[2].length();

        loop(t, x, y);
    }
}
