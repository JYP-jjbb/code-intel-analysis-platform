import zipfile,struct,sys
jar=r'runtimes/nutera/benchmarking/nuTerm-advantage/DynExUpTo2VarsConj/DynExUpTo2VarsConj.jar'
maj={}
with zipfile.ZipFile(jar,'r') as z:
    for n in z.namelist():
        if not n.endswith('.class'): continue
        b=z.read(n)
        if b[:4]!=b'\xca\xfe\xba\xbe': continue
        major=struct.unpack('>H',b[6:8])[0]
        maj.setdefault(major,0); maj[major]+=1
print(maj)
for m in sorted(maj):
    print('major',m)
