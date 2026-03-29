 

typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main() {
    int x, y;
	x = __VERIFIER_nondet_int();
	y = __VERIFIER_nondet_int();
	while (x >= 0 && y >= 1) {
		x = x - y;
		y = __VERIFIER_nondet_int();
	}
	return 0;
}
