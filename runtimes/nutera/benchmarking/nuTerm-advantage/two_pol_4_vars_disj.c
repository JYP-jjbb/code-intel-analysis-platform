extern int __VERIFIER_nondet_int(void);


int main() {

  int a = 0;
  int b = 0;
  int n = __VERIFIER_nondet_int();
  int m = __VERIFIER_nondet_int();
  while ((a + 1) * (a + 1) <= n || (b + 1) * (b + 1) <= m) {
      a = a + 1;
      b = b + 1;
  }
  return 0;
}
