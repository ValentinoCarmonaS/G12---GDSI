# ==============================================================================
# Etapa 1: Build y empaquetado del artefacto Spring Boot con Maven y JDK 21
# ==============================================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /workspace

# Copiar configuración de dependencias y código fuente
COPY pom.xml .
COPY src ./src

# Compilar y empaquetar el JAR ejecutable omitiendo tests y cobertura
RUN mvn clean package -DskipTests -Djacoco.skip=true

# ==============================================================================
# Etapa 2: Runtime liviano JRE 21 Alpine con usuario sin privilegios y memoria gobernada
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Crear grupo y usuario no privilegiado 'easyeat' para aislar la ejecución
RUN addgroup -S easyeat && adduser -S easyeat -G easyeat

# Copiar exclusivamente el JAR generado desde la etapa de compilación
COPY --from=builder /workspace/target/*.jar /app/app.jar

# Transferir la propiedad de los archivos al usuario 'easyeat'
RUN chown -R easyeat:easyeat /app

# Conmutar la identidad de ejecución a usuario no-root
USER easyeat

# Exponer el puerto por defecto documentado
EXPOSE 8080

# Gobernanza estricta de memoria JVM para cgroups (límite de 512 MB de Render)
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

