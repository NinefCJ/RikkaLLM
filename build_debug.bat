@echo off
taskkill /F /IM java.exe >nul 2>&1
mkdir D:\.gradle >nul 2>&1
mkdir D:\gradle-tmp >nul 2>&1
cd /d e:\Code\RikkaLLM
set "GRADLE_USER_HOME=D:\.gradle"
set "TEMP=D:\gradle-tmp"
set "TMP=D:\gradle-tmp"
call gradlew assembleDebug --console=plain -Dgradle.user.home=D:\.gradle -Dorg.gradle.jvmargs=-Djava.io.tmpdir=D:\gradle-tmp > assemble-debug.log 2> assemble-debug.err
