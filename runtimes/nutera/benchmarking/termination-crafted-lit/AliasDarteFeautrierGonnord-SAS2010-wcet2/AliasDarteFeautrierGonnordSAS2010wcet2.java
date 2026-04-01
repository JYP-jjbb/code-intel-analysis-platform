import java.util.Random;

public class AliasDarteFeautrierGonnordSAS2010wcet2 {

    public static void loop(int i, int j) {
        while (i < 5) {
            j = 0;
            while (i > 2 && j <= 9) {
                j = j + 1;
            }
            i = i + 1;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;
        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();
        loop(x, y);
    }
}

