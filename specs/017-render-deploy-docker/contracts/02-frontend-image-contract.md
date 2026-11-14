# Contrato Operativo 02: Imagen Docker del Frontend

**Artefacto**: `easyeat/frontend/Dockerfile`, `easyeat/frontend/.dockerignore` y `easyeat/frontend/nginx.conf`  
**Propósito**: Empaquetar y servir los archivos estáticos transpilados de React SPA sobre un servidor HTTP Nginx Alpine liviano, resolviendo el puerto dinámicamente y admitiendo inyección de la URL de backend en build time (HU-05, FR-018 a FR-021, AS-17 a AS-20).  

---

## 1. Especificación del Manifiesto de Construcción

### 1.1. Etapas de Compilación y Runtime
- **Etapa 1 (Build)**:
  - Imagen base: `node:20-alpine`
  - Directorio de trabajo: `/app`
  - Argumentos de compilación:
    ```dockerfile
    ARG VITE_API_BASE_URL
    ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
    ```
  - Contenido copiado: `package*.json`, `tsconfig*.json`, `vite.config.ts`, `tailwind.config.js`, `postcss.config.js`, `index.html`, `src/`
  - Comandos de ejecución:
    ```sh
    npm ci
    npm run build
    ```
- **Etapa 2 (Runtime)**:
  - Imagen base: `nginx:alpine`
  - Directorio destino de estáticos: `/usr/share/nginx/html`
  - Contenido copiado: `/app/dist` generado en Etapa 1
  - Configuración de servidor: plantilla en `/etc/nginx/templates/default.conf.template` o script de entrada con resolución de variable `PORT`
  - Puerto por omisión: `ENV PORT=80`
  - Comando de inicio (`CMD`):
    ```sh
    CMD ["/bin/sh", "-c", "envsubst '$PORT' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"]
    ```

---

## 2. Invariantes del Contrato

1. **Inv-FE-01 (Inyección estática de API en build)**: La variable `VITE_API_BASE_URL` provista durante `docker build --build-arg VITE_API_BASE_URL=...` debe quedar compilada dentro de los bundles JS en `/usr/share/nginx/html/assets/`, sustituyendo las referencias relativas por la URL absoluta del backend en Render.
2. **Inv-FE-02 (Resolución de puerto con fallback)**: Nginx debe enlazar la escucha en el puerto especificado por la variable de entorno `PORT` en runtime. Si `PORT` no está definida o está vacía, debe aplicar fallback automático al puerto `80`.
3. **Inv-FE-03 (Ligereza del contenedor)**: La imagen final no debe contener `node_modules/`, código TypeScript fuente (`.ts`, `.tsx`), ni dependencias de desarrollo. El tamaño de imagen final debe ser inferior a 50 MB.
4. **Inv-FE-04 (Higiene de contexto)**: El contexto de compilación debe excluir `node_modules/` y `dist/` locales mediante `.dockerignore`.

---

## 3. Entradas y Salidas Operativas

### Entradas
- Argumento de build: `--build-arg VITE_API_BASE_URL=https://<backend-render>/api/v1` (en Render) o `/api/v1` (en local).
- Variable de entorno en runtime: `PORT=10000` (inyectada por Render) o vacía en local.

### Salidas
- Contenedor web Nginx sirviendo la SPA en el puerto `${PORT:-80}`.
- Peticiones HTTP a activos estáticos con hashes inmutables y reescritura virtual hacia `/index.html`.

---

## 4. Fallos Esperados y Mitigación

| Escenario de Falla | Comportamiento del Contenedor | Mitigación en Contrato |
|---|---|---|
| Omisión de `VITE_API_BASE_URL` en build de Render | La SPA emite llamadas a `/api/v1` relativo en Nginx, retornando 404 de la API | Declaración de `ARG VITE_API_BASE_URL` y documentación en Render Build Settings. |
| Variable `PORT` no provista por el entorno local | Nginx falla si espera obligatoriamente un número de puerto no nulo | `ENV PORT=80` por defecto en Dockerfile y fallback `${PORT:-80}`. |

---

## 5. Verificación Observable

1. **Inspección de puerto dinámico**:
   ```sh
   docker run --rm -d -e PORT=8081 -p 8081:8081 --name test-fe <frontend-image-id>
   curl -I http://localhost:8081/
   # Salida esperada: HTTP/1.1 200 OK (Server: nginx)
   docker stop test-fe
   ```
2. **Inspección de fallback al puerto 80**:
   ```sh
   docker run --rm -d -p 80:80 --name test-fe-default <frontend-image-id>
   curl -I http://localhost:80/
   # Salida esperada: HTTP/1.1 200 OK
   docker stop test-fe-default
   ```
3. **Inspección de API inyectada en bundles**:
   ```sh
   docker run --rm <frontend-image-id> sh -c "grep -o 'https://[^\" ]*' /usr/share/nginx/html/assets/*.js"
   # Debe contener la URL provista en VITE_API_BASE_URL
   ```

