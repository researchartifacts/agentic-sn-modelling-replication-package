@echo off
rem Runs the shaded jar built by `mvn package` (target\netbench.jar).
java -jar "%~dp0target\netbench.jar" %*
