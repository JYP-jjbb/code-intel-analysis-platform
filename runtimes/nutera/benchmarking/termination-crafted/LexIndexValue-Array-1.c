
extern int __VERIFIER_nondet_int(void);

int main() {
	int a[1048];
	int k = 0;

  for (int i = 0; i < 1048; i++) {
    a[i] = __VERIFIER_nondet_int();
  }

	while (k < 1048 && a[k] >= 0) {
		if (__VERIFIER_nondet_int()) {
			k++;
		} else {
			a[k]--;
		}
	}
	return 0;
}
