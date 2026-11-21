# RADACAT Community Companion

<img src="assets/radacat-icon.png" alt="Two speech bubbles with radio waves" width="128" />

An unofficial Android and Windows companion for owners of Radacat C1 radio messengers. It restores short public text messaging without a Radacat account or the discontinued vendor service.

**Early community preview.** Tested on a small number of C1 units identifying as **TomCat II Note, device model 6, firmware 1.0.0**. Other models, regions and firmware versions are unverified. This is not an emergency or safety communication system.

## What works

- C1-to-C1 text messaging through a phone or Windows PC over Bluetooth LE.
- Saved sender names, readable conversation views, and clickable Google Maps coordinates.
- Phone background connection, incoming-message notifications and automatic connection recovery.
- Windows saved-device discovery and automatic reconnection.
- Optional recent phone location; optional fixed desktop coordinates.
- Settings screens, battery status, device details and troubleshooting reports.

The tested setup uses **public group 2222, channel 0**. Text and coordinates are **unencrypted**. Names are labels, not verified identities. A Bluetooth write confirmation does not establish recipient delivery. There is no application-level delivery receipt/retry guarantee.

## Download and install

Open this repository's **Releases** page for the Android APK and standalone Windows application. Until the first release is published, use the source build instructions below.

### Android

1. Download the community APK to your phone and allow installation from that download source when Android asks.
2. Open RADACAT Companion. Turn on a C1, grant Nearby devices permission, find it and select its `LL…` name.
3. Set your sender name in Settings. Keep public group/channel at the tested defaults.
4. Grant notification permission to receive incoming-message alerts while away from the conversation.
5. Optional: permit location when sending. If enabled and a fix from the last five minutes exists, a separate coordinate message is sent. Without a recent fix or permission, only the text is sent. Location sharing can be disabled in Settings.

Android 8+ is the declared minimum; live tests were performed on Android 12 and Android 17, not every intervening version. Android Force stop requires reopening the app. Persistent connection recovery has been tested after reopening; long-term reliability across all phone vendors is unverified.

**Existing private test builds:** a community release signed with a different key cannot update the private APK in place. Keep your existing app until you have saved any history you need. Android will require uninstalling that differently signed build, which removes its local data, before installing the community release. Do not uninstall just to test a download unnecessarily.

### Windows

1. Download `Radacat-Community-Desktop-preview.zip` and extract it.
2. Double-click `RADACAT-Community-Companion.exe`. No Python installation is needed.
3. Open Settings, find your C1 and connect. The app remembers this device for future launches.

Windows 64-bit and Bluetooth LE support are required. Tested on Windows 11. The preview executable is not Authenticode signed. Settings and logs are stored in `%LOCALAPPDATA%\RadacatCommunityCompanion`, independently of the executable, so replacing it preserves your settings. Radio messaging does not require internet.

To run from source or rebuild the executable, see [desktop/README.md](desktop/README.md).

Use a different C1 for each phone/PC. A C1 already connected to another Bluetooth client may not appear in scans.

## Sending and receiving

Messages are prefixed with your saved name, for example `Camp: Meet at the gate`. Each packet supports **42 UTF-8 bytes including the name and prefix**. Unicode characters can use multiple bytes. Longer input is rejected without truncating it.

Coordinates use a separate short `Name: geo:latitude,longitude` message. Click or tap it to open Google Maps. Maps access and map tiles may require internet; the radio transmission itself does not. The C1 uses the phone's location; built-in C1 GPS has not been established.

Desktop fixed coordinates are optional and disabled by default. Use decimal degrees: north/east positive, south/west negative. When enabled, sending a message also sends the configured location publicly to the group.

## Limitations

- Public unencrypted text only. Private teams, vendor-account features, voice, full location tracking, standalone relay operation and Meshtastic compatibility are not implemented or verified.
- The original app's readable interoperability remains unverified. Initial raw captures alone did not establish compatibility.
- The Channel field on Windows selects outgoing packet instructions; complete radio listening-configuration management is not implemented. Stay on the tested defaults.
- No comprehensive range, battery-runtime, congestion or multi-day stability testing has been performed. Nearby desktop/phone tests confirmed two-way messages and phone reception with the screen off.
- App settings, local messages, diagnostics and fixed coordinates are stored locally without additional application-level encryption. Reports may contain message text, identifiers and location; review them before sharing an issue report.

## Development

- Android build: [android/README.md](android/README.md).
- Windows tests: install `desktop/requirements.txt`, then run `python -m unittest discover -s desktop -p "test_*.py"`.
- Source provenance and dependencies: [PROVENANCE.md](PROVENANCE.md).
- First publication instructions: [PUBLISHING.md](PUBLISHING.md).

## License and credits

The community implementation and supplied icon are distributed under the [MIT license](LICENSE). Dependencies retain their own licenses. RADACAT names and original hardware/software belong to their respective owners. This project is not affiliated with or endorsed by the original manufacturer.

Created by a C1 owner through hardware testing and interoperability work, with implementation assistance from OpenAI Codex. Community fixes and tested hardware reports are welcome.
