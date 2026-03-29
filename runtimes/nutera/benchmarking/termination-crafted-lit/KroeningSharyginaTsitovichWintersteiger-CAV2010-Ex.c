 

typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main() {
    int i;
	i = __VERIFIER_nondet_int();
	while (i < 255) {
		if (__VERIFIER_nondet_int() != 0) {
			i = i + 1;
		} else {
			i = i + 2;
		}
	}
	return 0;
}
