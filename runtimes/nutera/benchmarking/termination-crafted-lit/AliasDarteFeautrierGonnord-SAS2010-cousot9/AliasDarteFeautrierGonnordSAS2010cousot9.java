import java.util.Random;

public class AliasDarteFeautrierGonnordSAS2010cousot9 {

    public static void loop(int i, int j, int n) {
        while (i > 0) {
            if (j > 0) {
                j = j - 1;
            } else {
                j = n;
                i = i - 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int j = 0;
        int n = 0;
        int i = 0;

        if (args.length >= 2) {
            j = args[0].length();
            n = args[1].length();
        }

        i = n;
        loop(i, j, n);
    }
}

