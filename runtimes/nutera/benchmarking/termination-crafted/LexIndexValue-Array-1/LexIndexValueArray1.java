import java.util.Random;

public class LexIndexValueArray1 {

    public static void loop(int n) {
        int[] a = new int[1048];
        int k = 0;
        int i = 0;
        int v;

        while (i < 1048) {
            a[i] = n;
            n = n + 1;
            i = i + 1;
        }

        while (k < 1048 && a[k] >= 0) {
            v = n;
            n = n + 1;
            if (v != 0) {
                k = k + 1;
            } else {
                a[k] = a[k] - 1;
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

