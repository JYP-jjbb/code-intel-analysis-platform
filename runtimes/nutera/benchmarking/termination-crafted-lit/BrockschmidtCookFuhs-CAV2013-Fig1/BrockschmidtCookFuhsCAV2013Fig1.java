
import java.util.Random;

public class BrockschmidtCookFuhsCAV2013Fig1 {

    public static void loop(int i, int j, int n) {
        while (i < n) {
            j = 0;
            while (j <= i) {
                j = j + 1;
            }
            i = i + 1;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;
        int z = 0;
        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();
        if (args.length >= 3) z = args[2].length();
        loop(x, y, z);
    }
}

