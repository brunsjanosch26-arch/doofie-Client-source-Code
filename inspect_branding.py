import struct, os

def read_class_methods(path):
    with open(path, 'rb') as f:
        data = f.read()
    # Extract all utf8 strings
    strings = {}
    i = 8  # skip magic + version
    # Read constant pool count
    cp_count = struct.unpack('>H', data[i:i+2])[0]
    i += 2
    cp = [None] * cp_count
    idx = 1
    while idx < cp_count:
        tag = data[i]
        i += 1
        if tag == 1:  # Utf8
            length = struct.unpack('>H', data[i:i+2])[0]
            s = data[i+2:i+2+length]
            try:
                cp[idx] = s.decode('utf-8')
            except:
                cp[idx] = repr(s)
            i += 2 + length
        elif tag in (7, 8, 16, 19, 20):  # class, string, etc - 2 bytes
            i += 2
        elif tag in (9, 10, 11, 12, 18):  # fieldref, methodref, etc - 4 bytes
            i += 4
        elif tag in (3, 4, 17):  # int, float - 4 bytes
            i += 4
        elif tag in (5, 6):  # long, double - 8 bytes (takes 2 slots)
            i += 8
            idx += 1
        elif tag == 15:  # MethodHandle - 3 bytes
            i += 3
        else:
            break
        idx += 1

    # Find method-like strings
    methods = [s for s in cp if s and isinstance(s, str) and
               not s.startswith('(') and not s.startswith('[') and
               not s.startswith('L') and '/' not in s and
               len(s) > 2 and any(c.isalpha() for c in s)]
    return methods

path = r"C:\Users\Janosch Bruns\OneDrive - Montessori-Schule Niederseeon - Schulnetz\Dokumente\noriskclientcopy\nrc-patch-tmp2\gg\norisk\client\v2\mixin\branding\BrandingPauseScreenMixin.class"
print("Methods/strings in BrandingPauseScreenMixin:")
for m in read_class_methods(path):
    print(f"  {repr(m)}")
