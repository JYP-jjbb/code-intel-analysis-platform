import java.util.Random;

public class aaron31 {

    public static void loop(int x, int y, int z, int t) {
        Random r = new Random();

        if (!(-1073741823 <= t && t <= 1073741823)) return;
        if (!(-1073741823 <= z && z <= 1073741823)) return;
        if (!(-1073741823 <= x && x <= 1073741823)) return;
        if (!(y <= 1073741823)) return;

        while (x >= y && x <= t + z) {
            if (r.nextInt() != 0) {
                z = z - 1;
                t = x;
                x = r.nextInt();
                if (!(-1073741823 <= x && x <= 1073741823)) return;
            } else {
                y = y + 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;
        int z = 0;
        int t = 0;

        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();
        if (args.length >= 3) z = args[2].length();
        if (args.length >= 4) t = args[3].length();

        loop(x, y, z, t);
    }
}
