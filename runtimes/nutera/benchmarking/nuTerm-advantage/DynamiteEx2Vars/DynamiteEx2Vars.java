public class DynamiteEx2Vars {

    public static void loop(int n, int m) {
      int a = 0;
      int b = 0;

      while (a * a * 4 <= n || b*b*4 <= m) {
        a = a + 1;
        b = b + 1;
      }
    }

    public static void main(String[] args) {
        if(args.length >= 2) {
          loop(args[0].length(),args[1].length());
        }
    }
}
