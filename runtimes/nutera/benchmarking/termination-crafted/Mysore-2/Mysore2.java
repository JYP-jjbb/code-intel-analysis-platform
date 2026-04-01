import java.util.Random;

public class Mysore2 {

    public static void loop(int c, int x) {

        if (!(-65535 <= x && x <= 65535)) return;
        if (!(-65535 <= c && c <= 65535)) return;

        if (c >= 2) {
            while (x + c >= 0) {
                x = x - c;
                c = c + 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int c = 0;
        int x = 0;

        if (args.length >= 1) c = args[0].length();
        if (args.length >= 2) x = args[1].length();

        loop(c, x);
    }
}
