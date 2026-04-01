import java.util.Random;

public class WhileFalse {

    public static void loop(int x) {
        boolean b = false;
        while (b) {
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        if (args.length >= 1) x = args[0].length();
        loop(x);
    }
}

