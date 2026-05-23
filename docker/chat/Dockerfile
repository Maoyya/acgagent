FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN mvn clean package -pl acg-chat -am -DskipTests

FROM eclipse-temurin:21-jre
COPY --from=build /app/acg-chat/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
