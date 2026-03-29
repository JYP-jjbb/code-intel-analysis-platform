

typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main()
{
    int x;
    x = __VERIFIER_nondet_int();
	if (x > 0) {
	    while (x != 0 && x!= -1) {
	    	x = x - 2;
    	}
	}
	return 0;
}
