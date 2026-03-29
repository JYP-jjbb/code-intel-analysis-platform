extern int __VERIFIER_nondet_int(void);

int main() {

  int a = 0;
  int b = 0;
  int n = __VERIFIER_nondet_int();
  int m = __VERIFIER_nondet_int();
  while (a * a <= n || b * b <= m) {
      a = a + 1;
      b = b + 1;
  }
  return 0;
}
