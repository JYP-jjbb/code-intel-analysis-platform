 

typedef enum {false, true} bool;

extern int __VERIFIER_nondet_int(void);

int main() {
    int x, y, z;
    x = __VERIFIER_nondet_int();
    y = __VERIFIER_nondet_int();
    z = __VERIFIER_nondet_int();
    while (x < y) {
        x = x + 1;
        y = z;
    }
    return 0;
}
