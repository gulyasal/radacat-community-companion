# Contributing

Please report your C1 model number, firmware version, host OS version and app version. Describe the expected and observed behavior and provide a short reproducible example.

Review diagnostics before sharing: remove message contents, coordinates, Bluetooth addresses, names and any unrelated phone information. Do not upload vendor APKs, phone backups or credentials.

Keep protocol changes covered by tests. For radio tests, distinguish an accepted local Bluetooth write from actual reception by a second device. Document the hardware and radio settings used; do not assume compatibility from matching packet sizes alone.

Before submitting changes, run the Python tests in the README and Java checks in android/README.md. Do not commit build output, dependency environments, local settings or signing keys. Contributions to the community implementation are made under its MIT license; do not contribute code or assets you do not have permission to share.
