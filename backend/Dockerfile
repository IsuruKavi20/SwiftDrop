
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
# Dependencies first: this layer is cached until pom.xml changes, so normal builds don't download the internet
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
# Tests already ran in CI ("Backend tests"); the image build only packages (tests are not even compiled)
RUN ./mvnw -B -q package -Dmaven.test.skip=true \
 && java -Djarmode=tools -jar target/backend-*.jar extract --layers --launcher --destination /extracted

# ---- 2. Run ----
FROM eclipse-temurin:21-jre-noble
# A normal user, not root: a bug in the app can't take over the container
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
# Layers from least to most often changed, so an update only ships the changed code layer
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER 10001:10001
# Use 75% of the container's memory limit for the heap; exit (and get restarted) instead of limping on OOM
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
# 8080 = API (public through the proxy), 8081 = health checks (private)
EXPOSE 8080 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
