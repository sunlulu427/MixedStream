# Updates

- assigned ndk version in build.gradle
- removed bintray-release plugin
- use mavenCentral instead of jcenter
- use source code mode instead of binary mode
- add property in local.properties
```properties
sdk.dir=/Users/bytedance/Library/Android/sdk
ndk.dir=/Users/bytedance/Library/Android/sdk/ndk/21.1.6352462
cmake.dir=/Users/bytedance/Library/Android/sdk/cmake/3.10.2.4988404
```

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