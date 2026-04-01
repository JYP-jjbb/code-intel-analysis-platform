import java.util.Random;

public class c03 {

    public static void loop(int x, int y, int z) {
        int c;
        c = 0;
        while (x < y && z < 2147483647) {
            if (x < z) {
                x = x + 1;
            } else {
                z = z + 1;
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

