

extern int __VERIFIER_nondet_int(void);

int main()
{
	int y = __VERIFIER_nondet_int();
	if (y >= 113383) {return 0;} // 113383 is the first input that would lead to an overflow
	while (y > 1) {
		if (y % 2 == 0) {
			y = y / 2;
		} else {
			y = 3*y + 1;
		}
	}
	return 0;
}
