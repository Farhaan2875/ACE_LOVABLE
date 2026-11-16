# ---------- build the Spring Boot jar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

# ---------- runtime ----------
FROM eclipse-temurin:21-jre
# Node.js is needed by the "local" preview runner (npm install + vite). Not needed when using the kubernetes runner.
COPY --from=node:22-slim /usr/local/bin/node /usr/local/bin/node
COPY --from=node:22-slim /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
 && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx \
 && useradd -m -u 10001 ace && mkdir -p /data && chown ace /data
USER ace
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
ENV ACE_STORAGE_LOCAL_ROOT=/data/projects \
    ACE_EXECUTION_LOCAL_WORK_DIR=/data/previews \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
