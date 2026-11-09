# Imagen única de producción (SOF-97, ADR-6): Spring Boot sirve la API y el build
# de React desde el mismo origen. Las pruebas corren en el CI, no aquí.

FROM node:20-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app/backend
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=backend /app/backend/target/marketplace-app-*.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
# Render entrega host, puerto y base por separado; la URL JDBC se arma aquí salvo
# que SPRING_DATASOURCE_URL ya venga definida.
ENTRYPOINT ["sh", "-c", "export SPRING_DATASOURCE_URL=\"${SPRING_DATASOURCE_URL:-jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}}\"; exec java $JAVA_OPTS -jar app.jar"]
