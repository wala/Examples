#### Testing

The simpest way to test 织女 once you have built it is to run it on the command line against our test programs in the `data` directory.  For three of the languages, just supplying the demo as the sole argument suffices, but the Android Java analysis needs to be told where to find an Android jar.  The four commands are as follows, assuming you are in the top-level directory of this project (i.e. 织女) 

##### Python

`java -jar target/zhinu-0.0.1-SNAPSHOT-织女.jar data/demo.py`

##### HTML

`java -jar target/zhinu-0.0.1-SNAPSHOT-织女.jar data/demo.html`

##### JavaScript

`java -jar target/zhinu-0.0.1-SNAPSHOT-织女.jar data/demo.js`

##### Android Java

`java -DandroidJar=/path/to/recent/android.jar -jar target/zhinu-0.0.1-SNAPSHOT-织女.jar data/Demo/src/edu/mit/dynamic_dispatch/MainActivity.java`
