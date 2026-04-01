import java.util.Random;

public class PastaA1 {

    public static void loop(int x) {
        int y;
        while (x > 0) {
            y = 0;
            while (y < x) {
                y = y + 1;
            }
            x = x - 1;
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
