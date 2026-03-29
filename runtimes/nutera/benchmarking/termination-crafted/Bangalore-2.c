

typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main()
{
    int x, y;
	x = __VERIFIER_nondet_int();
	y = __VERIFIER_nondet_int();
	if (y >= 1) {
    	while (x >= 0) {
	    	x = x - y;
	    }
	}
	return 0;
}
