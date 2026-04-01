import java.util.Random;

public class a07 {

    public static void loop(int x, int y, int z) {
        int c;
        c = 0;
        while ((x > y) && (x > z)) {
            y = y + 1;
            z = z + 1;
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

