import java.util.Random;

public class SyntaxSupportPointer011 {

    public static void loop(int x) {
        int[] p = new int[1];

        p[0] = x;

        while (p[0] >= 0) {
            p[0] = p[0] - 1;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        if (args.length >= 1) x = args[0].length();
        loop(x);
    }
}

