@echo off
setlocal
set "MVNW_DIR=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.11"
set "MAVEN_HOME=%MVNW_DIR%\apache-maven-3.9.11"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  if not exist "%MVNW_DIR%" mkdir "%MVNW_DIR%"
  powershell -NoProfile -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip' -OutFile '%MVNW_DIR%\maven.zip'; Expand-Archive -Force '%MVNW_DIR%\maven.zip' '%MVNW_DIR%'; Remove-Item '%MVNW_DIR%\maven.zip'"
  if errorlevel 1 exit /b 1
)
call "%MAVEN_HOME%\bin\mvn.cmd" %*
