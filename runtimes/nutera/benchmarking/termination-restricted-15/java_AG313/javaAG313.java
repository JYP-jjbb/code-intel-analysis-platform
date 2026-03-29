
import java.util.Random;

public class javaAG313 {

    public static void loop(int x, int y) {
        int i;
        i = 0;
        if (x != 0) {
            while (x > 0 && y > 0) {
                i = i + 1;
                x = (x - 1) - (y - 1);
            }
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 2) {
            int x = args[0].length();
            int y = args[1].length();
            loop(x, y);
        }
    }
}

