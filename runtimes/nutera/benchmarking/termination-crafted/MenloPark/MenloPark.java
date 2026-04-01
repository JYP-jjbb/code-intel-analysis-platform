import java.util.Random;

public class MenloPark {

    public static void loop(int x) {
        int y;
        int z;

        y = 100;
        z = 1;
        while (x >= 0) {
            x = x - y;
            y = y - z;
            z = -z;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        if (args.length >= 1) x = args[0].length();
        loop(x);
    }
}

