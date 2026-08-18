@echo off
echo ========================================================
echo STREAMVAULT IPTV SYSTEM QUALITY ASSURANCE (QA) CHECK
echo ========================================================

echo [1/5] Cleaning project...
call gradlew.bat clean
if %ERRORLEVEL% NEQ 0 (
    echo [QA FAILED] Clean step failed with exit code %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)

echo [2/5] Running Unit Tests...
call gradlew.bat testDebugUnitTest
if %ERRORLEVEL% NEQ 0 (
    echo [QA FAILED] Unit tests failed with exit code %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)

echo [3/5] Running Lint...
call gradlew.bat lint
if %ERRORLEVEL% NEQ 0 (
    echo [QA FAILED] Lint failed with exit code %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)

echo [4/5] Building Debug APK...
call gradlew.bat assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo [QA FAILED] Debug build failed with exit code %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)

echo [5/5] Building Release APK...
call gradlew.bat assembleRelease
if %ERRORLEVEL% NEQ 0 (
    echo [QA FAILED] Release build failed with exit code %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)

echo ========================================================
echo [QA SUCCESS] All QA checks passed successfully!
echo ========================================================
exit /b 0
