# Contrato Operativo 01: Imagen Docker del Backend

**Artefacto**: `easyeat/backend/Dockerfile` y `easyeat/backend/.dockerignore`  
**Propósito**: Empaquetar y ejecutar la aplicación Spring Boot en una imagen liviana Alpine JRE 21, ejecutada por un usuario sin privilegios, con gobernanza estricta de memoria para no exceder 512 MB de RAM en Render (HU-01, FR-001 a FR-004, AS-1 a AS-3).  

---

## 1. Especificación del Manifiesto de Construcción

### 1.1. Etapas de Compilación y Runtime
- **Etapa 1 (Build)**:
  - Imagen base: `maven:3.9.9-eclipse-temurin-21-alpine`
  - Directorio de trabajo: `/workspace`
  - Contenido copiado: `pom.xml` y directorio `src/`
  - Comando de compilación: `mvn clean package -DskipTests -Djacoco.skip=true`
- **Etapa 2 (Runtime)**:
  - Imagen base: `eclipse-temurin:21-jre-alpine`
  - Creación de identidad de sistema:
    - Grupo: `easyeat` (GID del sistema)
    - Usuario: `easyeat` (UID del sistema, sin shell interactivo, sin permisos de superusuario / no-root)
  - Directorio de trabajo: `/app`
  - Contenido copiado: estrictamente el archivo `/workspace/target/*.jar` como `/app/app.jar`
  - Permisos de archivos: propiedad transferida a `easyeat:easyeat` (`chown -R easyeat:easyeat /app`)
  - Identidad de ejecución: `USER easyeat`
  - Comando de inicio (`ENTRYPOINT`):
    ```sh
    ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
    ```

---

## 2. Invariantes del Contrato

1. **Inv-Img-01 (Inexistencia de código fuente)**: La imagen final de runtime no debe contener archivos `.java`, archivos `.class` sueltos fuera del JAR, scripts `.sh` de desarrollo, ni herramientas Maven (`mvn`).
2. **Inv-Img-02 (Principio de menor privilegio)**: El proceso Java principal (`PID 1`) debe ejecutarse con UID correspondiente al usuario `easyeat` (diferente de UID 0 / root).
3. **Inv-Img-03 (Cálculo adaptativo de memoria)**: La JVM debe leer los límites de cgroups del contenedor mediante `-XX:+UseContainerSupport` y limitar el heap space al 75% (`-XX:MaxRAMPercentage=75.0`). Para un contenedor de 512 MB, el heap máximo admisible debe situarse entre 360 MB y 384 MB, preservando al menos 128 MB para Metaspace, stacks y memoria nativa.
4. **Inv-Img-04 (Higiene de contexto)**: El contexto de compilación debe excluir el directorio local `target/` mediante `.dockerignore`.

---

## 3. Entradas y Salidas Operativas

### Entradas
- Contexto de construcción en `easyeat/backend/`.
- Archivo `.dockerignore` con exclusión de `target/`, `.git/`, `.idea/`, `.env*`.
- Entorno de ejecución con límite de memoria asignado (ej. 512 MB en Render).

### Salidas
- Imagen de contenedor Docker ejecutable con tamaño inferior a 250 MB.
- Proceso Java escuchando en la red interna del contenedor bajo identidad `easyeat`.

---

## 4. Fallos Esperados y Mitigación

| Escenario de Falla | Comportamiento del Contenedor | Mitigación en Contrato |
|---|---|---|
| Compilación sin JDK 21 | Fallo determinístico en build time | Imagen base fijada en Temurin 21. |
| Exceso de memoria de la JVM en instancia de 512 MB | El kernel finaliza el proceso con SIGKILL (exit code 137 OOM) | Flags `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`. |
| Intento de escalada de privilegios dentro del contenedor | Rechazado por el kernel (Permiso denegado) | Usuario no root `USER easyeat`. |

---

## 5. Verificación Observable

1. **Inspección de usuario de ejecución**:
   ```sh
   docker run --rm <backend-image-id> whoami
   # Salida esperada: easyeat (o UID no cero)
   ```
2. **Inspección de ausencia de código fuente**:
   ```sh
   docker run --rm <backend-image-id> sh -c "find / -name '*.java' 2>/dev/null | wc -l"
   # Salida esperada: 0
   ```
3. **Inspección de flags JVM**:
   ```sh
   docker run --rm <backend-image-id> sh -c "ps aux | grep java"
   # Debe contener: -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
   ```

