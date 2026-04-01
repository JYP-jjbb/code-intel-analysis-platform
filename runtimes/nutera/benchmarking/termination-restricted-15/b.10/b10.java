import java.util.Random;

public class b10 {

    public static void loop(int x, int y) {
        int c;
        c = 0;
        while (((x >= 0 && y < 2147483647 - x) || (x < 0 && y > -2147483648 - x)) && x + y > 0) {
            if (x > 0) {
                x = x - 1;
            } else {
                if (y > 0) {
                    y = y - 1;
                } else {

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
