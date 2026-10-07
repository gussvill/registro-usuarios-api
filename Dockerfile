# Base images are named by an exact version tag, never by a moving one such as "17-jre" or "latest".
# A tag can still be pushed again: for a release, append the digest reported by
# `docker buildx imagetools inspect <image>:<tag>` as <image>:<tag>@sha256:<digest>.

# Stage 1: build the jar with the Gradle wrapper of the repository and split it into layers.
FROM eclipse-temurin:17.0.20.1_1-jdk-noble AS builder
WORKDIR /builder
# The wrapper and the build scripts first: the Gradle distribution and the dependencies stay in
# their own layers until one of these files changes.
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
RUN ./gradlew --no-daemon --version
COPY src/ src/
# Tests run in the CI workflow and in `./gradlew build`; the image build only packages.
RUN ./gradlew --no-daemon bootJar -x test \
    && java -Djarmode=tools -jar build/libs/app.jar extract --layers --destination extracted

# Stage 2: runtime image with a JRE only.
FROM eclipse-temurin:17.0.20.1_1-jre-noble
WORKDIR /application
RUN groupadd --system app && useradd --system --gid app --no-create-home app
# Least-changing layers first, so a code change only replaces the last one.
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
USER app
EXPOSE 8080
# Heap as a share of the container limit instead of a fixed -Xmx that drifts from it.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
# TOKEN_SECRET (at least 32 bytes) is read from the environment at start-up and is deliberately
# not set here: without it the application falls back to its public, development-only default.
#   docker run -e TOKEN_SECRET=... -p 8080:8080 <image>
# No HEALTHCHECK instruction on purpose: whoever runs the container decides how to probe it.
ENTRYPOINT ["java", "-jar", "app.jar"]
