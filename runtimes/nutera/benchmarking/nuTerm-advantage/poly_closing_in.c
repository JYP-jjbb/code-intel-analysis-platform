extern int __VERIFIER_nondet_int(void);


int main() {

    int m = __VERIFIER_nondet_int();
    int a = 0;

    while (a * a <= m) {
        a = a + 1;
        m = m - 1;
    }

  return 0;
    
}
