
import java.util.Random;

public class KroeningSharyginaTsitovichWintersteigerCAV2010Ex {

    public static void loop(int i, int n) {
        while (i < 255) {
            if (n != 0) {
                i = i + 1;
            } else {
                i = i + 2;
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

