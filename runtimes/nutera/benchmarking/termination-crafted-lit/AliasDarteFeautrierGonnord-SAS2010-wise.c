 
typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main() {
    int x, y;
	x = __VERIFIER_nondet_int();
	y = __VERIFIER_nondet_int();
	if (x >= 0 && y >= 0) {
		while (x - y > 2 || y - x > 2) {
			if (x < y) {
				x = x + 1;
			} else {
				y = y + 1;
			}
		}
	}
	return 0;
}
