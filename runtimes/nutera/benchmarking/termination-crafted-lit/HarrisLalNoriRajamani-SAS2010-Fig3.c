 

extern int __VERIFIER_nondet_int(void);

int x;

void foo(void) {
	x--;
}


int main() {
	x = __VERIFIER_nondet_int();

	while (x > 0) {
		if (__VERIFIER_nondet_int()) {
			foo();
		} else {
			foo();
		}
	}
	return 0;
}
