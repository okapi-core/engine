FROM eclipse-temurin:21-jre

LABEL org.opencontainers.image.title="Okapi Oscar" \
      org.opencontainers.image.description="Okapi Oscar is an AI triaging agent that ships with Okapi. Internally, each Oscar session has an internal toolkit for querying the Okapi cluster. Oscar doesn't have raw ClickHouse access. Its only dependency is Postgres, which is used to save application data. Similar to ingester, Oscar should live in an internal network and not be exposed to users." \
      org.opencontainers.image.source="https://github.com/okapi-core/engine"

WORKDIR /app

# Copy the prepackaged Spring Boot fat jar
ARG JAR_FILE=target/okapi-oscar-*.jar
RUN echo "Copying file ${JAR_FILE}"
COPY ${JAR_FILE} /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
