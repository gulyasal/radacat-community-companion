# Provenance and distribution boundaries

This repository contains the newly implemented community companions. Interoperability work studied the discontinued Android app and observed Bluetooth traffic on owner-supplied C1 hardware. The implementations reproduce packet fields, identifiers and framing needed to talk to that hardware.

No original Radacat APK, decompiled vendor source file, firmware image, original artwork, database or extracted phone backup is included. The Android UI, Windows UI, connection management, coordinate handling, CRC implementation, tests and notification code were authored for this project with coding-agent assistance. This is a source/provenance review, not a legal clearance opinion.

The app icon was generated for this project using an image-generation tool and selected by the project owner. It is supplied in `assets/radacat-icon.png`; Android includes a resource copy for packaging. Its visual concept is two speech bubbles with radio waves on a teal background.

Desktop runtime dependencies are installed separately when running from source and bundled when using the Windows executable:

- Bleak: Bluetooth LE client, MIT license, https://github.com/hbldh/bleak
- Pillow: icon rendering, HPND license, https://github.com/python-pillow/Pillow
- Python/Tcl/Tk and platform libraries retain their own licenses.
- Windows WinRT bindings: MIT license, https://github.com/pywinrt/pywinrt
- The Windows executable is packaged with PyInstaller; its bootloader distribution exception permits bundling this MIT application. PyInstaller license texts are supplied with the Windows ZIP.
- The Android SDK, Java toolchain and R8/D8 compiler are build tools, not bundled release dependencies.

No dependency source/binaries are vendored in the source repository or source ZIP. Pip resolves Bleak's Windows dependencies into the developer's local virtual environment. The standalone Windows ZIP includes runtime binaries and third-party license notices. Dependencies' licenses are not replaced by this project's MIT license.

Public protocol IDs and model strings are retained for interoperability. Synthetic Bluetooth addresses, names and IDs in tests do not identify the owner's devices.
