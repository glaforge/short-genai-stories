FROM azul/zulu-openjdk-distroless:21-latest
COPY target/dependency-jars /app/dependency-jars
COPY target/application.jar /app/application.jar
ENTRYPOINT ["java", "-jar", "app/application.jar", "-Dexec.mainClass=\"storygen.ExplicitStoryGeneratorAgent\""]