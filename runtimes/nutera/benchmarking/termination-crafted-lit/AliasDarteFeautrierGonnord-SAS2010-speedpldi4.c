 
typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main() {
    int i, m, n;
	n = __VERIFIER_nondet_int();
	m = __VERIFIER_nondet_int();
	if (m > 0 && n > m) {
		i = n;
		while (i > 0) {
			if (i < m) {
				i = i - 1;
			} else {
				i = i - m;
            }
		}
	}
	return 0;
}
