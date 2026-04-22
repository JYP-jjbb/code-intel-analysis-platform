import zipfile,struct
jar=r'runtimes/nutera/deps/javachecker/build/libs/javachecker-uber.jar'
maj={}
with zipfile.ZipFile(jar,'r') as z:
    for n in z.namelist():
        if not n.endswith('.class'): continue
        b=z.read(n)
        if b[:4]!=b'\xca\xfe\xba\xbe': continue
        m=struct.unpack('>H',b[6:8])[0]
        maj[m]=maj.get(m,0)+1
print(maj)
