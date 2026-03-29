public class DynamiteExample {

    public static void loop(int n) {
      int a = 0;
      while ((a + 1) * (a + 1) <= n ) {
        a = a + 1;
      }
    } 

    public static void main(String[] args) {
        if(args.length >= 1) {
          loop(args[0].length());
        }
    }
}
