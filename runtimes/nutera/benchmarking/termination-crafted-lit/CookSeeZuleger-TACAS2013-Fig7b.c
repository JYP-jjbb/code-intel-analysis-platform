 

typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main() {
    int x, y, z;
	x = __VERIFIER_nondet_int();
	y = __VERIFIER_nondet_int();
	z = __VERIFIER_nondet_int();
	while (x>0 && y>0 && z>0) {
		if (__VERIFIER_nondet_int() != 0) {
			x = x - 1;
		} else {if (__VERIFIER_nondet_int() != 0) {
			y = y - 1;
			z = __VERIFIER_nondet_int();
		} else {
			z = z - 1;
			x = __VERIFIER_nondet_int();
		}}
	}
    return 0;
}
