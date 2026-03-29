

import java.util.Random;

public class Parts {
public static void loop(int x, int y, int z) {
    if (x < 0) {
        return;
    }
    if (y < 0) {
        return;
    }
    int m = 2 * x + 3;
    int[] ap = new int[m];
    int[] aq = new int[m];
    for (int p = 0; p <= x; p++) {
        for (int q = 0; q <= x; q++) {
            int t = 0;
            ap[t] = p;
            aq[t] = q;
            t++;
            while (t > 0) {
                t--;
                int cp = ap[t];
                int cq = aq[t];
                if (cp <= 0) {
                } else if (cq <= 0) {
                } else if (cq > cp) {
                    ap[t] = cp;
                    aq[t] = cp;
                    t++;
                } else {
                    ap[t] = cp;
                    aq[t] = cq - 1;
                    ap[t + 1] = cp - cq;
                    aq[t + 1] = cq;
                    t = t + 2;
                }
            }
        }
    }
}

public static void main(String[] args) {
    if (args.length >= 3) {
        int x = args[0].length();
        int y = args[1].length();
        int z = args[2].length();
        loop(x, y, z);
    }
}

}
