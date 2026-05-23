FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN mvn clean package -pl acg-user -am -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=build /app/acg-user/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
