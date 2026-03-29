

typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main()
{
    int a, b, q, olda;
	q = __VERIFIER_nondet_int();
	a = __VERIFIER_nondet_int();
	b = __VERIFIER_nondet_int();
  //prevent overflows and underflows
  if(!(-524287<=q && q<=524287)) return 0;
  if(!(-524287<=a && a<=524287)) return 0;
  if(!(-524287<=b && b<=524287)) return 0;
	while (q > 0) {
		q = q + a - 1;
		olda = a;
		a = 3*olda - 4*b;
		b = 4*olda + 3*b;
	}
	return 0;
}
