import java.util.Random;

public class PastaA10 {

    public static void loop(int x, int y) {
        while (x != y) {
            if (x > y) {
                y = y + 1;
            } else {
                x = x + 1;
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
