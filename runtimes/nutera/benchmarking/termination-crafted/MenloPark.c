

typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main()
{
    int x, y, z;
	x = __VERIFIER_nondet_int();
	y = 100;
	z = 1;
	while (x >= 0) {
		x = x - y;
		y = y - z;
		z = -z;
	}
	return 0;
}
