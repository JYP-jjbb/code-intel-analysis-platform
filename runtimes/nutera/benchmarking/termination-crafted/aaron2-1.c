

typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main() {
    int x, y, tx;
	tx = __VERIFIER_nondet_int();
	x = __VERIFIER_nondet_int();
	y = __VERIFIER_nondet_int();
  //prevent overflows
  if(!(tx<=1073741823)) return 0;
  if(!(x<=1073741823)) return 0;
  if(!(y>=-1073741823)) return 0;
	while (x >= y && tx >= 0) {
		if (__VERIFIER_nondet_int() != 0) {
			x = x - 1 - tx;
		} else {
			y = y + 1 + tx;
		}
	}
	return 0;
}
