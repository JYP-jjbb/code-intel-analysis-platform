
import java.util.Random;

public class McCarthyIterative {

    public static void loop(int x) {
        int c;
        c = 1;

        while (c > 0) {
            if (x > 100) {
                x = x - 10;
                c = c - 1;
            } else {
                x = x + 11;
                c = c + 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 1) {
            int x = args[0].length();
            loop(x);
        }
    }
}
