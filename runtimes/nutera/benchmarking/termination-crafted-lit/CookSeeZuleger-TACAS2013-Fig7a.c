 

typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main() {
    int x, y, d;
	x = __VERIFIER_nondet_int();
	y = __VERIFIER_nondet_int();
	d = __VERIFIER_nondet_int();
    while (x>0 && y>0 && d>0) {
        if (__VERIFIER_nondet_int() != 0) {
            x = x - 1;
            d = __VERIFIER_nondet_int();
        } else {
            x = __VERIFIER_nondet_int();
            y = y - 1;
            d = d - 1;
        }
    }
    return 0;
}
