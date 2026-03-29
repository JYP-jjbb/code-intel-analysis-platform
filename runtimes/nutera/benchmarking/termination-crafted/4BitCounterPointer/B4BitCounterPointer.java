import java.util.Random;

public class B4BitCounterPointer {

    public static void loop(int x, int y) {
        int a = 0;
        int b = 0;
        int c = 0;
        int d = 0;

        while (d == 0) {
            if (a == 0) {
                a = 1;
            } else {
                a = 0;
                if (b == 0) {
                    b = 1;
                } else {
                    b = 0;
                    if (c == 0) {
                        c = 1;
                    } else {
                        c = 0;
                        d = 1;
                    }
                }
            }
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

