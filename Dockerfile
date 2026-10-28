# syntax=docker/dockerfile:1
# The production image: `docker build -t extpulse .` from a clean checkout is enough.
# Tests are not run here; CI runs them before it builds the image.

# Build: compile, package, and split the jar into layers.
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /src
# Dependencies first: they change less than the code, so this layer is reused.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/extpulse-*.jar extract --layers --launcher --destination extracted

# Run: only the JRE and the app, as a user without privileges.
FROM eclipse-temurin:25-jre-noble
# Trust the certificate authorities Ubuntu trusts, not the JDK's own list, which lags
# behind: in October 2026 the JDK still trusted eMudhra's emSign ECC Root CA - G3 after
# keys under it leaked, while Ubuntu had dropped it. Security updates to the base image
# then reach the app with the next build.
RUN trust extract --overwrite --format=java-cacerts --filter=ca-anchors --purpose=server-auth \
        "$JAVA_HOME/lib/security/cacerts"
RUN useradd --system --uid 10001 --no-create-home extpulse
WORKDIR /app
# One layer per part of the jar, from least to most often changed, so an update to the
# code alone downloads a few hundred KB instead of every library.
COPY --from=build /src/extracted/dependencies/ ./
COPY --from=build /src/extracted/spring-boot-loader/ ./
COPY --from=build /src/extracted/snapshot-dependencies/ ./
COPY --from=build /src/extracted/application/ ./
USER extpulse
# Size the heap from the container's memory limit, and restart (exit) rather than limp
# along after running out of memory.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
