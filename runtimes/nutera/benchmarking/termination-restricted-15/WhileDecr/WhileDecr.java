import java.util.Random;

public class WhileDecr {

    public static void loop(int i) {
        while (i > 5) {
            i = i - 1;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 1) {
            int i = args[0].length();
            loop(i);
        }
    }
}
