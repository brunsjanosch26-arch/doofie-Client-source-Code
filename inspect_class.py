import struct, sys

def read_class_strings(path):
    with open(path, 'rb') as f:
        data = f.read()
    strings = []
    i = 0
    while i < len(data) - 3:
        if data[i] == 1:  # CONSTANT_Utf8
            length = struct.unpack('>H', data[i+1:i+3])[0]
            if 2 <= length <= 200:
                s = data[i+3:i+3+length]
                try:
                    decoded = s.decode('utf-8')
                    strings.append(decoded)
                except:
                    pass
            i += 3 + length
        else:
            i += 1
    return strings

files = [
    r"C:\Users\Janosch Bruns\OneDrive - Montessori-Schule Niederseeon - Schulnetz\Dokumente\noriskclientcopy\nrc-patch-tmp2\gg\norisk\client\v2\modules\impl\BrandingOverlay.class",
    r"C:\Users\Janosch Bruns\OneDrive - Montessori-Schule Niederseeon - Schulnetz\Dokumente\noriskclientcopy\nrc-patch-tmp2\gg\norisk\client\v2\mixin\branding\BrandingPauseScreenMixin.class",
    r"C:\Users\Janosch Bruns\OneDrive - Montessori-Schule Niederseeon - Schulnetz\Dokumente\noriskclientcopy\nrc-patch-tmp2\gg\norisk\client\v2\mixin\branding\BrandingContainerScreenMixin.class",
]

for path in files:
    import os
    print(f"\n=== {os.path.basename(path)} ===")
    strings = read_class_strings(path)
    for s in strings:
        # Show method names, field names, interesting strings
        if any(c.isalpha() for c in s) and len(s) > 2:
            print(f"  {repr(s)}")
