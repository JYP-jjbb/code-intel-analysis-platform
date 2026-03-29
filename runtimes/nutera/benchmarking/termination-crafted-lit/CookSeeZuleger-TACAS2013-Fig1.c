 
typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main() {
    int x, y;
	x = __VERIFIER_nondet_int();
	y = __VERIFIER_nondet_int();
    while (x>0 && y>0) {
        if (__VERIFIER_nondet_int() != 0) {
            x = x - 1;
        } else {
            x = __VERIFIER_nondet_int();
            y = y - 1;
        }
    }
    return 0;
}
