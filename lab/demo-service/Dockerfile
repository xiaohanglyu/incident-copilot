# Built from the jar on the host (mvn -f demo-service package); keeps the image build
# free of a Maven download and a JDK 25 Maven image tag.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/payment-service.jar app.jar
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
