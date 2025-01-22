FROM amazoncorretto:21
WORKDIR /app

# add metadata
LABEL maintainer="Matt Chard"
LABEL description="Litter backend container"

# copy jar
COPY litter.jar /app/litter.jar
EXPOSE 8080

# run the app
CMD ["java", "-jar", "/app/litter.jar"]