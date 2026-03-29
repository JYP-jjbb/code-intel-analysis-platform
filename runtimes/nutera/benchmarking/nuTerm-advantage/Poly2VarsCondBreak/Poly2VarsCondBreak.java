public class Poly2VarsCondBreak {

    public static void loop(int n, int m) {
      int a = 0;
      while (a <= n) {

        if (a * a * 4 <= m) {
          break;
        }
        a = a + 1;
      }
    } 

    public static void main(String[] args) {
        if(args.length >= 2) {
          loop(args[0].length(), args[1].length());
        }
    }
}
