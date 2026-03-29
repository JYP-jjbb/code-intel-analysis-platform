 
typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main() {
    int i, j, N;
	N = __VERIFIER_nondet_int();
	i = N;
	while (i > 0) {
		j = N;
		while (j > 0) {
			j = j - 1;
        }
		i = i - 1;
	}
	return 0;
}
