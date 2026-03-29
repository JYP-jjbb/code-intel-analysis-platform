 

typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main() {
    int k, i, j, n;
	k = __VERIFIER_nondet_int();
    i = __VERIFIER_nondet_int();
    j = __VERIFIER_nondet_int();
    n = __VERIFIER_nondet_int();
	if (k >= 1) {
		i = 0;
		while (i < n) {
			j = 0;
			while (j <= i) {
				j = j + k;
			}
			i = i + 1;
		}
	}
	return 0;
}
