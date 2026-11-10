# =====================================================================
# RestoFlow · Frontend (Angular) · Imagen de Docker
# Construcción en dos etapas (multi-stage):
#   1. "build": instala dependencias y compila Angular con Node
#   2. imagen final: solo Nginx sirviendo los archivos compilados
#      (sin Node, sin node_modules ni código fuente)
# Construir:  docker build -t restoflow-frontend:0.3.0 ./frontend
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

LABEL org.opencontainers.image.title="restoflow-frontend" \
      org.opencontainers.image.description="Aplicación web de RestoFlow (Angular servida con Nginx)" \
      org.opencontainers.image.version="0.3.0"

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/restoflow-frontend/browser /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:8080/ > /dev/null || exit 1
