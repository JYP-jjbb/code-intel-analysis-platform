

import java.util.Random;


public class Arrays03ValueRestictsIndex2 {

    public static void loop(int k) {
        Random r = new Random();
        int[] a = new int[1048];
        int i;
        int x;

        i = 0;
        while (i < 1048) {
            a[i] = r.nextInt();
            i = i + 1;
        }

        if (k >= 0 && k < 1048) {
            if (a[0] == 23 && a[k] == 42) {
                x = r.nextInt();
                while (x >= 0) {
                    x = x - k;
                }
            }
        }
        return;
    }

    public static void main(String[] args) {
        int k = 0;
        if (args.length >= 1) k = args[0].length();
        loop(k);
    }
}
