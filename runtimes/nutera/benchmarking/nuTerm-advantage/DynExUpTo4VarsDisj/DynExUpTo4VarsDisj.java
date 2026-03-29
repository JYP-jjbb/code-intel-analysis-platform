public class DynExUpTo4VarsDisj {

    public static void loop(int n, int m, int o, int p) {
        int a = 0;
        while (a * a * 4 <= n || a * a * 4 <= m ||  a * a * 4 <= o ||  a * a * 4 <= p) {
            a = a + 1;
        }
    }

    public static void main(String[] args) {
        if(args.length >= 4) {
            loop(args[0].length(), args[1].length(), args[2].length(), args[3].length());
        }
    }
}
