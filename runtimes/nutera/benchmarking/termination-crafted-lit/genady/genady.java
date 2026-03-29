
import java.util.Random;

public class genady {

    public static void loop(int i) {
        int j;

        j = 1;
        i = 10000;
        while (i - j >= 1) {
            j = j + 1;
            i = i - 1;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        if (args.length >= 1) x = args[0].length();
        loop(x);
    }
}

