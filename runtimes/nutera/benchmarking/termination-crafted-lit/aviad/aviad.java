import java.util.Random;

public class aviad {

    public static void loop(int a) {
        int t;
        int c = 0;

        while (a > 1) {
            t = a % 2;
            if (t == 0) {
                a = a / 2;
            } else {
                a = a - 1;
            }
            c = c + 1;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        if (args.length >= 1) x = args[0].length();
        loop(x);
    }
}
