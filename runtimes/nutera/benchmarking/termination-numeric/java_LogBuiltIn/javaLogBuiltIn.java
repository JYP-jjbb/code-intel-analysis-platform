import java.util.Random;

public class javaLogBuiltIn {
    public static void loop(int x) {
        int r = 0;
        while (x > 1) {
            x = x / 2;
            r++;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            return;
        }
        int x = args[0].length();
        loop(x);
    }

}
