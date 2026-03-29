
import java.util.Random;

public class LarrazOliverasRodriguezCarbonellRubioFMCAD2013Fig1 {

    public static void loop(int x, int y, int z) {
        if (x <= 10000 && x >= -10000 && y <= 10000 && z <= 10000) {
            while (y >= 1) {
                x = x - 1;
                while (y < z) {
                    x = x + 1;
                    z = z - 1;
                }
                y = x + y;
            }
        }
        return;
    }

    public static void main(String[] args) {
        int x = 0;
        int y = 0;
        int z = 0;
        if (args.length >= 1) x = args[0].length();
        if (args.length >= 2) y = args[1].length();
        if (args.length >= 3) z = args[2].length();
        loop(x, y, z);
    }
}
