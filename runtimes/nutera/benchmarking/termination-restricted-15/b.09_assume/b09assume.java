import java.util.Random;

public class b09assume {

    public static void loop(int x, int y) {
        int c;
        c = 0;
        if (y > 0) {
            while (x > 0) {
                if (x > y) {
                    x = y;
                } else {
                    x = x - 1;
                }
                c = c + 1;
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

