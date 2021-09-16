# resolve and cache dependencies, for subsequence build
FROM gradle:7-jdk11 as cache

COPY *.gradle.kts /app/
COPY *.properties /app/

WORKDIR /app/
RUN gradle clean build

FROM gradle:7-jdk11 as build

COPY --from=cache /home/gradle/.gradle /home/gradle/.gradle
COPY . /app/
WORKDIR /app/

# build jar
RUN gradle jar

# runner stage
FROM openjdk:11-jre-slim

COPY --from=build /app/build/libs/hko_bot.jar /

ENTRYPOINT ["java", "-jar", "/hko_bot.jar"]
