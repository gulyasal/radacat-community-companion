"""Build on Windows with Python 3.11+ and requirements-build.txt installed."""
from pathlib import Path
import importlib.metadata
import subprocess
import sys
import os
import shutil
from PIL import Image

root=Path(__file__).resolve().parent
build=root/'build'
build.mkdir(exist_ok=True)
icon=build/'radacat.ico'
Image.open(root.parent/'assets/radacat-icon.png').save(icon,format='ICO',sizes=[(16,16),(32,32),(48,48),(64,64),(128,128),(256,256)])
args=[sys.executable,'-m','PyInstaller','--noconfirm','--onefile','--windowed','--name','RADACAT-Community-Companion','--icon',str(icon),'--distpath',str(root/'dist'),'--workpath',str(build/'pyinstaller'),'--specpath',str(build),'--paths',str(root),'--add-data',str(root.parent/'assets')+os.pathsep+'assets','--add-data',str(root.parent/'LICENSE')+os.pathsep+'licenses/project','--collect-submodules','bleak','--collect-submodules','winrt']
# Include license texts for bundled runtime dependencies without bundling user data.
for distribution in importlib.metadata.distributions():
    name=distribution.metadata.get('Name','unknown')
    if not (name.lower().startswith(('bleak','pillow','winrt','typing_extensions'))):
        continue
    for item in distribution.files or []:
        if any('license' in part.lower() or 'copying' in part.lower() for part in item.parts):
            source=Path(distribution.locate_file(item))
            if source.is_file():
                args+=['--add-data',str(source)+os.pathsep+'licenses/'+name]
python_license=Path(sys.base_prefix)/'LICENSE.txt'
if python_license.exists():args+=['--add-data',str(python_license)+os.pathsep+'licenses/python']
args.append(str(root/'radacat_desktop.pyw'))
subprocess.run(args,check=True)
print('Built:',root/'dist/RADACAT-Community-Companion.exe')
# Keep notices readable beside the executable as well as inside its bundle.
notices=root/'dist/licenses'
for distribution in importlib.metadata.distributions():
    name=distribution.metadata.get('Name','unknown')
    if not name.lower().startswith(('bleak','pillow','winrt','typing_extensions','pyinstaller')):
        continue
    for item in distribution.files or []:
        if any('license' in part.lower() or 'copying' in part.lower() for part in item.parts):
            source=Path(distribution.locate_file(item))
            if source.is_file():
                target=notices/name/Path(str(item).replace('../',''))
                target.parent.mkdir(parents=True,exist_ok=True)
                shutil.copy2(source,target)
if python_license.exists():
    (notices/'python').mkdir(parents=True,exist_ok=True)
    shutil.copy2(python_license,notices/'python/LICENSE.txt')
for source in (Path(sys.base_prefix)/'tcl').rglob('license.terms'):
    target=notices/'tcl-tk'/source.relative_to(Path(sys.base_prefix)/'tcl')
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(source,target)
