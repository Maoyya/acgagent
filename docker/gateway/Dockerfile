FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN mvn clean package -pl acg-gateway -am -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=build /app/acg-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
