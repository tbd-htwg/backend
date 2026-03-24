FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/db

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
