public class MultDisj {

    public static void loop(int n, int m) {

        int a = 0;
        int b = 0;

        while (a * b <= n || a * b <= m) {
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
