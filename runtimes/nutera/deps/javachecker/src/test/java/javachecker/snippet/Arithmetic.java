package javachecker.snippet;

public class Arithmetic {

  public static void decreasingSum(int i, int j) {
    while (i > 0 && j > 0) {
      if ((i + j) % 2 == 0) {
        i--;
      } else {
        j--;
      }
    }
  }

  public static void decIncDec(int i) {
    while (i > 0) {
      i--;
      i++;
      i--;
    }
  }

  public static void countUp() {
    int i = 0;
    while (i <= 100)
      i++;
  }

  public static void countDownIntoNegative() {
    int i = 0;
    while (i >= -42)
      i--;
  }

  /*
   * * from Aprove_09 class DivMinus but without random
   */
  public static void divMinus(int x, int y) {
    for (int res = 0; x >= y && y > 0; res = res + 1) {
      x -= y;
    }
  }

  /*
   * from TPDB
   */
  public static void pastaB1(int x) {
    for (int y = 0; x > y; --x) {
    }
  }

  public static void pastaB12(int x, int y) {
    while (x > 0 || y > 0) {
      if (x > 0) {
        --x;
      } else if (y > 0) {
        --y;
      }
    }
  }

  public static void pastaB6(int x) {
    for (int y = 300; x > 0 && y > 0; --y) {
      --x;
    }
  }

  public static void pastaB8(int x) {
    if (x > 0) {
      while (x != 0) {
        if (x % 2 == 0) {
          x /= 2;
        } else {
          --x;
        }
      }
    }
  }
  
  public static void countUpToLimit(int limit) {
    int i = 0;
    do {
      i++;
    } while (i <= limit);
  }
  
  public static int gcd(int a, int b) {
    int tmp;
    while(b > 0 && a > 0) {
      tmp = b;
      b = a % b;
      a = tmp;
    }
    return a;
  }
  
  public static void pastaC3(int x, int y, int z) {
    while (x < y) {
      if (x < z) {
        x++;
      } else {
        z++;
      }
    }
  }
  
  /*
   * From Heizmann, Hoenicke, Leike, and Podelski. Linear Ranking for Linear Lasso Programs. ATVA2013. Fig5
   */
  public static void HHLP13Fig5(int x) {
    int y = 2;
    while (x >= 0 && y > 0) {
        x = x - y;
        y = (y + 1) / 2;
    }
  }
  
  /*
   * Chen Flur Mukhopadhyay. SAS 2012. Ex 216
   */
  public static void CFM12Ex216(int x, int y) {
    while (x > 0) {
        x = y;
        y = y - 1;
    }
  }
  
  /*
   * Colon Sipma TACAS2001 Fig1
   */
  public static void CS01Fig1(int k, int i, int j) {
    int tmp;
    while (i <= 100 && j <= k) {
      tmp = i;
      i = j;
      j = tmp + 1;
      k = k - 1;
    }
  }
  
  public static void pastaA7(int x, int y, int z) {
    while (x > y && x > z) {
        y++;
        z++;
    }
  }
  
  public static void countUpWithBias(int i, int j, int k) {
    while (i < k && j < 100) {
      i++;
      j++;
    }
  }
  
  public static void pastaC1(int x) {
    while (x >= 0) {
      int y = 1;
      while (x > y) {
        y = 2*y;
      }
      x--;
    }
  }
  
  public static void pastaA1(int x) {
    while (x > 0) {
        int y = 0;
        while (y < x) {
          y++;
        }
        x--;
    }
  }
  
  public static void CFM12Ex218(int x, int y) {
    while (x > 0) {
      x = x + y - 5;
      y = -2*y;
    }
  }
  
  public static int logIterative(int x, int y) {
    int res = 0;
    while (x >= y && y > 1) {
      res++;
      x = x/y;
    }
    return res;
  } 
  
  public static void plusSwap(int x, int y) {
    
    int z;
    int res = 0;

    while (y > 0) {
      z = x;
      x = y-1;
      y = z;
      res++;
    }

    res = res + x;
  }
  
  public static void PR04Fig1(int x, int y) {
    while (x >= 0 && x <= 1073741823) {
        y = 1;
        while (y < x) {
            y = 2*y;
        }
        x = x - 1;
    }
  }

}
