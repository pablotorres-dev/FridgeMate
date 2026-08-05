# syntax=docker/dockerfile:1

# --- Stage 1: build the Angular frontend ---
FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY Front/Angular/package.json Front/Angular/package-lock.json ./
RUN npm ci
COPY Front/Angular/ ./
RUN npm run build

# --- Stage 2: build the Spring Boot jar, bundling the frontend into it ---
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY Java/FridgeCalories/pom.xml ./
RUN mvn -B dependency:go-offline
COPY Java/FridgeCalories/src ./src
# The compiled SPA becomes a static resource of the jar, so one artifact
# serves both the API and the UI.
COPY --from=frontend /frontend/dist/fridgemate/browser/ ./src/main/resources/static/
RUN mvn -B clean package -DskipTests

# --- Stage 3: runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
