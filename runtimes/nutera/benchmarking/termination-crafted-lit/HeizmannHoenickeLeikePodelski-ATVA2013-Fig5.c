 

extern int __VERIFIER_nondet_int(void);

int main() {
	int x = __VERIFIER_nondet_int();
	int y = 2;
	while (x >= 0) {
		x = x - y;
		y = (y + 1) / 2;
	}
	return 0;
}
