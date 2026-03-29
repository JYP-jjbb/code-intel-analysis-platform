
import java.util.Random;

public class HarrisLalNoriRajamaniSAS2010Fig3 {

    public static void loop(int x, int n) {
        while (x > 0) {
            if (n != 0) {
                x = x - 1;
            } else {
                x = x - 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int n = 0;
        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) n = args[1].length();
        loop(x, n);
    }
}
