import java.util.Random;

public class b13 {

    public static void loop(int x, int y, int z) {
        int c;
        c = 0;
        while ((x > z) || (y > z)) {
            if (x > z) {
                x = x - 1;
            } else {
                if (y > z) {
                    y = y - 1;
                } else {

                }
            }
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 3) {
            int x = args[0].length();
            int y = args[1].length();
            int z = args[2].length();
            loop(x, y, z);
        }
    }
}
