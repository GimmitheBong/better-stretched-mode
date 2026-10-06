# Third-party notices

Better Stretched Mode's original plugin code is licensed under the BSD 2-Clause License in [`LICENSE`](LICENSE). The same full text is included in the plugin JAR at `META-INF/LICENSE`.

## Gradle Wrapper

The build tooling includes generated `gradlew` and `gradlew.bat` scripts and `gradle/wrapper/gradle-wrapper.jar`, from [Gradle](https://github.com/gradle/gradle/tree/v8.8.0). These are licensed under the **Apache License, Version 2.0**, rather than this plugin's BSD license.

The original script copyright/license headers are retained. The wrapper JAR retains its bundled `META-INF/LICENSE`. A full copy of the Apache License is provided in [`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt).

The wrapper files are development/build tooling and are not bundled in the plugin JAR. RuneLite is used as an external compile-only runtime dependency; its classes are not copied into the plugin JAR.
