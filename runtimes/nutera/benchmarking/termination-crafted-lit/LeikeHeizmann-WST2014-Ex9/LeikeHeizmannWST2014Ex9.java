import java.util.Random;

public class LeikeHeizmannWST2014Ex9 {

    public static void loop(int x) {
        while (x > 0) {
            x = x / 2;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        if (args.length >= 1) x = args[0].length();
        loop(x);
    }
}
