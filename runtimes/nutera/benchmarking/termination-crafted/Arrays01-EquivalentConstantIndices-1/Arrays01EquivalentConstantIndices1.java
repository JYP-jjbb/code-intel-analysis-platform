import java.util.Random;

public class Arrays01EquivalentConstantIndices1 {

    public static void loop(int x, int y) {
        Random r = new Random();
        int[] a = new int[1048];
        int i;

        i = 0;
        while (i < 1048) {
            a[i] = r.nextInt();
            i = i + 1;
        }

        while (a[1 + 2] >= 0) {
            a[3] = a[2 + 1] - 1;
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;

        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();

        loop(x, y);
    }
}
