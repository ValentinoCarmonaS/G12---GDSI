# Justificación de Estimaciones Técnicas: MVP Final

**Proyecto:** EasyEat (Gestión Gastronómica SaaS Multi-Tenant)  
**Fecha:** 2026-10-14  
**Equipo:** Grupo 12 (6 integrantes, FIUBA GDSI 2026)  
**Alcance cubierto:** 82 Historias de Usuario (279 Story Points en 6 Sprints)  
**Documentos relacionados:**
- [`01 - Decisiones de Planificación del MVP.md`](./01%20-%20Decisiones%20de%20Planificaci%C3%B3n%20del%20MVP.md) (Registro de decisiones de planificación)
- [`02 - Roadmap de Sprints del MVP.md`](./02%20-%20Roadmap%20de%20Sprints%20del%20MVP.md) (Distribución de historias por sprint)
- [`04 - Proceso de Ajuste y Respuesta al Product Owner.md`](./04%20-%20Proceso%20de%20Ajuste%20y%20Respuesta%20al%20Product%20Owner.md) (Bitácora de negociación y proceso dialéctico)

---

## Introducción

Este documento consolida la justificación técnica de estimación en Story Points (SP) de las 82 historias de usuario que componen el backlog del MVP Final. Cada estimación se desglosa en sus tres dimensiones analíticas:

- **Complejidad:** Dificultad técnica intrínseca, diseño arquitectónico, consultas SQL/NoSQL e interfaces de usuario.
- **Incertidumbre:** Dependencia de librerías externas, APIs de terceros (SDKs de pago) o protocolos de red en tiempo real.
- **Validación:** Cobertura de pruebas unitarias, de integración, edge cases y manejo de errores.

Se incluye en el título de cada historia la referencia al Sprint asignado en el roadmap.

---

## COMENSAL

### COM-1.1.1 Crear cuenta con mail y contraseña (3 SP - Sprint 1)
*   **Complejidad:** Baja-Media (CRUD estándar).
*   **Incertidumbre:** Nula.
*   **Validación:** Alta (muchos edge cases de validación de inputs y correos duplicados).

### COM-1.2.1 Login con mail y contraseña (3 SP - Sprint 1)
*   **Complejidad:** Media. Al ser el primer módulo de acceso en desarrollarse dentro de un proyecto virgen (greenfield), absorbe la construcción de la infraestructura de autenticación centralizada para toda la plataforma SaaS multi-tenant: endpoint único `POST /api/auth/login`, hashing de contraseñas con salting (bcrypt), generación y firma criptográfica de tokens JWT con claims de rol y tenant, y persistencia de sesiones.
*   **Incertidumbre:** Baja (patrón estándar de la industria).
*   **Validación:** Media (manejo seguro de credenciales erróneas, respuestas genéricas anti-enumeración de usuarios y protección contra ataques de fuerza bruta).
*   *Nota de calibración (PO):* Se calibra de 2 SP a 3 SP para concentrar el costo arquitectónico base del que derivan y reutilizan los demás roles.

### COM-1.3.1 Recuperar contraseña por mail (3 SP - Sprint 1)
*   **Complejidad:** Media (generación de tokens JWT seguros con expiración, servicio de mail).
*   **Incertidumbre:** Baja.
*   **Validación:** Media (bloqueos por spam).

### COM-1.4.1 Editar datos personales (2 SP - Sprint 1)
*   **Complejidad:** Baja (formulario simple).
*   **Incertidumbre:** Nula.
*   **Validación:** Media (re-verificación si cambia el correo).

### COM-1.4.2 Editar datos demográficos (5 SP - Sprint 3)
*   **Complejidad:** Media-Alta (manejo de subida de archivos, compresión, storage S3/Blob).
*   **Incertidumbre:** Baja.
*   **Validación:** Alta (límites de peso y formatos de imagen).

### COM-1.4.3 Configurar restricciones alimentarias (5 SP - Sprint 1)
*   **Complejidad:** Media (relación N:M, impacto transversal en catálogo).
*   **Incertidumbre:** Baja.
*   **Validación:** Alta (garantizar que el filtro aplique correctamente en todo el sistema).

### COM-1.5.1 Buscar establecimiento manualmente (5 SP - Sprint 2)
*   **Complejidad:** Media (debouncing en frontend, query eficiente en backend).
*   **Incertidumbre:** Baja.
*   **Validación:** Alta (manejo de estados vacíos y cambio de tenant).

### COM-4.1.1 Ver catálogo en tiempo real (5 SP - Sprint 2)
*   **Complejidad:** Media (carga perezosa de imágenes, scroll).
*   **Incertidumbre:** Baja.
*   **Validación:** Media.

### COM-4.1.2 Ocultar platos sin stock (5 SP - Sprint 2)
*   **Complejidad:** Alta (propagación de eventos de inventario en tiempo real hacia los clientes conectados).
*   **Incertidumbre:** Media.
*   **Validación:** Alta (condiciones de carrera).

### COM-4.2.1 Filtros de perfil automáticos (3 SP - Sprint 1)
*   **Complejidad:** Baja (leer preferencias en la request del catálogo).
*   **Incertidumbre:** Nula.
*   **Validación:** Alta (garantizar seguridad alimentaria).

### COM-4.2.2 Búsqueda por filtros manuales (3 SP - Sprint 3)
*   **Complejidad:** Baja (query params).
*   **Incertidumbre:** Nula.
*   **Validación:** Media.

### COM-4.2.3 Barra de búsqueda de texto (2 SP - Sprint 2)
*   **Complejidad:** Baja (filtrado client-side si el catálogo ya está cargado).
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### COM-5.1.1 Visualizar multimedia y nombre (2 SP - Sprint 3)
*   **Complejidad:** Baja (modal/vista detalle).
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### COM-5.1.2 Ver ingredientes y alérgenos (1 SP - Sprint 3)
*   **Complejidad:** Nula (renderizado de array de strings).
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### COM-5.1.3 Ver precio (1 SP - Sprint 3)
*   **Complejidad:** Nula.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### COM-6.1.1 Agregar plato al pedido (2 SP - Sprint 3)
*   **Complejidad:** Baja (manejo de estado global en el cliente / Redux/Context).
*   **Incertidumbre:** Nula.
*   **Validación:** Media (alertas si cambia de local).

### COM-6.1.2 Ver acumulado del carrito (1 SP - Sprint 3)
*   **Complejidad:** Baja (UI badge).
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### COM-6.1.3 Modificar cantidades (2 SP - Sprint 3)
*   **Complejidad:** Baja.
*   **Incertidumbre:** Nula.
*   **Validación:** Media (prevenir bajar de 0).

### COM-6.2.1 Seleccionar Inmediato o Reserva (1 SP - Sprint 3)
*   **Complejidad:** Baja (radio buttons y estado).
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### COM-6.2.2 Elegir día y franja (3 SP - Sprint 3)
*   **Complejidad:** Media (selector de fechas/horas dinámico).
*   **Incertidumbre:** Baja.
*   **Validación:** Alta (horarios comerciales activos).

### COM-6.2.3 Indicador de saturación de franja (3 SP - Sprint 3)
*   **Complejidad:** Media (consulta al backend sobre % de capacidad).
*   **Incertidumbre:** Nula.
*   **Validación:** Media.

### COM-6.2.4 Ocultar franjas saturadas (2 SP - Sprint 3)
*   **Complejidad:** Baja (deshabilitar options en UI).
*   **Incertidumbre:** Nula.
*   **Validación:** Alta (doble validación en backend por si se envió un payload alterado).

### COM-7.1.1 Usar método guardado (3 SP - Sprint 4)
*   **Complejidad:** Media (manejo de tokens con la pasarela).
*   **Incertidumbre:** Media.
*   **Validación:** Alta (rechazos bancarios).

### COM-7.1.2 Cargar nueva tarjeta (8 SP - Sprint 4)
*   **Complejidad:** Alta (cumplimiento PCI-DSS, inyección de SDK del proveedor de pagos tipo MercadoPago/Stripe, cifrado en frontend).
*   **Incertidumbre:** Alta.
*   **Validación:** Crítica.

### COM-7.1.3 Opción de recordar pago (3 SP - Sprint 4)
*   **Complejidad:** Media (almacenar el token devuelto por la pasarela de forma segura asociada al usuario).
*   **Incertidumbre:** Baja.
*   **Validación:** Media.

### COM-7.2.1 Pago de carrito individual (8 SP - Sprint 4)
*   **Complejidad:** Media-Alta (orquestación: crear orden en BD, impactar pasarela, capturar respuesta, limpiar carrito).
*   **Incertidumbre:** Media.
*   **Validación:** Muy Alta (prevención de doble cobro).

### COM-7.3.1 Confirmación de pago (5 SP - Sprint 4)
*   **Complejidad:** Media (pantalla de éxito y Webhook listener fallback por si el frontend pierde conexión).
*   **Incertidumbre:** Media.
*   **Validación:** Crítica.

### COM-7.3.2 Tiempo estimado en vivo (5 SP - Sprint 4)
*   **Complejidad:** Alta (algoritmo simple en backend que calcule tiempos promedios en base a la cola actual).
*   **Incertidumbre:** Alta (tiempos de cocina variables).
*   **Validación:** Media.

### COM-8.1.1 Indicadores visuales de cambio de estado (2 SP - Sprint 5)
*   **Complejidad:** Baja (Consumir eventos WebSockets ya implementados y actualizar DOM/Tags en la UI del comensal).
*   **Incertidumbre:** Nula. Ya no hay dependencia de notificaciones push nativas o de terceros.
*   **Validación:** Media (Testear desconexiones de socket y polling de backup).

### COM-8.1.2 Aviso visual de "Listo para retirar" (2 SP - Sprint 5)
*   **Complejidad:** Baja (Renderizado condicional destacado/modal en la app web cuando el estado es Listo).
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### COM-8.1.3 Alerta de congestión en el local (3 SP - Sprint 6)
*   **Complejidad:** Media (evaluar Nro de pedidos "Listos").
*   **Incertidumbre:** Nula.
*   **Validación:** Media (apagar alerta dinámicamente).

---

## ENCARGADO DE MOSTRADOR

### ENC-1.1.1 Ingreso con credenciales de empleado (1 SP - Sprint 1)
*   **Complejidad:** Mínima. No requiere diseñar un sistema de autenticación nuevo; reutiliza directamente el endpoint unificado de autenticación desarrollado en COM-1.2.1. Solo consume el servicio existente validando que el claim del token corresponda al rol "ENCARGADO".
*   **Incertidumbre:** Nula.
*   **Validación:** Baja (verificación de acceso con credenciales provistas por el administrador y bloqueo de roles ajenos).
*   *Nota de calibración (PO):* Se reduce de 3 SP a 1 SP eliminando la sobreestimación por duplicación de arquitectura base.

### ENC-1.2.1 Redirección automática a la vista de mostrador (1 SP - Sprint 1)
*   **Complejidad:** Mínima. Consiste en una directiva condicional estándar en el Router Guard del frontend que evalúa el payload del JWT tras el login y navega automáticamente hacia la ruta protegida `/mostrador`.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja (verificar que un usuario sin rol de encargado sea redirigido a su vista correspondiente o a denegación de acceso).
*   *Nota de calibración (PO):* Se calibra a 1 SP reflejando el esfuerzo técnico real de una regla de ruteo simple.

### ENC-2.1.1 Visualizar pedidos listos para entregar (8 SP - Sprint 6)
*   **Complejidad:** Alta. Reutiliza la infraestructura de comunicación en tiempo real vía WebSockets establecida para el KDS (CHF-3.1.1 en Sprint 5), agregando el tópico o sala de mostrador, el consumo de eventos de pedidos listos, la interfaz de despacho y las pruebas de reconexión y concurrencia ante múltiples terminales.
*   **Incertidumbre:** Media. Definición arquitectónica de escalabilidad para las conexiones concurrentes.
*   **Validación:** Alta. Testing de reconexión automática frente a microcortes de red.

### ENC-2.1.2 Ver detalles rápidos del pedido (2 SP - Sprint 6)
*   **Complejidad:** Baja. Desarrollo estrictamente visual (UI/UX) del componente "tarjeta de pedido" mapeando el JSON recibido.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja. Comprobación de legibilidad en pantallas de mostrador.

### ENC-2.2.1 Anticipar pedidos en preparación (3 SP - Sprint 5)
*   **Complejidad:** Media. Se apoya en la infraestructura WebSocket base implementada en CHF-3.1.1 (Sprint 5), suscribiéndose al tópico de eventos de cocina para mantener actualizada la lista de órdenes en preparación en la vista de mostrador.
*   **Incertidumbre:** Baja.
*   **Validación:** Media. Evitar duplicación de tarjetas en memoria al transicionar de estados.

### ENC-2.2.2 Alertas de espera prolongada en mostrador (3 SP - Sprint 6)
*   **Complejidad:** Media. Lógica en frontend que calcule deltas de tiempo basándose en el timestamp de origen inyectado por el backend (evitando desfasajes por zonas horarias del dispositivo cliente).
*   **Incertidumbre:** Baja.
*   **Validación:** Media. Garantizar que el parpadeo de alerta se reactive si la tablet entra en modo reposo y vuelve a encenderse.

### ENC-3.2.1 Ingresar código numérico manualmente (2 SP - Sprint 6)
*   **Complejidad:** Baja. Funcionalidad de fallback; endpoint REST clásico de búsqueda (GET) por cadena alfanumérica y formulario simple.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja. Sanitización de input básico.

### ENC-3.3.1 Visualizar match exitoso (2 SP - Sprint 6)
*   **Complejidad:** Baja. Renderizado condicional del modal o overlay de confirmación verde y carga de los datos finales del comensal (para check de identidad).
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### ENC-3.3.2 Marcar pedido como Entregado (3 SP - Sprint 6)
*   **Complejidad:** Media. Mutación de estado en la base de datos, descarte de la tarjeta en memoria, y disparo del evento final para cerrar el ciclo de vida de la orden.
*   **Incertidumbre:** Baja.
*   **Validación:** Media. Evitar condiciones de carrera si dos encargados intentan marcar el mismo pedido en paralelo desde dispositivos distintos.

---

## CHEF

### CHF-1.1.1 Ingreso con credenciales de empleado (1 SP - Sprint 1)
*   **Complejidad:** Mínima. Reutiliza el endpoint central de autenticación (`POST /api/auth/login`) implementado en COM-1.2.1. Solo valida que el token JWT emitido contenga el rol "CHEF" para habilitar el ruteo a la terminal de cocina.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja (control de acceso con credenciales válidas y rechazo de roles no autorizados).

### CHF-1.2.1 Redirección automática a la vista de cocina (1 SP - Sprint 1)
*   **Complejidad:** Mínima. Regla condicional en el Router Guard del cliente web que redirige al dashboard del KDS de cocina (`/cocina`) al detectar el claim de rol "CHEF" en el token de sesión.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja (verificar ruteo hacia la terminal de cocina).
*   *Nota de calibración (PO):* Se calibra a 1 SP por coherencia arquitectónica con los guards del resto de los roles.

### CHF-2.1.1 Visualizar el catálogo de ingredientes disponibles (5 SP - Sprint 1)
*   **Complejidad:** Media. Desarrollar el listado base, el endpoint de lectura paginada y la UI del modal de selección.
*   **Incertidumbre:** Baja.
*   **Validación:** Media. Casos de catálogos extensos.

### CHF-2.2.1 Crear ficha base del plato (5 SP - Sprint 2)
*   **Complejidad:** Media-Alta. Diseño de esquema de base de datos para platos, formulario complejo y, críticamente, la arquitectura de subida, optimización y almacenamiento en la nube de imágenes (AWS S3, Blob Storage).
*   **Incertidumbre:** Baja.
*   **Validación:** Alta. Validar formatos de imagen y límite de pesos.

### CHF-2.2.2 Asignar receta (8 SP - Sprint 2)
*   **Complejidad:** Alta. Implica establecer una relación estructurada Many-to-Many con campos adicionales (cantidades/unidad de medida), y una UI interactiva que permita agregar/quitar insumos de la matriz de la receta de forma dinámica.
*   **Incertidumbre:** Baja.
*   **Validación:** Alta. Prevención de asignación de ingredientes duplicados o en cantidad cero.

### CHF-2.2.3 Configurar alérgenos y etiquetas dietarias (3 SP - Sprint 2)
*   **Complejidad:** Media. Otra relación N:M, pero de implementación mucho más estándar (checkboxes/tags) sin datos numéricos asociados.
*   **Incertidumbre:** Nula.
*   **Validación:** Media.

### CHF-2.2.4 Enviar a revisión del Administrador (3 SP - Sprint 2)
*   **Complejidad:** Media. Implica el diseño e implementación inicial de la máquina de estados (State Machine) del ciclo de vida del plato (Borrador -> Revisión -> Activo).
*   **Incertidumbre:** Baja.
*   **Validación:** Baja.

### CHF-2.2.5 Pausa manual de platos (2 SP - Sprint 3)
*   **Complejidad:** Baja. Simple mutación booleana (soft disable) en la base de datos a través de un toggle.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### CHF-3.1.1 Visualización en tiempo real (8 SP - Sprint 5)
*   **Complejidad:** Alta. Si esta es la primera interfaz en desarrollarse, requiere levantar toda la infraestructura Pub/Sub o de WebSockets (Socket.io/SignalR) para que el KDS reciba los pedidos provenientes de la pasarela de pagos del comensal al instante.
*   **Incertidumbre:** Media. Riesgos de conectividad en entornos de cocina.
*   **Validación:** Alta. Pruebas de estrés y reconexión.

### CHF-3.1.2 Priorización automática de la cola (3 SP - Sprint 5)
*   **Complejidad:** Media. Algoritmo de ordenamiento (sorting) de dos niveles en el frontend/backend basado en la franja horaria y el timestamp de llegada.
*   **Incertidumbre:** Baja.
*   **Validación:** Media.

### CHF-3.2.1 Leer la comanda (2 SP - Sprint 5)
*   **Complejidad:** Baja. Lógica estrictamente de presentación (UI layout) para renderizar los detalles en tipografía clara y contrastante.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### CHF-4.1.1 Empezar a cocinar (3 SP - Sprint 5)
*   **Complejidad:** Media. Transición de estado en BD y emisión de un evento WebSocket hacia otros clientes conectados.
*   **Incertidumbre:** Baja.
*   **Validación:** Media.

### CHF-4.1.2 Check incremental por ítem (5 SP - Sprint 5)
*   **Complejidad:** Media-Alta. Implica manejo de estado local avanzado dentro de la comanda (marcado ítem por ítem) y requerimientos de "optimistic UI" para que no haya lag visual al tocar en la pantalla táctil de la cocina.
*   **Incertidumbre:** Baja.
*   **Validación:** Media. Evitar pérdidas de estado ante una recarga.

### CHF-4.1.3 Finalizar el pedido por completo (3 SP - Sprint 5)
*   **Complejidad:** Media. Lógica de limpieza en la vista KDS, actualización de estado maestro de la orden y propagación del evento.
*   **Incertidumbre:** Baja.
*   **Validación:** Media. Bloqueo si hay ítems sin tachar en la comanda.

### CHF-4.1.4 Cronómetro de preparación (3 SP - Sprint 5)
*   **Complejidad:** Media. Timer asíncrono en React/Angular que calcula la diferencia en vivo desde el timestamp en que la orden pasó a "En preparación".
*   **Incertidumbre:** Baja.
*   **Validación:** Media. Evitar desincronizaciones por tabs inactivas ( throttling del navegador).

### CHF-4.1.5 Alerta visual de demora en KDS (3 SP - Sprint 5)
*   **Complejidad:** Media. Condicionales de renderizado visual disparados automáticamente al cruzar el umbral del timer implementado en 4.1.4.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### CHF-4.2.1 Gatillar cambio de estado al comensal (3 SP - Sprint 5)
*   **Complejidad:** Media. Consiste en emitir el evento WebSocket hacia la sala/tópico del comensal específico y actualizar el estado transaccional en BD.
*   **Incertidumbre:** Baja. La infraestructura de WebSockets se encuentra operativa tras implementarse en CHF-3.1.1 (Sprint 5).
*   **Validación:** Media.

### CHF-4.2.2 Sincronización con Mostrador (3 SP - Sprint 5)
*   **Complejidad:** Media. Emplea la infraestructura de sockets existente, pero exige gestionar subscripciones segregadas (rooms/tópicos) para el canal del encargado.
*   **Incertidumbre:** Baja.
*   **Validación:** Media.

### CHF-4.2.3 Sincronización con Mostrador (2 SP - Sprint 5)
*   **Complejidad:** Baja. Si 4.2.2 está resuelta, es solo emitir el payload con otro código de estado para encolarlo en la columna principal.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### CHF-5.1.1 Descuento de stock transaccional (8 SP - Sprint 4)
*   **Complejidad:** Alta. Este es el motor del inventario. Requiere implementar transacciones SQL ACID robustas y control de concurrencia para evitar condiciones de carrera (race conditions) si dos pagos se aprueban milisegundos antes del descuento real del inventario.
*   **Incertidumbre:** Baja.
*   **Validación:** Crítica. Test de stress y locks transaccionales.

### CHF-5.2.1 Alerta visual de stock crítico (3 SP - Sprint 3)
*   **Complejidad:** Media. Consultas con cruce de información vs. los thresholds configurados.
*   **Incertidumbre:** Baja.
*   **Validación:** Media. Evitar spam si el valor está oscilando en el límite debido a cancelaciones.

---

## ADMINISTRADOR

### ADM-1.1.1 Ingresar al Back-Office web (1 SP - Sprint 1)
*   **Complejidad:** Mínima. Reutiliza el endpoint centralizado de login (COM-1.2.1); valida que las credenciales posean el rol "ADMIN" y concede el acceso al back-office del establecimiento correspondiente sin duplicar código de autenticación.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja (verificación de restricciones administrativas multi-tenant).
*   *Nota de calibración (PO):* Se reduce de 3 SP a 1 SP al tratarse del mismo flujo de autenticación transversal.

### ADM-1.2.1 Recuperar contraseña (1 SP - Sprint 1)
*   **Complejidad:** Mínima. Reutiliza al 100% el servicio común de generación de tokens temporales de reseteo y envío de correo ya implementado en COM-1.3.1. No requiere desarrollar una pasarela de correos separada.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja (comprobación de reseteo para cuentas con rol administrador).
*   *Nota de calibración (PO):* Se reduce de 3 SP a 1 SP eliminando la duplicación del servicio de email transaccional.

### ADM-2.1.1 Configurar branding del restaurante (8 SP - Sprint 4)
*   **Complejidad:** Alta. Implica carga de assets (S3/Blob Storage) y el desarrollo de un motor CSS dinámico en la plataforma del comensal que lea la paleta configurada e inyecte variables CSS o un tema (Theming) en tiempo de ejecución.
*   **Incertidumbre:** Baja.
*   **Validación:** Alta. Garantizar accesibilidad y contraste correcto con colores arbitrarios.

### ADM-2.2.1 Configurar horarios comerciales (5 SP - Sprint 3)
*   **Complejidad:** Media. UI de configuración de calendario. La complejidad real radica en el backend interceptando transacciones y filtrando catálogos basándose en franjas horarias relativas al Timezone del restaurante.
*   **Incertidumbre:** Media (Manejo de husos horarios).
*   **Validación:** Alta. Boundary testing en los minutos de apertura/cierre.

### ADM-2.2.2 Configurar capacidad máxima por franja horaria (3 SP - Sprint 3)
*   **Complejidad:** Media. Formulario numérico y adición de limitantes lógicos al módulo de reservas.
*   **Incertidumbre:** Baja.
*   **Validación:** Media.

### ADM-2.2.3 Habilitar métodos de pago (2 SP - Sprint 3)
*   **Complejidad:** Baja. Persistencia de flags booleanos por local y lectura en el renderizado del checkout del cliente.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### ADM-3.1.1 Alta de staff (5 SP - Sprint 1)
*   **Complejidad:** Media. Creación de registros en la entidad de usuarios con envíos automáticos de correos con contraseñas temporales y políticas de forzado de cambio al primer login.
*   **Incertidumbre:** Baja.
*   **Validación:** Media.

### ADM-3.1.2 Asignación de Roles (5 SP - Sprint 1)
*   **Complejidad:** Media. Establecer la base de datos de roles y permisos, y los middlewares de backend necesarios para proteger endpoints según la jerarquía asignada (Admin vs Chef vs Mostrador).
*   **Incertidumbre:** Baja.
*   **Validación:** Crítica. Pruebas de penetración de permisos cruzados (Ej. que un Chef no pueda alterar precios).

### ADM-3.1.3 Baja de exempleados (3 SP - Sprint 6)
*   **Complejidad:** Baja (Soft-delete o inhabilitación lógica).
*   **Incertidumbre:** Baja.
*   **Validación:** Media. Revocación asíncrona de tokens JWT activos en memoria.

### ADM-4.1.1 Crear, editar y eliminar ingredientes del inventario (3 SP - Sprint 1)
*   **Complejidad:** Media. CRUD estándar (Create, Read, Update, Delete) de la tabla base de insumos sin relaciones complejas.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### ADM-4.1.2 Ingresar y actualizar manualmente las cantidades (3 SP - Sprint 1)
*   **Complejidad:** Media. Formulario de ajuste transaccional (sobreescribir el stock actual) requiriendo logs de auditoría para saber quién modificó el stock a mano.
*   **Incertidumbre:** Baja.
*   **Validación:** Media.

### ADM-4.1.3 Definir el umbral de stock Crítico (2 SP - Sprint 3)
*   **Complejidad:** Baja. Anexar el campo numérico de límite en la BD y UI correspondiente.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### ADM-4.1.4 Visualizar alertas de reabastecimiento (3 SP - Sprint 3)
*   **Complejidad:** Media. Dashboard administrativo que filtra mediante queries (stock real <= stock umbral).
*   **Incertidumbre:** Baja.
*   **Validación:** Baja.

### ADM-5.1.1 Ver platos pendientes (3 SP - Sprint 2)
*   **Complejidad:** Media. Bandeja de entrada visual conectada a un estado específico del Workflow de platos.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### ADM-5.1.2 Auditoría de receta y food cost (2 SP - Sprint 2)
*   **Complejidad:** Baja. Vista de sólo lectura renderizando el trabajo ya guardado por el Chef.
*   **Incertidumbre:** Nula.
*   **Validación:** Baja.

### ADM-5.2.1 Costeo (3 SP - Sprint 2)
*   **Complejidad:** Media. Guardar el precio de venta al público en la entidad.
*   **Incertidumbre:** Baja.
*   **Validación:** Alta. Evitar fallos de punto flotante/decimales en la moneda que puedan afectar a la pasarela de pago.

### ADM-5.2.2 Aprobar y Publicar (2 SP - Sprint 2)
*   **Complejidad:** Baja. Actualizar el estado en la máquina de estados. Impacto instantáneo en la app comensal.
*   **Incertidumbre:** Baja.
*   **Validación:** Media.

### ADM-5.2.3 Rechazar al Chef con feedback (3 SP - Sprint 2)
*   **Complejidad:** Media. Retornar el estado y persistir un comentario atado al ciclo de revisión de esa entidad específica.
*   **Incertidumbre:** Baja.
*   **Validación:** Baja.

### ADM-6.1.1 Estadísticas de ingresos y volumen (8 SP - Sprint 6)
*   **Complejidad:** Alta. Implementar el módulo de Business Intelligence (BI). Requiere queries de agregación pesadas (GROUP BY fechas) y renderizado de gráficos integrando librerías especializadas (Chart.js / Recharts) en el frontend.
*   **Incertidumbre:** Baja.
*   **Validación:** Media.

### ADM-6.1.2 Mapa de calor de franjas horarias (8 SP - Sprint 6)
*   **Complejidad:** Alta. Lógica algorítmica y cruces de datos bidimensionales para construir matrices térmicas, más el esfuerzo de la UI interactiva customizada.
*   **Incertidumbre:** Media.
*   **Validación:** Media.

### ADM-6.2.1 Ranking de Platos (3 SP - Sprint 6)
*   **Complejidad:** Media. Query de agregación relacional (Count de ítems vendidos unidos a la tabla de platos) ordenados de forma descendente.
*   **Incertidumbre:** Baja.
*   **Validación:** Baja.
