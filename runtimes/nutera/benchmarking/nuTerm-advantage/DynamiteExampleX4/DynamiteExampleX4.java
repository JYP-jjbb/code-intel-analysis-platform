public class DynamiteExampleX4 {

    public static void loop(int n) {
      int a = 0;
      while (a * a * 4 <= n ) {
        a = a + 1;
      }
    }

    public static void main(String[] args) {
        if(args.length >= 1) {
          loop(args[0].length());
        }
    }
}
