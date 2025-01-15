# build stage: build the Litter app using Maven
# copy the pom.xml file + source code to work dir, then build w/ Maven
FROM maven:3-amazoncorretto-21-debian-bookworm AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

## Use when building at home (caching & offline mode for speedup). Use mvn clean package when in cloud
#RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests dependency:go-offline

# packaging stage: package app -> JAR
FROM amazoncorretto:21
COPY --from=builder /app/target/litter-0.0.1-SNAPSHOT.jar /app/
WORKDIR /app
CMD ["java", "-jar", "litter-0.0.1-SNAPSHOT.jar"]
