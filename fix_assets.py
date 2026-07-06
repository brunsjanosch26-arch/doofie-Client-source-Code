import zipfile, os, shutil

PATCHED_JAR = r"C:\Users\Janosch Bruns\OneDrive - Montessori-Schule Niederseeon - Schulnetz\Dokumente\noriskclientcopy\src-tauri\resources\nrc-client.jar"
OUT_JAR = r"C:\Users\Janosch Bruns\OneDrive - Montessori-Schule Niederseeon - Schulnetz\Dokumente\noriskclientcopy\nrc-client-fixed.jar"

with zipfile.ZipFile(PATCHED_JAR, 'r') as zin:
    entries = {}
    for name in zin.namelist():
        try:
            entries[name] = zin.read(name)
        except:
            pass

print(f"Read {len(entries)} entries")

# Find all nrc-client assets and also add them under dfc-client namespace
extra = {}
for name, data in entries.items():
    if name.startswith("assets/nrc-client/"):
        new_name = name.replace("assets/nrc-client/", "assets/dfc-client/", 1)
        extra[new_name] = data
        print(f"  Duplicating: {name} -> {new_name}")

print(f"Adding {len(extra)} duplicate entries under dfc-client namespace")

with zipfile.ZipFile(OUT_JAR, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
    for name, data in entries.items():
        zout.writestr(name, data)
    for name, data in extra.items():
        zout.writestr(name, data)

print(f"Done: {OUT_JAR}")

# Replace
import shutil
shutil.copy2(OUT_JAR, PATCHED_JAR)
print("Replaced nrc-client.jar")
