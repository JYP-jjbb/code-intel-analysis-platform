import java.util.Random;

public class CookSeeZulegerTACAS2013Fig8a {

    public static void loop(int x) {
        while (x != 0) {
            if (x > 0) {
                x = x - 1;
            } else {
                x = x + 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        if (args.length >= 1) x = args[0].length();
        loop(x);
    }
}

