import zipfile,struct,glob,os
from collections import Counter
jars=glob.glob(r'runtimes/nutera/benchmarking/**/*.jar', recursive=True)
ctr=Counter()
samples={}
for jar in jars:
    try:
        with zipfile.ZipFile(jar,'r') as z:
            cls=[n for n in z.namelist() if n.endswith('.class') and '$' not in n]
            if not cls: continue
            n=cls[0]
            b=z.read(n)
            if len(b)<8 or b[0:4]!=b'\xca\xfe\xba\xbe': continue
            major=struct.unpack('>H', b[6:8])[0]
            ctr[major]+=1
            samples.setdefault(major,jar)
    except Exception:
        pass
print('majors',ctr)
for m in sorted(samples):
    print(m,samples[m])
