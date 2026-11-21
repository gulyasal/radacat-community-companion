# Build the Android APK on Windows

This is a small Java/platform-API application; it does not use Gradle or bundled vendor code. The build script requires locally installed Android SDK tools and a JDK. None of the SDK, compiler JARs or signing keys is included in this repository.

Known working toolchain:

- JDK 21 (`JAVA_HOME`).
- Android SDK (`ANDROID_HOME`), platform `android-32` and build-tools `32.0.0`.
- R8/D8 `9.4.24`, downloaded separately from Google's Android Maven repository.

Download the compiler from `https://dl.google.com/dl/android/maven2/com/android/tools/r8/9.4.24/r8-9.4.24.jar` into `android/tools/r8.jar` (ignored by Git), then run:

```powershell
.\android\build.ps1
```

Use `-SdkDir`, `-JdkDir`, `-R8Jar`, `-BuildToolsVersion` and `-Platform` to specify installed paths/tool versions. The tested script is Windows-specific. Compiling against API 32 while declaring target 35 is a limitation of this initial build setup; future migration to a modern SDK/Gradle toolchain is desirable. Newer permission names are declared as manifest strings.

The default build generates a **local development key** in `android/development.keystore`. Its password is deliberately public (`android`); never use this key for published releases. The output is `android/build/Radacat-Community-Companion.apk`.

For a release, generate and securely back up a separate keystore and use:

```powershell
.\android\build.ps1 -KeyStore 'D:\private\release.jks' -KeyPasswordFile 'D:\private\password.txt' -KeyAlias companion
```

Keep both files outside the repository and outside release archives. The password file contains one password line; the build passes its filename to apksigner. Preserve the release signing identity for future APK updates.

Run the Java protocol checks with a JDK:

```powershell
New-Item -ItemType Directory -Force android/build/tests | Out-Null
javac -d android/build/tests android/src/com/mapmates/radacat/C1Protocol.java android/src/com/mapmates/radacat/GeoLinks.java android/src/com/mapmates/radacat/MessageGate.java android/tests/ProtocolTest.java
java -cp android/build/tests ProtocolTest
```

No phone backups or original APKs are needed to build or run these tests.
