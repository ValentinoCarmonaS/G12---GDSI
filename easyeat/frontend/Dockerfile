# ==============================================================================
# Etapa 1: Build de la SPA con Vite
# ==============================================================================
FROM node:20-alpine AS build

WORKDIR /app

# Inyección de variable de entorno en tiempo de compilación (AS-19, FR-020, Inv-FE-01)
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

# Copia de manifiestos y dependencias
COPY package*.json ./
RUN npm ci

# Copia del código fuente y configuraciones
COPY tsconfig*.json vite.config.ts tailwind.config.js postcss.config.js index.html ./
COPY src/ ./src/
COPY public/ ./public/

# Compilación de la aplicación estática
RUN npm run build

# ==============================================================================
# Etapa 2: Runtime con Nginx Alpine y puerto dinámico
# ==============================================================================
FROM nginx:alpine AS runtime

# Puerto por defecto con fallback a 80 si PORT no está definido (AS-20, FR-021, Q2, Inv-FE-02)
ENV PORT=80

# Copia de los estáticos generados por Vite
COPY --from=build /app/dist /usr/share/nginx/html

# Copia de la plantilla de configuración de Nginx para resolución de variable PORT
COPY nginx.conf /etc/nginx/templates/default.conf.template

# Arranque dinámico: envsubst resuelve $PORT en default.conf antes de iniciar Nginx
CMD ["/bin/sh", "-c", "envsubst '$PORT' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"]
