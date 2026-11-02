# EasyEat

Sistema de autenticación y gestión para locales gastronómicos y comensales.

## Requisitos previos

- Docker y Docker Compose
- Java 21
- Apache Maven 3.9+
- Node.js 18+ y npm

## Instrucciones para levantar el proyecto

### 1. Servicios auxiliares (base de datos y correo de prueba)

Iniciar los contenedores de PostgreSQL y Mailpit definidos en el backend:

```bash
cd easyeat/backend
docker compose up -d
```

- PostgreSQL queda expuesto en el puerto `5432` (usuario: `easyeat`, contraseña: `easyeat_dev_password`, base de datos: `easyeat`).
- La interfaz web de Mailpit queda accesible en `http://localhost:8025` (servidor SMTP en puerto `1025`).

### 2. Backend (Spring Boot)

#### Configuración por defecto (Mailpit local)

Por defecto, el backend está preconfigurado para conectarse a PostgreSQL en `localhost:5432` y despachar correos transaccionales a Mailpit en `localhost:1025` con remitente `noreply@easyeat.com`, sin requerir variables de entorno adicionales:

```bash
cd easyeat/backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

El backend estará disponible en `http://localhost:8080`.

#### Configuración personalizada o relay externo

Para utilizar un servidor SMTP real (como Gmail con Contraseña de Aplicación) o servicios dedicados (Brevo, SendGrid), consultar las variables disponibles en `easyeat/backend/.env.example` y exportarlas en la sesión de terminal antes de iniciar la aplicación:

```bash
export SPRING_MAIL_HOST=smtp.gmail.com
export SPRING_MAIL_PORT=587
export SPRING_MAIL_USERNAME="tu_correo@gmail.com"
export SPRING_MAIL_PASSWORD="tu_app_password"
export SPRING_MAIL_SMTP_AUTH=true
export SPRING_MAIL_STARTTLS_ENABLE=true
export APP_MAIL_FROM="tu_correo@gmail.com"

cd easyeat/backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. Frontend (React + Vite)

En otra terminal, instalar dependencias y levantar el servidor de desarrollo:

```bash
cd easyeat/frontend
npm install
npm run dev
```

La aplicación web estará disponible en `http://localhost:3000`. Las peticiones a `/api` son redirigidas automáticamente al backend en `http://localhost:8080`.

## Cuentas y credenciales de prueba

El entorno de desarrollo (`dev`) inicializa automáticamente los siguientes usuarios con sus roles y credenciales:

| Rol | Correo electrónico | Contraseña | Descripción |
| --- | --- | --- | --- |
| ADMINISTRADOR | `admin@easyeat.com` | `SecurePass123` | Administrador general de la plataforma |
| ADMINISTRADOR | `laura.admin@cafeparque.com` | `Password123` | Administrador de local (Café Parque) |
| ENCARGADO | `maria.encargada@laesquina.com` | `Password123` | Encargado de salón y mostrador (La Esquina) |
| CHEF | `carlos.chef@comedorcentral.com` | `Password123` | Personal de cocina (Comedor Central) |
| COMENSAL | `juan.comensal@gmail.com` | `Password123` | Usuario comensal |

## Carrito de compras y datos demográficos del comensal (Feature 009)

Módulo implementado con arquitectura Spec-Driven Development (SDD):

- **Selector numérico acotado al stock real en cocina (COM-6.1.1)**: Permite seleccionar cantidades en el modal de detalle del plato e incorporar órdenes acumulando ítems sin duplicar filas.
- **Botón flotante reactivo y cálculo monetario exacto en centavos (COM-6.1.2)**: Visibilidad biyectiva contextual (visible sólo si hay ítems en el catálogo), cálculo aritmético en centavos enteros para eliminar drift de punto flotante IEEE 754.
- **Modificación interactiva, guardas de stock y rollback ante fallos de red (COM-6.1.3)**: Modificación con debounce de 300 ms, bloqueo visual al alcanzar stock límite de cocina (`POST /api/v1/cart/validate-stock`), remoción en cantidad 1 y reversión optimista determinista ante desconexión o errores del servidor.
- **Datos demográficos y avatar de perfil (COM-1.4.2)**: Edición de edad biológica (14 a 120 años), género y domicilio de contacto (`PUT /api/v1/profile/demographics`). Carga de avatar JPEG/PNG/WebP de hasta 5 MB persistido en base de datos PostgreSQL mediante Flyway `V12__extend_user_demographics_and_avatar.sql` (`BYTEA`), con eliminación diferida sujeta a confirmación en formulario.
