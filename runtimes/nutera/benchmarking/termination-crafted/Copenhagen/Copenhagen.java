import java.util.Random;

public class Copenhagen {

    public static void loop(int x, int y) {
        int o;

        while (x >= 0 && y >= 0) {
            o = x;
            x = y - 1;
            y = o - 1;
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
