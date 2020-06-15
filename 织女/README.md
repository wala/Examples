织女 is an example of taint analysis across mutiple languages in [WALA](https://github.com/wala/WALA).

织女 (織女, zhī nǚ, Zhinü, [pronounciation](https://www.mdbg.net/chinese/dictionary?wdrst=0&popup=1&wdqchssp=%E7%BB%87%E5%A5%B3 )) is a [weaver goddess in Chinese legend](https://medium.com/saseprints/chinese-folktale-the-cowherd-and-the-weaver-girl-cd045f934a6).  织女 is a fitting name for our analysis:
1. 织女 uses the cross-language nature of WALA to weave together a single taint analysis for four different languages based on the Common AST (CAst) and components from the WALA core.
2. In legend, 织女 the goddess was one of those who used the 鹊桥 (Magpie Bridge), and 织女 the taint analysis uses [MagpieBridge](https://github.com/MagpieBridge/MagpieBridge).

### Building

While 织女 itself is straightforward to build, it has several dependencies that need to be built first:
1. The Python components depend on Jython3.  That can be tedious to build, so we provide [pre-built Maven jars](https://github.com/wala/Examples/blob/master/%E7%BB%87%E5%A5%B3/maven/); simply unpack them in your Maven repository directory.
2. [WALA](https://github.com/wala/WALA) has extensive build help on the site.  Since 织女 is built with Maven, you should build WALA with `gradlew build publishToMavenLocal`.
3. [MagpieBridge](https://github.com/MagpieBridge/MagpieBridge) can be built using `mvn clean install`.  Note that, for now, 织女 needs to use the 'ibm' branch of MagpieBridge, but we anticipate that changing soon.
4. Build 织女 with `mvn clean install` in its top-level directory.
