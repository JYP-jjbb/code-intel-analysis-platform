import java.util.Random;

public class BrockschmidtCookFuhsCAV2013Fig9a {

    public static void loop(int k, int i, int j, int n) {
        if (k >= 1) {
            i = 0;
            while (i < n) {
                j = 0;
                while (j <= i) {
                    j = j + k;
                }
                i = i + 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int a = 0;
        int b = 0;
        int c = 0;
        int d = 0;
        if (args.length >= 1) a = args[0].length();
        if (args.length >= 2) b = args[1].length();
        if (args.length >= 3) c = args[2].length();
        if (args.length >= 4) d = args[3].length();
        loop(a, b, c, d);
    }
}

