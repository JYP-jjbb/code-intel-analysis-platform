import java.util.Random;

public class ChawdharyCookGulwaniSagivYangESOP2008random2d {

    public static void loop(int r) {
        int N, x, y, i;

        N = 10;
        x = 0;
        y = 0;
        i = 0;

        while (i < N) {
            i = i + 1;

            r = r;
            if (r >= 0 && r <= 3) {
                if (r == 0) {
                    x = x + 1;
                } else {
                    if (r == 1) {
                        x = x - 1;
                    } else {
                        if (r == 2) {
                            y = y + 1;
                        } else {
                            if (r == 3) {
                                y = y - 1;
                            }
                        }
                    }
                }
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        if (args.length >= 1) x = args[0].length();
        loop(x);
    }
}
