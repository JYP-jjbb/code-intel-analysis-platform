 

typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main() {
    int x, y;
	x = __VERIFIER_nondet_int();
	y = 23;
	while (x >= y) {
		x = x - 1;
	}
	return 0;
}
