import java.util.Random;

public class PastaB1 {

    public static void loop(int x, int y) {
        while (x > y) {
            x = x - 1;
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

