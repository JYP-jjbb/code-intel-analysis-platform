import java.util.Random;

public class Benghazinondet2 {

    public static void loop(int x, int d, int e) {
        int o;

        if (!(-1048575 <= x && x <= 1048575)) return;
        if (!(-1048575 <= d && d <= 1048575)) return;
        if (!(-1048575 <= e && e <= 1048575)) return;

        while (x >= 0) {
            x = x - d;
            o = d;
            d = e + 1;
            e = o + 1;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int d = 0;
        int e = 0;

        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) d = args[1].length();
        if (args.length >= 3) e = args[2].length();

        loop(x, d, e);
    }
}

