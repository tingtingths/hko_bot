FROM gradle:7-jdk11 as build

COPY . /app/
WORKDIR /app

# build jar
RUN gradle jar

# runner stage
FROM openjdk:11-jre-slim

COPY --from=build /app/build/libs/hko_bot.jar /

ENTRYPOINT ["java", "-jar", "/hko_bot.jar"]
