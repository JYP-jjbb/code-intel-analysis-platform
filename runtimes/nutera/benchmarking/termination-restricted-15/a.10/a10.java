/*
Original C source:

typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main() {
    int c;
    int x, y;
    x = __VERIFIER_nondet_int();
    y = __VERIFIER_nondet_int();
    c = 0;
    while (!(x == y)) {
        if (x > y) {
            y = y + 1;
        } else {
            x = x + 1;
        }
    }
    return 0;
}


-------------------------------------------

 * Auto-generated Java skeleton for SV-COMP benchmark.
 * Original C file: termination-restricted-15\a.10.c
 * You still need to manually translate the C code into Java inside loop().
 */

import java.util.Random;

public class a10 {

    public static void loop(int x, int y) {
        int c;
        c = 0;
        while (!(x == y)) {
            if (x > y) {
                y = y + 1;
            } else {
                x = x + 1;
            }
        }
        return;
    }

    public static void main(String[] args) {
        if (args.length >= 2) {
            int x = args[0].length();
            int y = args[1].length();
            loop(x, y);
        }
    }
}
