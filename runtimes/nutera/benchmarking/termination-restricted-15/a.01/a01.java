import java.util.Random;

public class a01 {

    public static void loop(int x, int y) {
        int c;
        c = 0;
        while (x > 0) {
            y = 0;
            while (y < x) {
                y = y + 1;
            }
            x = x - 1;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 2) {
            int x = args[0].length();
            int y = args[1].length();
            loop(x, y);
        }
    }
}
