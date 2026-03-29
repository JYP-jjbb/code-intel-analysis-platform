
import java.util.Random;

public class AliasDarteFeautrierGonnordSAS2010while2 {

    public static void loop(int n) {
        int i, j;

        i = n;
        while (i > 0) {
            j = n;
            while (j > 0) {
                j = j - 1;
            }
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

