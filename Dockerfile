# 1) build stage (Gradle)
FROM gradle:jdk21-corretto AS builder
WORKDIR /app

# DL dependencies; use caching (skip tests -- dependency cache only)
ENV GRADLE_USER_HOME=/home/gradle/.gradle
COPY gradle.properties settings.gradle build.gradle ./
RUN gradle dependencies --build-cache || true

# copy in source code & build (add "-x test" to RUN to skip tests)
COPY src ./src
RUN gradle bootJar --build-cache

# 2) runtime stage
FROM amazoncorretto:21
WORKDIR /app

# add metadata
LABEL maintainer="Matt Chard"
LABEL description="Litter Application Container"
# LABEL version="xxxx"

# copy jar from builder, expose port 8080
COPY --from=builder /app/build/libs/litter.jar /app/litter.jar
EXPOSE 8080

# run the app
CMD ["java", "-jar", "/app/litter.jar"]