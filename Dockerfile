FROM eclipse-temurin:21 as BUILD

COPY gradle/ /src/gradle
COPY settings.gradle.kts build.gradle.kts gradlew gradle.properties /src/
COPY src/ /src/src
WORKDIR /src
RUN ./gradlew --no-daemon shadowJar

FROM eclipse-temurin:21

COPY --from=BUILD /src/build/libs/coinmonitor.jar /bin/runner/run.jar
WORKDIR /bin/runner

CMD ["java","-jar","run.jar"]
