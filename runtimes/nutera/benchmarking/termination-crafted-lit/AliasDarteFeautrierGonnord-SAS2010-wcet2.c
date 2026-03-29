 
typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main() {
    int i, j;
	i = __VERIFIER_nondet_int();
	j = __VERIFIER_nondet_int();
	while (i < 5) {
		j = 0;
		while (i > 2 && j <= 9) {
			j = j + 1;
        }
		i = i + 1;
	}
	return 0;
}
