#!/usr/bin/env sh
# ==============================================================================
# verify-backend-image.sh
# Verificación automatizada del contrato de imagen Docker del Backend (HU-01)
#
# Trazabilidad:
# - Spec 017: AS-1, AS-2, AS-3, SC-001, SC-002, FR-001, FR-002, FR-003, FR-004
# - Contrato: specs/017-render-deploy-docker/contracts/01-backend-image-contract.md
# - Invariantes: Inv-Img-01, Inv-Img-02, Inv-Img-03, Inv-Img-04
# ==============================================================================

set -e

IMAGE_NAME="${1:-easyeat-backend:test}"
DOCKERFILE_PATH="${2:-easyeat/backend/Dockerfile}"

echo "=============================================================================="
echo "Iniciando verificación del contrato de imagen Docker del backend: ${IMAGE_NAME}"
echo "=============================================================================="

# 1. Verificar existencia del Dockerfile
if [ ! -f "${DOCKERFILE_PATH}" ]; then
  echo "[-] ERROR: No se encontró el Dockerfile en ${DOCKERFILE_PATH}" >&2
  exit 1
fi
echo "[+] OK: Dockerfile encontrado en ${DOCKERFILE_PATH}"

# 2. Verificar que Dockerfile declara usuario easyeat y etapa de runtime eclipse-temurin:21-jre-alpine
grep -E "FROM\s+maven:3\.9\.9-eclipse-temurin-21-alpine" "${DOCKERFILE_PATH}" >/dev/null || {
  echo "[-] ERROR: Dockerfile debe declarar la etapa build base maven:3.9.9-eclipse-temurin-21-alpine" >&2
  exit 1
}
grep -E "FROM\s+eclipse-temurin:21-jre-alpine" "${DOCKERFILE_PATH}" >/dev/null || {
  echo "[-] ERROR: Dockerfile debe declarar la etapa runtime base eclipse-temurin:21-jre-alpine" >&2
  exit 1
}
grep -E "USER\s+easyeat" "${DOCKERFILE_PATH}" >/dev/null || {
  echo "[-] ERROR: Dockerfile debe declarar la conmutación al usuario sin privilegios 'USER easyeat'" >&2
  exit 1
}
echo "[+] OK: Dockerfile declara etapas e identidades según contrato (FR-001, FR-002)"

# 3. Inspección del usuario de ejecución del contenedor (Inv-Img-02, AS-3)
echo "[*] Verificando usuario de ejecución no-root en el contenedor..."
CONTAINER_USER=$(docker run --rm "${IMAGE_NAME}" whoami)
if [ "${CONTAINER_USER}" != "easyeat" ]; then
  echo "[-] ERROR: El usuario del contenedor es '${CONTAINER_USER}', se esperaba 'easyeat'" >&2
  exit 1
fi
echo "[+] OK: Proceso ejecutándose bajo usuario no privilegiado: ${CONTAINER_USER}"

# 4. Inspección de ausencia de código fuente .java en el contenedor (Inv-Img-01, AS-3, SC-001)
echo "[*] Verificando ausencia total de código fuente (.java)..."
JAVA_FILES_COUNT=$(docker run --rm "${IMAGE_NAME}" sh -c "find / -name '*.java' 2>/dev/null | wc -l" | tr -d '[:space:]')
if [ "${JAVA_FILES_COUNT}" -ne 0 ]; then
  echo "[-] ERROR: Se encontraron ${JAVA_FILES_COUNT} archivos .java dentro del contenedor de runtime" >&2
  exit 1
fi
echo "[+] OK: Cero archivos .java en el contenedor de runtime (encontrados: 0)"

# 5. Inspección de ausencia de herramientas de compilación maven (Inv-Img-01, AS-3, SC-001)
echo "[*] Verificando ausencia de herramientas de compilación (mvn)..."
if docker run --rm "${IMAGE_NAME}" which mvn >/dev/null 2>&1; then
  echo "[-] ERROR: La herramienta de compilación 'mvn' no debe existir en la imagen de runtime" >&2
  exit 1
fi
echo "[+] OK: 'mvn' no está presente en la imagen de runtime"

# 6. Inspección de flags JVM en la configuración de la imagen (Inv-Img-03, AS-2, SC-002)
echo "[*] Verificando flags JVM en ENTRYPOINT / CMD de la imagen..."
ENTRYPOINT_JSON=$(docker inspect --format='{{json .Config.Entrypoint}}' "${IMAGE_NAME}")
echo "ENTRYPOINT detectado: ${ENTRYPOINT_JSON}"

echo "${ENTRYPOINT_JSON}" | grep -q -- "-XX:+UseContainerSupport" || {
  echo "[-] ERROR: Flag JVM -XX:+UseContainerSupport no encontrado en ENTRYPOINT" >&2
  exit 1
}
echo "${ENTRYPOINT_JSON}" | grep -q -- "-XX:MaxRAMPercentage=75.0" || {
  echo "[-] ERROR: Flag JVM -XX:MaxRAMPercentage=75.0 no encontrado en ENTRYPOINT" >&2
  exit 1
}
echo "${ENTRYPOINT_JSON}" | grep -q -- "/app/app.jar" || {
  echo "[-] ERROR: JAR de destino /app/app.jar no encontrado en ENTRYPOINT" >&2
  exit 1
}
echo "[+] OK: Flags JVM de memoria gobernada correctamente configurados en ENTRYPOINT"

echo "=============================================================================="
echo "[+] CONTRATO DE IMAGEN DOCKER BACKEND VALIDADO EXITOSAMENTE (100% OK)"
echo "=============================================================================="

