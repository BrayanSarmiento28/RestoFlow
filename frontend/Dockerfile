# =====================================================================
# RestoFlow · Frontend (Angular) · Imagen de Docker
# Construcción en dos etapas (multi-stage):
#   1. "build": instala dependencias y compila Angular con Node
#   2. imagen final: solo Nginx sirviendo los archivos compilados
#      (sin Node, sin node_modules ni código fuente)
# Construir:  docker build -t restoflow-frontend:1.1.1 ./frontend
# =====================================================================

# ---------- Etapa 1: compilación ----------
FROM node:24-alpine AS build
WORKDIR /app

# Primero solo package.json y package-lock.json: "npm ci" queda en una capa
# que Docker reutiliza mientras no cambien las dependencias
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
# Configuración de Angular: production (por defecto), preprod o development
ARG CONFIGURACION=production
RUN npx ng build --configuration "$CONFIGURACION"

# ---------- Etapa 2: imagen final ----------
# Nginx oficial en versión "unprivileged": corre sin root y escucha en el puerto 8080
FROM nginxinc/nginx-unprivileged:1.27-alpine

# La versión llega desde docker-compose (RESTOFLOW_VERSION), la misma de la etiqueta de la imagen
ARG VERSION=1.1.1
LABEL org.opencontainers.image.title="restoflow-frontend" \
      org.opencontainers.image.description="Aplicación web de RestoFlow (Angular servida con Nginx)" \
      org.opencontainers.image.version="${VERSION}"

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/restoflow-frontend/browser /usr/share/nginx/html

EXPOSE 8080

# 127.0.0.1 y no localhost: en Alpine, localhost apunta primero a la dirección IPv6 (::1) y Nginx escucha
# en IPv4, así que el chequeo fallaba y Docker mostraba "unhealthy" aunque el frontend funcionaba (RES-59)
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/ > /dev/null || exit 1
