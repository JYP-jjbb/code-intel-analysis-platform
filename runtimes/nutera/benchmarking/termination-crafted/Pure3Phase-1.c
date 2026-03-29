 

typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main()
{
    int x, y, z;
    x = __VERIFIER_nondet_int();
    y = __VERIFIER_nondet_int();
    z = __VERIFIER_nondet_int();
    // prevent overflows
    if(!(x<=65535)) return 0;
    if(!(-65535<=y && y<=65535)) return 0;
    if(!(-65535<=z && z<=65535)) return 0;
	while (x >= 0) {
		if (__VERIFIER_nondet_int() != 0) {
			x = x + y;
		} else {
			x = x + z;
		}
		y = y + z;
		z = z - 1;
	}
	return 0;
}
