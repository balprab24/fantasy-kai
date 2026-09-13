# Two stages so the runtime image carries a JRE and a jar, not a JDK and a
# Maven cache. Both stages pin Temurin 25 -- the same runtime pom.xml's
# enforcer rule demands, so "works locally" and "works on Fly" mean the runtime.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Dependencies resolve in their own layer, keyed on the pom alone. A source
# edit then rebuilds in seconds instead of re-downloading Spring Boot.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests are skipped here on purpose: the suite is Testcontainers-based and
# wants a Docker daemon, which an image build does not have. CI runs
# `./mvnw -B verify` on every push and is the gate -- this stage only packages.
RUN ./mvnw -B -q clean package -DskipTests

# Alpine over the Ubuntu-based tag purely on measurement: 455 MB against
# 675 MB for an otherwise identical image, and every dependency here is pure
# Java, so musl costs nothing. Verified on the built image, not assumed:
# Argon2id registration, HS256 login and the Redis-backed rate limiter all
# behave the same as on glibc.
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Nothing here needs root, and Fly will happily run as it if asked.
RUN addgroup -S fantasykai && adduser -S -G fantasykai fantasykai
USER fantasykai

COPY --from=build --chown=fantasykai:fantasykai /build/target/backend-0.0.1-SNAPSHOT.jar app.jar

# MaxRAMPercentage rather than a fixed -Xmx: the heap then tracks whatever the
# machine is resized to instead of silently ignoring the change. It is not
# decoration -- StatIngestor holds a whole season of CSV records in memory and
# then a second full-size batch, so the daily pull is the peak, not a request.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
