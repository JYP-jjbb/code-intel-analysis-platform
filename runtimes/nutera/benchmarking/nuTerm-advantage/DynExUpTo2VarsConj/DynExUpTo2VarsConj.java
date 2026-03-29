public class DynExUpTo2VarsConj {

    public static void loop(int n, int m) {
      int a = 0;
      while (a * a * 4 <= n && a * a * 4 <= m ) {
        a = a + 1;
      }
    } 

    public static void main(String[] args) {
        if(args.length >= 2) {
          loop(args[0].length(), args[1].length());
        }
    }
}
