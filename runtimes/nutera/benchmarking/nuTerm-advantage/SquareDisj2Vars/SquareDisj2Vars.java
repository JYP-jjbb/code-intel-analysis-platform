public class SquareDisj2Vars {

    public static void loop(int n) {
        int a = 0;
        int b = 0;

        while (a * a <= n || b <= n) {
            a = a + 1;
            b = b + 1;
        }
    }

    public static void main(String[] args) {
        if(args.length >= 1) {
          loop(args[0].length());
        }
    }
}
