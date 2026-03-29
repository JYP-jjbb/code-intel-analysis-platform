

typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main()
{
    int x, y;
	x = __VERIFIER_nondet_int();
	y = __VERIFIER_nondet_int();

  // prevent overflow: x + y*(y+1)/2 <= 2^31-1
  if(!(x<=65534)) return 0;
  if(!(y<=65534)) return 0;
  // prevent underflow
  if(!(y>=-65534)) return 0;
	while (x >= 0) {
		x = x + y;
		y = y - 1;
	}
	return 0;
}
