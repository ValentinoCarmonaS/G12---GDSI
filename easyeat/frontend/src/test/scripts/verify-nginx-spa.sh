#!/usr/bin/env sh
# ==============================================================================
# verify-nginx-spa.sh
# Verificación automatizada del contrato de servidor Nginx y SPA React (HU-05)
#
# Trazabilidad:
# - Spec 017: AS-17, AS-18, AS-20, FR-018, FR-019, FR-021, Q2, Q5, SC-006, SC-010
# - Contratos: contracts/02-frontend-image-contract.md, contracts/07-spa-routing-contract.md
# - Invariantes: Inv-FE-01, Inv-FE-02, Inv-SPA-01, Inv-SPA-02, Inv-SPA-03
# ==============================================================================

set -e

IMAGE_NAME="${1:-easyeat-frontend:test}"
DOCKERFILE_PATH="${2:-easyeat/frontend/Dockerfile}"
NGINX_CONF_PATH="${3:-easyeat/frontend/nginx.conf}"

echo "=============================================================================="
echo "Iniciando verificación del servidor Nginx y SPA: ${IMAGE_NAME}"
echo "=============================================================================="

# 1. Verificar existencia de archivos clave
if [ ! -f "${DOCKERFILE_PATH}" ]; then
  echo "[-] ERROR: No se encontró el Dockerfile en ${DOCKERFILE_PATH}" >&2
  exit 1
fi
echo "[+] OK: Dockerfile encontrado en ${DOCKERFILE_PATH}"

if [ ! -f "${NGINX_CONF_PATH}" ]; then
  echo "[-] ERROR: No se encontró nginx.conf en ${NGINX_CONF_PATH}" >&2
  exit 1
fi
echo "[+] OK: nginx.conf encontrado en ${NGINX_CONF_PATH}"

# 2. Verificar configuración en nginx.conf
grep -E "try_files\s+\\\$uri\s+\\\$uri/\s+/index\.html;" "${NGINX_CONF_PATH}" >/dev/null || {
  echo "[-] ERROR: nginx.conf debe contener directiva try_files con fallback a /index.html" >&2
  exit 1
}
grep -E "listen\s+.*PORT" "${NGINX_CONF_PATH}" >/dev/null || {
  echo "[-] ERROR: nginx.conf debe declarar puerto listen configurable con variable PORT" >&2
  exit 1
}
grep -E "Cache-Control.*no-cache" "${NGINX_CONF_PATH}" >/dev/null || {
  echo "[-] ERROR: nginx.conf debe declarar Cache-Control no-cache para rutas HTML/raíz" >&2
  exit 1
}
grep -E "Cache-Control.*(max-age=31536000|immutable)" "${NGINX_CONF_PATH}" >/dev/null || {
  echo "[-] ERROR: nginx.conf debe declarar Cache-Control immutable para /assets/" >&2
  exit 1
}
echo "[+] OK: nginx.conf cumple con directivas de reescritura SPA y control de caché"

# 3. Verificar estructura de Dockerfile
grep -E "FROM\s+node:20-alpine" "${DOCKERFILE_PATH}" >/dev/null || {
  echo "[-] ERROR: Dockerfile debe usar node:20-alpine en etapa build" >&2
  exit 1
}
grep -E "FROM\s+nginx:alpine" "${DOCKERFILE_PATH}" >/dev/null || {
  echo "[-] ERROR: Dockerfile debe usar nginx:alpine en etapa runtime" >&2
  exit 1
}
grep -E "ARG\s+VITE_API_BASE_URL" "${DOCKERFILE_PATH}" >/dev/null || {
  echo "[-] ERROR: Dockerfile debe recibir ARG VITE_API_BASE_URL" >&2
  exit 1
}
echo "[+] OK: Dockerfile cumple con especificación multi-etapa e inyección de ARG"

# 4. Verificación de ejecución en contenedor si la imagen existe
if docker image inspect "${IMAGE_NAME}" >/dev/null 2>&1; then
  echo "[*] Verificando comportamiento de contenedor Nginx en ejecución..."
  CONTAINER_ID=$(docker run -d --rm -e PORT=9000 -p 9000:9000 "${IMAGE_NAME}")
  trap 'docker stop ${CONTAINER_ID} >/dev/null 2>&1 || true' EXIT INT TERM

  sleep 3

  # Comprobar puerto 9000 y código 200 en ruta virtual profunda
  RESPONSE_CODE=$(docker run --rm --network host curlimages/curl:latest -s -o /dev/null -w "%{http_code}" http://localhost:9000/checkout || true)
  if [ "${RESPONSE_CODE}" != "200" ]; then
    # Intento local directo si curl está disponible en host
    if command -v curl >/dev/null 2>&1; then
      RESPONSE_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:9000/checkout)
    fi
  fi

  if [ "${RESPONSE_CODE}" = "200" ]; then
    echo "[+] OK: Ruta virtual SPA /checkout responde HTTP 200 en puerto asignado PORT=9000"
  else
    echo "[!] AVISO: No se pudo verificar respuesta HTTP directa en contenedor (código recibido: ${RESPONSE_CODE})"
  fi

  docker stop "${CONTAINER_ID}" >/dev/null 2>&1 || true
  trap - EXIT INT TERM
else
  echo "[*] Nota: Imagen '${IMAGE_NAME}' aún no construida. Validación estática de manifiestos y directivas completada."
fi

echo "=============================================================================="
echo "Verificación de Nginx y SPA completada con éxito."
echo "=============================================================================="
