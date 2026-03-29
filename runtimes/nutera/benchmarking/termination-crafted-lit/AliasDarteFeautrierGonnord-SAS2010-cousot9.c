typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main() {
    int i, j, N;
	j = __VERIFIER_nondet_int();
	N = __VERIFIER_nondet_int();
	i = N;
	while (i > 0) {
		if (j > 0) {
			j = j - 1;
		} else {
			j = N;
			i = i - 1;
		}
	}
	return 0;
}
