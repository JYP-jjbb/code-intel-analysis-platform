
import java.util.Random;

public class Cairostep21 {

    public static void loop(int x) {

        if (x > 0) {
            while (x != 0 && x != -1) {
                x = x - 2;
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
