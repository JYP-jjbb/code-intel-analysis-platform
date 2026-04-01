import java.util.Random;

public class b11 {

    public static void loop(int x, int y) {
        int c;
        c = 0;
        while (((x >= 0 && y < 2147483647 - x) || (x < 0 && y > -2147483648 - x)) && x + y > 0) {
            if (x > y) {
                x = x - 1;
            } else {
                if (x == y) {
                    x = x - 1;
                } else {
                    y = y - 1;
                }
            }
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
