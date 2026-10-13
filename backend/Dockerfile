# =====================================================================
# RestoFlow · Backend (Spring Boot) · Imagen de Docker
# Construcción en dos etapas (multi-stage):
#   1. "build": compila el proyecto con Maven y genera el .jar
#   2. imagen final: solo Java (JRE) y el .jar, sin Maven ni código fuente
# Construir:  docker build -t restoflow-backend:0.2.0 ./backend
# =====================================================================

# ---------- Etapa 1: compilación ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Primero solo el pom.xml: las dependencias se descargan en una capa aparte y
# Docker la reutiliza mientras el pom.xml no cambie (construcciones más rápidas)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Las pruebas se ejecutan en el pipeline de GitHub Actions; aquí solo se empaqueta
RUN mvn -B -q package -DskipTests \
    && cp target/restoflow-backend-*.jar app.jar

# ---------- Etapa 2: imagen final ----------
FROM eclipse-temurin:17-jre-alpine

LABEL org.opencontainers.image.title="restoflow-backend" \
      org.opencontainers.image.description="API REST de RestoFlow (Spring Boot, arquitectura hexagonal)" \
      org.opencontainers.image.version="0.2.0"

# Usuario sin privilegios: el contenedor no se ejecuta como root
RUN addgroup -S restoflow && adduser -S -G restoflow restoflow

WORKDIR /app
COPY --from=build --chown=restoflow:restoflow /app/app.jar app.jar

USER restoflow

# Perfil por defecto; docker-compose lo cambia con la variable SPRING_PROFILES_ACTIVE
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

# Docker revisa cada 30 s que la API responda (endpoint público /api/salud)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/salud || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
