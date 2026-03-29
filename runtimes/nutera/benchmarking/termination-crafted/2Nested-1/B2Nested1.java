import java.util.Random;

public class B2Nested1 {

    public static void loop(int x, int y) {

        if (!(x <= 65534)) return;
        if (!(y <= 65534)) return;
        if (!(y >= -65534)) return;

        while (x >= 0) {
            x = x + y;
            y = y - 1;
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
