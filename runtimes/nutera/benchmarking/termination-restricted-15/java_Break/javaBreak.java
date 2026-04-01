import java.util.Random;

public class javaBreak {

    public static void loop() {
        int i;
        int c;
        i = 0;
        c = 0;
        while (i <= 10) {
            i = i + 1;
            c = c + 1;
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 0) {
            loop();
        }
    }
}

