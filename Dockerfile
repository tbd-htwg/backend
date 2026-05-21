# Build trip-service, social-service, or external-info-service image:
#   docker build --build-arg SERVICE=trip -t tripplanning-trip-service .
#   docker build --build-arg SERVICE=social -t tripplanning-social-service .
#   docker build --build-arg SERVICE=external-info -t tripplanning-external-info-service .
ARG SERVICE=trip

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
ARG SERVICE

COPY pom.xml ./
COPY tripplanning-common/pom.xml tripplanning-common/
COPY tripplanning-trip-service/pom.xml tripplanning-trip-service/
COPY tripplanning-social-service/pom.xml tripplanning-social-service/
COPY tripplanning-external-info-service/pom.xml tripplanning-external-info-service/
RUN mvn -pl tripplanning-${SERVICE}-service -am dependency:go-offline -DskipTests

COPY tripplanning-common tripplanning-common
COPY tripplanning-trip-service tripplanning-trip-service
COPY tripplanning-social-service tripplanning-social-service
COPY tripplanning-external-info-service tripplanning-external-info-service
# Bust layer cache on redeploy (local-dev.sh passes CACHEBUST=$(date +%s)).
ARG CACHEBUST=0
RUN echo "cachebust=${CACHEBUST}" && mvn -pl tripplanning-${SERVICE}-service -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
ARG SERVICE
COPY --from=build /app/tripplanning-${SERVICE}-service/target/tripplanning-${SERVICE}-service-*.jar app.jar
RUN mkdir -p /app/db
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
