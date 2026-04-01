import java.util.Random;

public class AliasDarteFeautrierGonnordSAS2010nestedLoop1 {

    public static void loop(int i, int j, int k, int m, int n, int N) {
        if (0 <= n && 0 <= m && 0 <= N) {
            i = 0;
            while (i < n) {
                j = 0;
                while (j < m) {
                    j = j + 1;
                    k = i;
                    while (k < N - 1) {
                        k = k + 1;
                    }
                    i = k;
                }
                i = i + 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int i = 0, j = 0, k = 0, n = 0, m = 0, N = 0;

        if (args.length >= 6) {
            i = args[0].length();
            j = args[1].length();
            k = args[2].length();
            n = args[3].length();
            m = args[4].length();
            N = args[5].length();
        }

        loop(i, j, k, m, n, N);
    }
}

