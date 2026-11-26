FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY chess-domain/pom.xml chess-domain/pom.xml
COPY chess-application/pom.xml chess-application/pom.xml
COPY chess-infrastructure/pom.xml chess-infrastructure/pom.xml
COPY chess-desktop/pom.xml chess-desktop/pom.xml
COPY chess-online-protocol/pom.xml chess-online-protocol/pom.xml
COPY chess-server/pom.xml chess-server/pom.xml
COPY chess-domain/src chess-domain/src
COPY chess-online-protocol/src chess-online-protocol/src
COPY chess-server/src chess-server/src
RUN mvn -B -ntp -pl chess-server -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG ROYAL_BUILD_REV=local
ENV ROYAL_BUILD_REV=${ROYAL_BUILD_REV}
WORKDIR /app
COPY --from=build /workspace/chess-server/target/chess-server.jar server.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=45.0 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true"
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
