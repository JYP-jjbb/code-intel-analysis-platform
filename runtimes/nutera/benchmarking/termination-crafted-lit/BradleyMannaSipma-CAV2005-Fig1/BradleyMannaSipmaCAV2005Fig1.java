import java.util.Random;

public class BradleyMannaSipmaCAV2005Fig1 {

    public static void loop(int y1, int y2) {
        if (y1 > 0 && y2 > 0) {
            while (y1 != y2) {
                if (y1 > y2) {
                    y1 = y1 - y2;
                } else {
                    y2 = y2 - y1;
                }
            }
        }
        return;
    }

    public static void main(String[] args) {
        int y1 = 0, y2 = 0;

        if (args.length >= 2) {
            y1 = args[0].length();
            y2 = args[1].length();
        }

        loop(y1, y2);
    }
}
