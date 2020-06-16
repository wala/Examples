### Building

While 织女 itself is straightforward to build, it has several dependencies that need to be built first:
1. Building WALA makes use of Java 8 and a C++ compiler, so make sure you have them on your system.
2. To use 织女 with Android Java, make sure you have the Android SDK installed, with a recent `android.jar`
3. The Python components depend on Jython3.  That can be tedious to build, so we provide [pre-built Maven jars](https://github.com/wala/Examples/blob/master/%E7%BB%87%E5%A5%B3/maven/); simply unpack them in your Maven repository directory.
4. [WALA](https://github.com/wala/WALA) has extensive build help on the site.  Since 织女 is built with Maven, you should build WALA with `gradlew build publishToMavenLocal`.
5. [WALA IDE](https://github.com/wala/IDE) is needed for WALA Python.  Clone it, and then build it by running `mvn clean install` in its `com.ibm.wala.cast.lsp` directory.
6. [WALA ML](https://github.com/wala/ML) is needed for 织女.  Clone it and then build it with `mvn clean install`.
7. [MagpieBridge](https://github.com/MagpieBridge/MagpieBridge) can be built using `mvn clean install`.  Note that, for now, 织女 needs to use the 'ibm' branch of MagpieBridge, but we anticipate that changing soon.
8. Build 织女 with `mvn clean install` in its top-level directory.
