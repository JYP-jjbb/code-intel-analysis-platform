extern int __VERIFIER_nondet_int(void);

int main() {
	int x = 0, y = 0, z = __VERIFIER_nondet_int();
	while (z > 0) {
		x = x + 1;
		y = y - 1;
		z = z - 1;
	}
	return 0;
}
