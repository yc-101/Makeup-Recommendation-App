@echo off
"D:\\development\\Android Studio Sdk\\cmake\\3.22.1\\bin\\cmake.exe" ^
  "-HD:\\Desktop\\AndroidCamera2\\openCV\\libcxx_helper" ^
  "-DCMAKE_SYSTEM_NAME=Android" ^
  "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON" ^
  "-DCMAKE_SYSTEM_VERSION=29" ^
  "-DANDROID_PLATFORM=android-29" ^
  "-DANDROID_ABI=armeabi-v7a" ^
  "-DCMAKE_ANDROID_ARCH_ABI=armeabi-v7a" ^
  "-DANDROID_NDK=D:\\development\\Android Studio Sdk\\ndk\\23.1.7779620" ^
  "-DCMAKE_ANDROID_NDK=D:\\development\\Android Studio Sdk\\ndk\\23.1.7779620" ^
  "-DCMAKE_TOOLCHAIN_FILE=D:\\development\\Android Studio Sdk\\ndk\\23.1.7779620\\build\\cmake\\android.toolchain.cmake" ^
  "-DCMAKE_MAKE_PROGRAM=D:\\development\\Android Studio Sdk\\cmake\\3.22.1\\bin\\ninja.exe" ^
  "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=D:\\Desktop\\AndroidCamera2\\openCV\\build\\intermediates\\cxx\\Debug\\3p22603k\\obj\\armeabi-v7a" ^
  "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=D:\\Desktop\\AndroidCamera2\\openCV\\build\\intermediates\\cxx\\Debug\\3p22603k\\obj\\armeabi-v7a" ^
  "-DCMAKE_BUILD_TYPE=Debug" ^
  "-BD:\\Desktop\\AndroidCamera2\\openCV\\.cxx\\Debug\\3p22603k\\armeabi-v7a" ^
  -GNinja ^
  "-DANDROID_STL=c++_shared"
