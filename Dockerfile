# ---------- Build Stage ----------
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

COPY SeniorON/gradlew .
COPY SeniorON/gradle gradle
COPY SeniorON/build.gradle .
COPY SeniorON/settings.gradle .

RUN chmod +x gradlew

# dependency cache
RUN ./gradlew dependencies --no-daemon || true

COPY SeniorON/src src

RUN ./gradlew clean bootJar --no-daemon

# ---------- Runtime Stage ----------
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

ENV TZ=Asia/Seoul
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["java","-Xms128m","-Xmx256m","-jar","app.jar"]