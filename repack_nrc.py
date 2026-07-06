import zipfile, os, shutil

ORIG_JAR = r"C:\Users\Janosch Bruns\OneDrive - Montessori-Schule Niederseeon - Schulnetz\Dokumente\noriskclientcopy\src-tauri\resources\nrc-client.jar"
PATCHED_DIR = r"C:\Users\Janosch Bruns\OneDrive - Montessori-Schule Niederseeon - Schulnetz\Dokumente\noriskclientcopy\nrc-patch-tmp2"
OUT_JAR = r"C:\Users\Janosch Bruns\OneDrive - Montessori-Schule Niederseeon - Schulnetz\Dokumente\noriskclientcopy\nrc-client-patched.jar"

# Read original jar entries (to preserve ones that failed to extract)
orig_entries = {}
with zipfile.ZipFile(ORIG_JAR, 'r') as z:
    for name in z.namelist():
        try:
            orig_entries[name] = z.read(name)
        except Exception as e:
            print(f"ORIG SKIP {name}: {e}")

print(f"Original entries: {len(orig_entries)}")

# Build new jar: use patched files where available, fallback to original
with zipfile.ZipFile(OUT_JAR, 'w', compression=zipfile.ZIP_DEFLATED) as out:
    used_patched = 0
    used_orig = 0

    for entry_name, orig_data in orig_entries.items():
        # Convert zip path to filesystem path
        fs_path = os.path.join(PATCHED_DIR, entry_name.replace('/', os.sep))

        if os.path.isfile(fs_path):
            with open(fs_path, 'rb') as f:
                data = f.read()
            out.writestr(entry_name, data)
            used_patched += 1
        else:
            out.writestr(entry_name, orig_data)
            used_orig += 1

print(f"New jar written: {used_patched} patched + {used_orig} original = {used_patched+used_orig} total")
print(f"Output: {OUT_JAR}")
