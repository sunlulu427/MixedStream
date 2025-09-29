# Updates

- assigned ndk version in build.gradle
- bump Gradle 8.6 / AGP 8.4.2 / Kotlin 1.9.24 / JDK 17
- removed bintray-release plugin
- use mavenCentral instead of jcenter
- use source code mode instead of binary mode
- add property in local.properties
```properties
sdk.dir=/Users/bytedance/Library/Android/sdk
ndk.dir=/Users/bytedance/Library/Android/sdk/ndk/27.1.12297006
cmake.dir=/Users/bytedance/Library/Android/sdk/cmake/3.22.1
```

- document Clean Architecture principles & expose `LiveStreamSession` 抽象，`AVLiveView` 支持注入自定义推流会话
- add GitHub Actions workflow to run Gradle builds on every push/PR
- add PlantUML rendering step in CI + `tools/render_docs.sh`

- update default preview resolution and push resolution
- change camera facing to back (for emulator)
- using canvas instead of TextView for watermark
- H264码流分析工具
  - h264bitstream: https://github.com/aizvorski/h264bitstream
  - ```shell
    brew install h264bitstream
    h264_analyze stream-110236189491724767.h264 -o stream-110236189491724767.h264.txt
    ```
  - JM: https://vcgit.hhi.fraunhofer.de/jvet/JM
- h265码率分析工具
  - hevcbrowser: https://github.com/virinext/hevcesbrowser
  - HM: https://vcgit.hhi.fraunhofer.de/jvet/HM
- 二进制文件编辑器
  - Hex Fiend: https://github.com/HexFiend/HexFiend
