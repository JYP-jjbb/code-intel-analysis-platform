import java.util.Random;

public class c08 {

    public static void loop(int i, int j) {
        int c;
        c = 0;
        while (i >= 0) {
            j = 0;
            while (j <= i - 1) {
                j = j + 1;
            }
            i = i - 1;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 2) {
            int i = args[0].length();
            int j = args[1].length();
            loop(i, j);
        }
    }
}

