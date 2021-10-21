# HKO Bot

[![Build Status](https://drone.itdog.me/api/badges/ting/hko_bot/status.svg)](https://drone.itdog.me/ting/hko_bot)

## Build
```shell
# package jar to build/libs
./gradlew jar
```

## Run
```shell
# long polling
java -jar build/libs/hko_bot.jar -u <bot username> -t <bot token> -f <persistent file>
# or webhook
java -jar build/libs/hko_bot.jar -u <bot username> -t <bot token> -f <persistent file> --webhook-base-url https://example.com --webhook-path verylongandrandomtexttoserveastoken
```
