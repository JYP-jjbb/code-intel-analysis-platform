 
extern int __VERIFIER_nondet_int(void);

int main() {
	int x = 0, y = 100;
	int z = __VERIFIER_nondet_int();
	while (x < 40) {
		if (z == 0) {
			x = x + 1;
		} else {
			x = x + 2;
		}
	}
	return 0;
}
