# Build trip-service, social-service, external-info-service, platform-service, or seed-job image:
#   docker build --build-arg SERVICE=trip -t tripplanning-trip-service .
#   docker build --build-arg SERVICE=social -t tripplanning-social-service .
#   docker build --build-arg SERVICE=external-info -t tripplanning-external-info-service .
#   docker build --build-arg SERVICE=platform -t tripplanning-platform-service .
#   docker build --build-arg SERVICE=seed-job -t tripplanning-seed-job .
ARG SERVICE=trip

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
ARG SERVICE

COPY pom.xml ./
COPY tripplanning-common/pom.xml tripplanning-common/
COPY tripplanning-trip-service/pom.xml tripplanning-trip-service/
COPY tripplanning-social-service/pom.xml tripplanning-social-service/
COPY tripplanning-external-info-service/pom.xml tripplanning-external-info-service/
COPY tripplanning-platform-service/pom.xml tripplanning-platform-service/
COPY tripplanning-seed-job/pom.xml tripplanning-seed-job/
RUN if [ "${SERVICE}" = "seed-job" ]; then \
      mvn -pl tripplanning-seed-job -am dependency:go-offline -DskipTests; \
    else \
      mvn -pl tripplanning-${SERVICE}-service -am dependency:go-offline -DskipTests; \
    fi

COPY tripplanning-common tripplanning-common
COPY tripplanning-trip-service tripplanning-trip-service
COPY tripplanning-social-service tripplanning-social-service
COPY tripplanning-external-info-service tripplanning-external-info-service
COPY tripplanning-platform-service tripplanning-platform-service
COPY tripplanning-seed-job tripplanning-seed-job
# Bust layer cache on redeploy (local-dev.sh passes CACHEBUST=$(date +%s)).
ARG CACHEBUST=0
RUN echo "cachebust=${CACHEBUST}" && if [ "${SERVICE}" = "seed-job" ]; then \
      mvn -pl tripplanning-seed-job -am package -DskipTests && \
      cp tripplanning-seed-job/target/tripplanning-seed-job-*.jar /app/app.jar; \
    else \
      mvn -pl tripplanning-${SERVICE}-service -am package -DskipTests && \
      cp tripplanning-${SERVICE}-service/target/tripplanning-${SERVICE}-service-*.jar /app/app.jar; \
    fi

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/app.jar app.jar
RUN mkdir -p /app/db
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
