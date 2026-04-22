import os,sys
print('python',sys.executable)
try:
    import z3
    print('z3 module', z3.__file__)
    pkg=os.path.dirname(z3.__file__)
    for root,dirs,files in os.walk(pkg):
        for f in files:
            lf=f.lower()
            if 'z3' in lf and (lf.endswith('.dll') or lf.endswith('.so') or lf.endswith('.jar')):
                print(os.path.join(root,f))
except Exception as e:
    print('ERR',e)
