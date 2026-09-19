FROM eclipse-temurin:21-jre

LABEL org.opencontainers.image.source="https://github.com/okapi-core/engine"

WORKDIR /app

ARG JAR_FILE=target/okapi-ops-*.jar
RUN echo "Copying file ${JAR_FILE}"
COPY ${JAR_FILE} /app/app.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]
