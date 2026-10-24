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

En una terminal, iniciar el servicio backend activando el perfil `dev` para ejecutar las migraciones de Flyway y la carga de datos de prueba:

```bash
cd easyeat/backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

El backend estará disponible en `http://localhost:8080`.

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
