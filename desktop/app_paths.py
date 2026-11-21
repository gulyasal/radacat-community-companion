"""Separate bundled assets from per-user writable data in the Windows EXE."""
import os
import sys
from pathlib import Path

def app_paths():
    source=Path(__file__).resolve().parent
    if getattr(sys,'frozen',False):
        data=Path(os.environ.get('LOCALAPPDATA',str(Path.home()/'AppData/Local')))/'RadacatCommunityCompanion'
        assets=Path(sys._MEIPASS)/'assets'
    else:
        data=source
        assets=source.parent/'assets'
    return data,assets
