public class TwoPol4VarsDisj {

    public static void loop(int n, int m) {
        int a = 0;
        int b = 0;

        while ((a + 1) * (a + 1) <= n || (b + 1) * (b + 1) <= m) {
            a = a + 1;
            b = b + 1;
        }
    }

    public static void main(String[] args) {
        if(args.length >= 2) {
          loop(args[0].length(), args[1].length());
        }
    }
}
