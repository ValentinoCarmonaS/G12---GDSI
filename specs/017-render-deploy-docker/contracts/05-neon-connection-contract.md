# Contrato Operativo 05: Conexión a Neon PostgreSQL y Resiliencia

**Artefacto**: `application.yml` (`spring.datasource.*`) y pool HikariCP  
**Propósito**: Garantizar la conectividad segura mediante cifrado TLS hacia la base de datos gestionada en Neon, admitiendo conexión directa para soportar bloqueos de Flyway y resiliencia ante la suspensión por inactividad (HU-04, FR-014, FR-015, AS-13, AS-14, Q7, Q11).  

---

## 1. Especificación de Parámetros JDBC y Pool

### 1.1. Configuración de Conexión
- **Cadena de Conexión Directa**: Provista por `SPRING_DATASOURCE_URL`, sin intermediarios `-pooler` (PgBouncer en modo transacción).
  - Formato: `jdbc:postgresql://<neon-endpoint>.neon.tech/<dbname>?sslmode=require`
- **Credenciales Externalizadas**:
  - Usuario: `SPRING_DATASOURCE_USERNAME`
  - Contraseña: `SPRING_DATASOURCE_PASSWORD`
- **Driver**: `org.postgresql.Driver`

### 1.2. Parámetros de Resiliencia en HikariCP
```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 30000       # 30 segundos de espera para tolerar el despertar de Neon (scale-to-zero)
      validation-timeout: 5000       # 5 segundos para validar socket activo con isValid()
      maximum-pool-size: 5           # Pool compacto adaptado al límite de 512 MB de RAM
      minimum-idle: 1                # 1 conexión persistente para reducir latencia inicial
      idle-timeout: 600000           # 10 minutos
      max-lifetime: 1800000          # 30 minutos
```

---

## 2. Invariantes del Contrato

1. **Inv-Neon-01 (Cifrado Mandatorio SSL/TLS)**: Toda comunicación con Neon debe negociar cifrado TLS (`sslmode=require`). Si se omite o rechaza el protocolo seguro, la conexión debe abortar de inmediato (AS-13).
2. **Inv-Neon-02 (Conexión Directa sin Pooler)**: La cadena JDBC no debe apuntar a endpoints que contengan `-pooler.neon.tech`. Esto garantiza la compatibilidad con bloqueos consultivos de PostgreSQL (`pg_advisory_lock`) requeridos por Flyway (Q7/A7).
3. **Inv-Neon-03 (Cero Credenciales en Código)**: Cero contraseñas o nombres de servidor fijados en `application.yml` para producción. La ausencia de variables en Render debe impedir el inicio de la aplicación (AS-14).
4. **Inv-Neon-04 (Tolerancia a Scale-To-Zero)**: Si Neon está en suspensión por inactividad (plan gratuito), HikariCP debe esperar hasta 30 segundos sin fallar inmediatamente con error de socket, permitiendo que la instancia de Neon despierte y complete la petición sin intervención humana (Q11/A11).

---

## 3. Fallos Esperados y Mitigación

| Escenario de Falla | Comportamiento | Mitigación en Contrato |
|---|---|---|
| Cadena de conexión con `sslmode=disable` | Neon rechaza la autenticación de plano | Forzar parámetro `sslmode=require` en `SPRING_DATASOURCE_URL`. |
| Despertar de Neon tarda 4 segundos tras inactividad | HikariCP podría abortar si el timeout fuera el estándar de 2 s | `connection-timeout: 30000` (30 segundos). |
| Conexión huérfana en el pool tras reinicio de Neon | Petición falla con broken pipe | Validación de conexiones activas antes de entregar al hilo (`isValid()`). |

---

## 4. Verificación Observable

1. **Verificación de conexión segura en logs**:
   Al iniciar Spring Boot contra Neon, los logs de HikariCP deben evidenciar la creación exitosa del pool:
   ```text
   HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@...
   HikariPool-1 - Start completed.
   ```
2. **Simulación de timeout y reconexión**:
   Iniciar el backend apuntando a una base de datos Neon suspendida; certificar que la aplicación no colapsa en el segundo 2 sino que aguarda y completa la conexión en el segundo 4–5.

