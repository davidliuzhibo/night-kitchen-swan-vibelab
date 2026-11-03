# 夜宵食堂

用手势在夜市小摊翻炒、翻串、煮面，完成八道夜宵和三晚委托。应用包名为 com.david.nightkitchen，版本为 3.0.5-sound-exit-rc1，versionCode 为 110。

使用 JDK 17 或 21、Android SDK 35 和 PICO Spatial SDK 6.1.9。从仓库根目录运行 ./gradlew :app:testDebugUnitTest :app:assembleRelease。Windows 使用 gradlew.bat。首次运行需要联网。Gradle 8.13 发行包由华为云镜像提供，Wrapper 使用 Gradle 官方公布的 SHA-256 校验下载。其余依赖由 settings.gradle.kts 指定的仓库提供。

43 个 GLB 和三段音频均随源码提供，模型文件直接位于 app/src/main/assets/cookware。游戏离线运行。
