import java.util.Random;

public class ex3b {

    public static void loop(int x, int y) {
        int c;
        c = 0;
        if (y > 46340) {
            return;
        }
        while ((x > 1) && (x < y)) {
            x = x * x;
            c = c + 1;
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

