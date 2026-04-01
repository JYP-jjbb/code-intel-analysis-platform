import java.util.Random;

public class McCarthy91Iteration {

    public static void loop(int n) {
        int c;

        c = 1;
        while (c > 0) {
            if (n > 100) {
                n = n - 10;
                c = c - 1;
            } else {
                n = n + 11;
                c = c + 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int n = 0;
        if (args.length >= 1) n = args[0].length();
        loop(n);
    }
}

