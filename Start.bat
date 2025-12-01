@echo off
echo Project Start Ho Raha Hai...
javac -cp ".;mysql-connector-j-9.5.0.jar" VotingSystem.java
java -cp ".;mysql-connector-j-9.5.0.jar" VotingSystem
pause