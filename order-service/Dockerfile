# syntax=docker/dockerfile:1

# Built from the repository root: docker build -f order-service/Dockerfile -t order-service .
# the jar does not depend on the platform: the build stage runs natively, the runtime stage once per platform
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -pl order-service -am package -Dmaven.test.skip=true -Djacoco.skip=true
# dependencies change least often, the application classes most often: one image layer each
RUN java -Djarmode=tools -jar order-service/target/order-service-*-exec.jar \
    extract --layers --launcher --destination /extracted

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
# owned by the app user, so a volume mounted here starts writable for it
RUN mkdir -p /var/log/app && chown app:app /var/log/app
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
