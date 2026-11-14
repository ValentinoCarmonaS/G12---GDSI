# Contrato Operativo 07: Enrutamiento SPA y Caché en Nginx

**Artefacto**: `easyeat/frontend/nginx.conf`  
**Propósito**: Asegurar que las recargas y accesos directos a rutas virtuales de React Router (`/checkout`, `/catalog`, `/reset-password`) sean absorbidas por el servidor web Nginx retornando `index.html` con código HTTP 200, evitando errores 404, y aplicando una política de caché que prevenga el desfasaje de versiones (HU-05, FR-019, AS-18, Q5).  

---

## 1. Especificación de la Configuración de Nginx

### 1.1. Reglas de Servidor y Reescritura
```nginx
server {
    listen ${PORT};
    server_name localhost;

    root /usr/share/nginx/html;
    index index.html;

    # Compresión gzip para activos de texto
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_proxied any;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript image/svg+xml;

    # Regla 1: Activos inmutables compilados por Vite con hash de contenido
    location /assets/ {
        try_files $uri =404;
        add_header Cache-Control "public, max-age=31536000, immutable";
        access_log off;
    }

    # Regla 2: Enrutamiento de reserva SPA y revalidación de index.html
    location / {
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-cache";
    }

    # Página de error estándar ante colapso interno
    error_page 500 502 503 504 /50x.html;
    location = /50x.html {
        root /usr/share/nginx/html;
    }
}
```

---

## 2. Invariantes del Contrato

1. **Inv-SPA-01 (Cero 404 en Rutas Virtuales)**: Toda solicitud HTTP `GET` a una ruta que no corresponda a un archivo estático real existente en disco (por ejemplo `/catalog`, `/checkout`, `/admin/mi-local`, `/verificar-email`) debe responder con código HTTP 200 OK y entregar el contenido íntegro de `/index.html` (AS-18).
2. **Inv-SPA-02 (Cero Cache-Lock en HTML)**: El archivo `index.html` debe servirse con la cabecera `Cache-Control: no-cache`, obligando a los navegadores a revalidar el documento con el servidor antes de usar la copia en caché. Esto garantiza que ante un nuevo despliegue en Render, los usuarios descarguen inmediatamente las referencias a los nuevos bundles JS (Q5/A5).
3. **Inv-SPA-03 (Caché Inmutable en Assets)**: Todos los archivos bajo `/assets/` generados por Vite (que incorporan hash en su nombre, ej. `index-B2x_9a.js`) deben servirse con `Cache-Control: public, max-age=31536000, immutable`, eliminando peticiones redundantes a la red (Q5/A5).

---

## 3. Matriz de Comportamiento HTTP por Ruta

| Ruta Solicitada | Tipo de Recurso | Código HTTP Retornado | Contenido Servido | Cabecera Cache-Control |
|---|---|---|---|---|
| `/` | Entrada raíz | 200 OK | `index.html` | `no-cache` |
| `/checkout` | Ruta virtual SPA | 200 OK | `index.html` | `no-cache` |
| `/catalog` | Ruta virtual SPA | 200 OK | `index.html` | `no-cache` |
| `/reset-password` | Ruta virtual SPA | 200 OK | `index.html` | `no-cache` |
| `/assets/main-1a2b.js` | Archivo físico JS | 200 OK | Script binario/texto | `public, max-age=31536000, immutable` |
| `/assets/inexistente.js`| Archivo estático ausente | 404 Not Found | Error 404 Nginx | - |

---

## 4. Verificación Observable

1. **Verificación de recarga en ruta virtual profunda**:
   ```sh
   curl -i http://localhost:80/checkout
   # Salida esperada:
   # HTTP/1.1 200 OK
   # Cache-Control: no-cache
   # ... (Contenido HTML con <div id="root"></div>)
   ```
2. **Verificación de caché inmutable en activos**:
   ```sh
   # Suponiendo un activo generado en dist/assets/
   ASSET_FILE=$(docker run --rm <frontend-image-id> ls /usr/share/nginx/html/assets | head -n 1)
   curl -I http://localhost:80/assets/$ASSET_FILE
   # Salida esperada:
   # HTTP/1.1 200 OK
   # Cache-Control: public, max-age=31536000, immutable
   ```

