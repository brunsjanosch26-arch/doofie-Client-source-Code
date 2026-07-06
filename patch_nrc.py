import zipfile, os, shutil, struct, re

JAR = r"C:\Users\Janosch Bruns\OneDrive - Montessori-Schule Niederseeon - Schulnetz\Dokumente\noriskclientcopy\src-tauri\resources\nrc-client.jar"
TMP = r"C:\Users\Janosch Bruns\OneDrive - Montessori-Schule Niederseeon - Schulnetz\Dokumente\noriskclientcopy\nrc-patch-tmp2"

# ── Extract ────────────────────────────────────────────────────────────────
if os.path.exists(TMP):
    shutil.rmtree(TMP)
os.makedirs(TMP)

with zipfile.ZipFile(JAR, 'r') as z:
    for name in z.namelist():
        try:
            z.extract(name, TMP)
        except Exception as e:
            print(f"SKIP {name}: {e}")

print("Extracted. Text files:")
for root, dirs, files in os.walk(TMP):
    for f in files:
        if any(f.endswith(ext) for ext in ['.json', '.txt', '.lang', '.properties']):
            print(" ", os.path.join(root, f).replace(TMP, ''))

# ── Patch text files ───────────────────────────────────────────────────────
REPLACEMENTS_TEXT = [
    ("NoRisk Client", "Doofie Client"),
    ("NoRisk ⚡ Client", "Doofie Client"),
    ("NORISK ⚡ CLIENT", "DOOFIE CLIENT"),
    ("norisk client", "doofie client"),
    ("NoRiskClient", "DoofieClient"),
]

patched_text = 0
for root, dirs, files in os.walk(TMP):
    for f in files:
        if any(f.endswith(ext) for ext in ['.json', '.txt', '.lang', '.properties', '.toml']):
            path = os.path.join(root, f)
            try:
                with open(path, 'r', encoding='utf-8', errors='replace') as fh:
                    content = fh.read()
                new_content = content
                for old, new in REPLACEMENTS_TEXT:
                    new_content = new_content.replace(old, new)
                if new_content != content:
                    with open(path, 'w', encoding='utf-8') as fh:
                        fh.write(new_content)
                    print(f"Patched text: {f}")
                    patched_text += 1
            except Exception as e:
                print(f"Error patching {f}: {e}")

# ── Patch .class files (bytecode string replacement) ──────────────────────
def patch_class_bytes(data):
    """
    Replace UTF-8 constant pool entries in Java .class files.
    Handles length changes by rewriting the affected entries.
    """
    REPLACEMENTS = [
        (b"NoRisk Client",        b"Doofie Client"),
        (b"NoRisk \xe2\x9a\xa1 Client", b"Doofie Client  "),  # ⚡ = E2 9A A1, pad to same length
        (b"NORISK \xe2\x9a\xa1 CLIENT", b"DOOFIE CLIENT  "),
        (b"NoRisk \xe2\x9a\xa1",  b"Doofie       "),           # pad
        (b"norisk-client",        b"doofie-client"),
        (b"NoRiskClient",         b"DoofieClient "),            # pad with space (same len)
        (b"nrc-client",           b"dfc-client"),
    ]
    for old, new in REPLACEMENTS:
        if len(old) != len(new):
            # Truncate or pad to match
            if len(new) < len(old):
                new = new + b' ' * (len(old) - len(new))
            else:
                new = new[:len(old)]
        data = data.replace(old, new)
    return data

patched_class = 0
for root, dirs, files in os.walk(TMP):
    for f in files:
        if f.endswith('.class'):
            path = os.path.join(root, f)
            try:
                with open(path, 'rb') as fh:
                    data = fh.read()
                new_data = patch_class_bytes(data)
                if new_data != data:
                    with open(path, 'wb') as fh:
                        fh.write(new_data)
                    print(f"Patched class: {root.replace(TMP,'')}/{f}")
                    patched_class += 1
            except Exception as e:
                print(f"Error patching {f}: {e}")

print(f"\nDone. Text files patched: {patched_text}, Class files patched: {patched_class}")

# ── Search for remaining references ────────────────────────────────────────
print("\nSearching for remaining 'NoRisk' references in class files:")
found = 0
for root, dirs, files in os.walk(TMP):
    for f in files:
        if f.endswith('.class'):
            path = os.path.join(root, f)
            with open(path, 'rb') as fh:
                data = fh.read()
            if b'NoRisk' in data or b'norisk' in data:
                print(f"  REMAINING: {root.replace(TMP,'')}/{f}")
                found += 1
if found == 0:
    print("  None found!")
