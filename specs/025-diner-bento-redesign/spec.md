# Feature Specification: Rediseño visual Bento del flujo integral del Comensal

**Feature Branch**: `feat/025-diner-bento-redesign`  
**Spec Directory**: `specs/025-diner-bento-redesign`  
**Created**: 22/11/2026  
**Status**: Aprobada (lista para implementación)  
**Input**: User description: "Crear la spec 025 en una nueva rama saliendo desde feat/frontend-redesign. Trabajar en el rol del comensal únicamente desde el frontend, dejándolo visualmente cuidado y respetando el tema Bento (contrato visual comensal.DESIGN.md remediado en Spec 024)."

---

## Autorización constitucional de alcance (Principio X)

De conformidad con el Principio X de la constitución del proyecto (Integridad Full-Stack / Vertical Slices), toda especificación limitada a una única capa requiere autorización humana directa y explícita. El 22/11/2026, el responsable del proyecto autorizó expresamente la ejecución de la especificación `025` como entrega exclusiva de interfaz de usuario (single-tier frontend-only), sin modificaciones al servidor ni a los contratos de comunicación existentes, con el objetivo de rediseñar integralmente las pantallas del rol Comensal y las vistas públicas/de acceso sobre la arquitectura visual establecida en la especificación `024`.

Asimismo, el 22/11/2026 el responsable del proyecto autorizó desarrollar las cinco historias de usuario (`US1`–`US5`) en la rama única `feat/025-diner-bento-redesign` (creada desde `feat/frontend-redesign`), organizadas mediante commits atómicos por historia de usuario (excepción documentada a la regla de una historia por rama de la constitución, consistente con el criterio aplicado en la especificación `024`).

---

## Contexto y objetivo

La especificación `024` estableció los cimientos visuales de EasyEat: el contrato remediado del rol Comensal (`.specify/memory/designs/comensal.DESIGN.md`), el ámbito determinista de temas por ruta, el contenedor de navegación minimalista con menú desplegable, el aislamiento por capas frente a la identidad personalizada de cada establecimiento (especificación `016`) y los cinco primitivos base accesibles (botón de acción, insignia de estado, estado de retroalimentación, esqueleto de carga y estado vacío). Sin embargo, el contenido interno de las once pantallas que recorre el Comensal —desde el acceso público hasta el seguimiento en vivo y el perfil personal— conserva aún la maquetación heredada de las especificaciones `001`–`023`, con estilos heterogéneos, baja cohesión visual en móvil y ausencia de la estructura modular **Bento**.

Esta especificación define el rediseño estructural y visual **Bento** del flujo completo del Comensal con cinco objetivos concretos:

1. **Cohesión estética Bento regida por el contrato remediado (`comensal.DESIGN.md`)**: Aplicar en todas las pantallas del alcance una composición modular en bloques tipo tarjeta de jerarquía clara, espaciado suave (`4/8/12/16/24/32` px), superficie cálida `#FFF5E6`, bloques elevados `#FFFFFF`, acentos cálidos `#FAD4C0` / `#80A1C1` con pares de contraste WCAG 2.2 AA y tipografía autoalojada `DM Sans` + `JetBrains Mono` (respetando la precedencia constitucional sobre `Inter` y la política Zero AI-Slop).
2. **Cobertura punta a punta del recorrido del Comensal**: Rediseñar de forma estructural las **6 pantallas propias del Comensal** (Descubrimiento de establecimientos, Catálogo y carrito, Checkout, Retorno de pago, Confirmación y seguimiento en vivo, y Perfil con Mis pedidos) y las **5 pantallas públicas y de autenticación** (Inicio de sesión, Registro, Recuperación de contraseña, Restablecimiento de contraseña y Verificación de correo).
3. **Convivencia enfocada con la identidad de marca del establecimiento (especificación `016`)**: Integrar los colores y logotipo propios de cada restaurante únicamente en puntos focales de identidad y acción (cabecera/banner del local, categoría activa, botón principal de compra y cabecera de confirmación), preservando el lienzo Bento, las tarjetas, los filtros y las alertas de seguridad alimentaria bajo los tokens estructurales del sistema.
4. **Ergonomía mobile-first y accesibilidad WCAG 2.2 AA**: Garantizar navegación por teclado completa, estados interactivos explícitos (reposo, hover, foco visible, activo, deshabilitado, carga y error), objetivos táctiles accesibles y lectura inmediata de información crítica (alérgenos, precios, franjas horarias y PIN antifraude de retiro).
5. **Cero regresiones funcionales**: Preservar intactos el 100 % de las reglas de negocio, contratos de intercambio con el servidor y garantías conductuales de las especificaciones `001`–`023`.

Quedan explícitamente fuera de alcance: cualquier pantalla de los roles Encargado del Mostrador, Chef o Administrador (especificaciones `026`–`028`), la pantalla de cambio obligatorio de contraseña de primer ingreso del personal, modificaciones al servidor o a los contratos de datos, y nuevas funcionalidades de negocio no existentes en las especificaciones `001`–`023`.

---

## Clarifications

### Sesión 22/11/2026 (Grill-Me)

- Q: ¿Qué pantallas entran en la Spec `025`? → A: Se incluyen las 6 pantallas del flujo Comensal (`Descubrimiento`, `Catálogo/Carrito`, `Checkout`, `Retorno de pago`, `Confirmación y seguimiento en vivo`, `Perfil`) y las 5 pantallas públicas/de acceso que heredan el tema Bento (`Login`, `Register`, `ForgotPassword`, `ResetPassword`, `VerifyEmail`), totalizando 11 pantallas.
- Q: ¿Se autoriza formalmente la excepción single-tier frontend-only bajo el Principio X y cuál es la estrategia de ramas? → A: Sí. Se autoriza la excepción single-tier frontend-only y trabajar las cinco historias de usuario en la rama única `feat/025-diner-bento-redesign` saliendo desde `feat/frontend-redesign`, con commits atómicos por historia de usuario.
- Q: ¿Qué protagonismo deben tener los colores de marca del establecimiento (especificación `016`) dentro de la estética Bento? → A: Acentos de marca enfocados: banner/identidad del local, categoría activa, acción principal de compra y cabecera de confirmación utilizan los tokens de marca del establecimiento con contraste garantizado, mientras que la superficie base (`#FFF5E6`), las tarjetas (`#FFFFFF`), los filtros y las alertas semánticas permanecen 100 % bajo los tokens Bento del sistema.
- Q: ¿Cuál es la profundidad del rediseño Bento? → A: Rediseño estructural Bento completo: reorganizar cada pantalla en bloques y grillas modulares responsivas (mobile-first), mejorar jerarquía visual, micro-estados y ergonomía táctil, manteniendo intactos el 100 % de las APIs, lógica de negocio y pruebas conductuales existentes.
- Q: ¿Qué composición deben tener las 5 pantallas públicas y de autenticación (`Login`, `Register`, `ForgotPassword`, `ResetPassword`, `VerifyEmail`)? → A: Tarjeta Bento centrada y polimórfica impulsada 100 % por los tokens del tema activo, limpia en móvil y escritorio y compatible sin fricción cuando `/login` adopta el tema de un rol de personal por enlace profundo.
- Q: ¿Cómo se organizan filtros dietéticos y carrito en la pantalla de Catálogo? → A: Catálogo Bento mobile-first con bloque de identidad del establecimiento, búsqueda y navegación fija de categorías, filtros dietéticos en barra lateral colapsable en escritorio y panel deslizable en móvil, grilla modular de platos y carrito en panel deslizable disparado desde un botón flotante accesible.
- Q: ¿Cómo se jerarquizan Checkout y Seguimiento en vivo? → A: Checkout en grilla Bento de 2 columnas en escritorio (configuración de modalidad/fecha/franja horaria y medio de pago a la izquierda; resumen fijo del pedido con importes en tipografía monoespaciada a la derecha) y columna única en móvil; Seguimiento con indicador visual de progreso por pasos y tarjeta héroe de alto impacto para el PIN de retiro en tipografía monoespaciada de gran tamaño cuando el pedido está listo.
- Q: ¿Cómo se organiza el Perfil del Comensal y qué política rige para las pruebas heredadas? → A: Perfil en grilla Bento de 2 columnas en escritorio y mantenimiento estricto del invariante de pruebas conductuales `001`–`023` (cero debilitamiento de aserciones; solo actualización acotada de aserciones estrictamente acopladas a clases de maquetación previas si alguna se viera afectada).

### Sesión 22/11/2026 (Clarify)

- Q: ¿Cómo debe convivir la grilla Bento de 2 columnas en `ProfilePage` con las pestañas actuales `Mi perfil` / `Mis pedidos` (`/perfil#pedidos`)? → A: Preservar el `role="tablist"` (`Mi perfil` y `Mis pedidos`) estilizado como pestañas segmentadas Bento sincronizadas con `/perfil#pedidos`, mostrando la grilla Bento de 2 columnas (Datos personales + Restricciones dietéticas) dentro de `Mi perfil` y una grilla Bento dedicada de tarjetas de pedidos dentro de `Mis pedidos`.
- Q: ¿Cómo debe componerse la pantalla `/descubrimiento` dado que funciona por búsqueda activa (mínimo 3 caracteres) y no existe un endpoint de listado general de locales? → A: Alinear `US1` y `FR-005` al flujo real basado en búsqueda: tarjeta Bento de acceso rápido al establecimiento activo (si existe), bloque héroe Bento de búsqueda con resultados en formato Bento (incluyendo estados de carga, sin coincidencias y error con reintento) y grilla Bento de 3 bloques informativos de beneficios, sin endpoints nuevos.
- Q: ¿Cómo debe activarse la capa de marca del establecimiento (`--brand-*`) en Checkout y Confirmación/Seguimiento? → A: Reutilizar el proveedor de marca existente (`BrandThemeProvider`) en las vistas de Checkout y Confirmación/Seguimiento utilizando el identificador del establecimiento activo o del pedido, con respaldo automático a la paleta Bento neutra cuando dicho identificador no esté disponible.
- Q: ¿Qué nivel de sobriedad visual y movimiento (modo Impeccable) debe gobernar las 11 pantallas del Comensal? → A: Modo **Operate** sobrio y rápido: escaneabilidad prioritaria, cifras tabulares en `JetBrains Mono` para precios, subtotales, cuentas regresivas y PIN, micro-transiciones breves (≤ 200 ms) con supresión automática bajo preferencia de movimiento reducido y cero ornamentos decorativos superfluos.

---

## Personas

- **Comensal invitado (no autenticado)**: Persona que descubre EasyEat desde un enlace o accede a registrarse, iniciar sesión, recuperar su acceso o explorar el catálogo público de un establecimiento. Necesita confianza visual inmediata, formularios limpios y lectura sin fricción desde el teléfono móvil.
- **Comensal autenticado**: Cliente habitual que explora establecimientos, aplica filtros por restricciones dietéticas y alérgenos, arma su carrito, elige franja horaria de retiro, paga y realiza seguimiento en vivo hasta mostrar su PIN en el mostrador. Necesita claridad modular (Bento), señales inequívocas de seguridad alimentaria y acceso rápido a su pedido activo.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Acceso público y descubrimiento de establecimientos con estética Bento (Priority: P1)

Como Comensal (invitado o autenticado), quiero que las pantallas de ingreso, registro, recuperación/restablecimiento de contraseña, verificación de correo y descubrimiento de establecimientos presenten una interfaz modular Bento cálida, clara y adaptable a cualquier pantalla, para acceder a mi cuenta y elegir dónde pedir con rapidez y confianza.

**Why this priority**: Representa el primer punto de contacto del Comensal con EasyEat. Sin una entrada coherente y un catálogo de establecimientos atractivo y escaneable, la percepción de calidad de todo el flujo queda fragmentada.

**Independent Test**: Puede validarse de forma independiente recorriendo las cinco rutas de acceso público (`/login`, `/register`, `/forgot-password`, `/reset-password`, `/verificar-email`) en móvil y escritorio, verificando la tarjeta centrada polimórfica (incluido un enlace profundo hacia personal en `/login`), y navegando `/descubrimiento` para buscar, filtrar estados de carga/vacío/error y seleccionar un establecimiento.

**Acceptance Scenarios**:

1. **Given** un usuario no autenticado que visita `/login`, `/register`, `/forgot-password`, `/reset-password` o `/verificar-email` desde móvil o escritorio, **When** la vista se carga, **Then** visualiza una tarjeta Bento centrada sobre la superficie cálida `#FFF5E6`, con encabezado claro de EasyEat, campos y botones accesibles que exhiben todos los estados interactivos (foco visible, carga y errores mediante los primitivos base) y contraste mínimo WCAG 2.2 AA.
2. **Given** un usuario de personal que llega a `/login` a través de un enlace profundo hacia mostrador, cocina o administración, **When** la pantalla de inicio de sesión se renderiza, **Then** la tarjeta centrada adopta íntegramente los tokens visuales del rol destino sin desajustes de contraste ni estilos fijos del tema Comensal.
3. **Given** un Comensal autenticado en `/descubrimiento`, **When** visualiza la pantalla en móvil, tableta o escritorio e ingresa al menos 3 caracteres en el buscador, **Then** encuentra una composición Bento modular con tarjeta de acceso rápido al restaurante activo (cuando existe uno seleccionado), un bloque héroe de búsqueda con resultados claros y accionables para ingresar al catálogo, y una grilla Bento de tres bloques informativos de valor operativo.
4. **Given** un Comensal buscando establecimientos en `/descubrimiento` mientras se consultan resultados, cuando la búsqueda no arroja coincidencias o cuando ocurre un error de conexión, **When** cambia el estado de los resultados de búsqueda, **Then** el sistema muestra respectivamente indicadores/esqueletos de carga Bento, un estado vacío orientativo con opción de ajustar o limpiar la búsqueda, o un estado de retroalimentación con acción de reintento.

---

### User Story 2 — Catálogo gastronómico, seguridad alimentaria y carrito Bento con branding aislado (Priority: P1)

Como Comensal que explora el menú de un restaurante, quiero navegar una grilla Bento de platos con filtros dietéticos claros, advertencias inequívocas de alérgenos y un carrito deslizable ágil, percibiendo los acentos de marca del local sin perder la legibilidad del sistema, para armar mi pedido de forma segura y sin fricción desde el teléfono o la computadora.

**Why this priority**: Es la pantalla de mayor interacción y conversión del Comensal, donde convergen seguridad alimentaria (alérgenos y perfil dietético), branding multi-tenant (especificación `016`) y gestión del carrito.

**Independent Test**: Puede validarse de forma independiente abriendo `/catalogo` para un establecimiento con branding personalizado y para otro con branding neutro, probando la barra de búsqueda, navegación fija de categorías, alternador maestro de perfil dietético, apertura/cierre de filtros manuales (sidebar en escritorio y panel deslizable en móvil), detalle de plato, advertencias y modal de riesgo por alérgeno, botón flotante, panel de carrito y modal de cambio de establecimiento.

**Acceptance Scenarios**:

1. **Given** un Comensal en `/catalogo` de un establecimiento con identidad personalizada (especificación `016`), **When** observa la página, **Then** el bloque de cabecera del local, la categoría activa en la navegación fija y el botón principal de compra/agregar exhiben los colores de marca del establecimiento con contraste WCAG 2.2 AA, mientras la superficie general (`#FFF5E6`), las tarjetas de plato (`#FFFFFF`), los paneles de filtros y las alertas de alérgenos mantienen intacta la paleta Bento del sistema.
2. **Given** un Comensal buscando y filtrando platos en escritorio o en móvil, **When** utiliza el buscador, el alternador maestro de perfil dietético o los filtros manuales de alérgenos y dietas, **Then** en escritorio opera los filtros desde una barra lateral Bento colapsable junto a la grilla de platos y en móvil desde un panel deslizable accesible por teclado y tacto, con indicadores claros de filtros activos y estados vacíos diferenciados (sin resultados por filtro vs catálogo incompatible con perfil dietético).
3. **Given** un Comensal visualizando la grilla modular de platos y el detalle de un plato, **When** inspecciona una tarjeta o abre el modal de detalle, **Then** percibe jerarquía clara entre imagen, nombre, descripción, precio alineado en `JetBrains Mono`, estado de disponibilidad e insignias de alérgenos de alto contraste que nunca dependen únicamente del color; y si intenta agregar un plato con conflicto dietético, el modal de riesgo presenta advertencia clara y acciones accesibles.
4. **Given** un Comensal con productos en su carrito o cambiando de establecimiento, **When** activa el botón flotante del carrito o selecciona otro local con carrito activo, **Then** el panel deslizable del carrito muestra cada ítem con sus insignias, controles de cantidad táctiles accesibles, subtotales en `JetBrains Mono` y acción hacia el checkout; y el modal de cambio de local comunica claramente el impacto antes de confirmar.
5. **Given** un establecimiento cerrado o con restricciones operativas, **When** el Comensal ingresa a su catálogo, **Then** los avisos de horarios de operación y bloqueo de establecimiento se muestran como bloques Bento de retroalimentación accesibles sin romper la jerarquía visual ni ocultar la información esencial.

---

### User Story 3 — Checkout modular Bento y retorno de pago (Priority: P1)

Como Comensal listo para confirmar mi compra, quiero configurar la modalidad de retiro, fecha, franja horaria y medio de pago en un checkout Bento ordenado que mantenga visible el resumen de mi pedido y maneje con claridad cualquier conflicto o espera de pago, para finalizar mi orden sin dudas ni errores.

**Why this priority**: El checkout concentra decisiones críticas (modalidad de retiro, cupos horarios, validación de stock y pago); una presentación desordenada o confusa provoca abandonos y errores operativos.

**Independent Test**: Puede validarse de forma independiente ingresando a `/checkout` con un carrito activo en escritorio y móvil, verificando la grilla de 2 columnas con resumen fijo en escritorio y columna única en móvil, alternando retiro inmediato vs programado, seleccionando fecha y franja horaria, cambiando medio de pago, simulando estados de conflicto de stock/concurrencia, cuenta regresiva de orden pendiente, panel de pago en proceso y recorriendo `/checkout/payment-return` en sus distintos resultados de retorno.

**Acceptance Scenarios**:

1. **Given** un Comensal en `/checkout` desde una pantalla de escritorio, **When** revisa el formulario de compra, **Then** visualiza una grilla Bento de 2 columnas que agrupa a la izquierda los bloques modulares de modalidad de retiro, selector de fecha/franjas horarias y medio de pago, y a la derecha un bloque fijo con el resumen detallado del pedido y totales en `JetBrains Mono`; en móvil ambos bloques se apilan ordenadamente con la acción de confirmación accesible.
2. **Given** un Comensal seleccionando fecha y franja horaria de retiro, **When** navega los bloques de fecha y la grilla de franjas disponibles, **Then** cada opción exhibe estados claros (disponible, seleccionada, deshabilitada/sin cupo) con contraste WCAG 2.2 AA, objetivos táctiles mínimos de 44×44 px y operación completa por teclado.
3. **Given** un escenario de conflicto de stock, concurrencia, acreditación pendiente, orden en espera con temporizador o pago en proceso, **When** el sistema notifica dicho estado, **Then** los banners, paneles de espera y modales de conflicto/concurrencia utilizan la jerarquía Bento y los pares semánticos accesibles del tema para explicar el problema y ofrecer acciones claras de resolución o reintento.
4. **Given** un Comensal redirigido desde la pasarela externa hacia `/checkout/payment-return`, **When** se procesa el resultado (aprobado, pendiente, rechazado o error), **Then** la vista presenta una tarjeta Bento centrada con estado semántico inequívoco, mensaje orientativo y navegación directa hacia el seguimiento del pedido o reintento en checkout.

---

### User Story 4 — Confirmación de pedido y seguimiento en vivo con tarjeta héroe de PIN (Priority: P2)

Como Comensal que ya realizó un pedido, quiero ver la confirmación y el avance en tiempo real de mi orden en bloques Bento claros —destacando de manera prominente mi PIN antifraude cuando el pedido esté listo para retirar— para saber exactamente cuándo acercarme al mostrador y retirar mi comida sin demoras.

**Why this priority**: Es la pantalla que el Comensal consulta de pie frente al local o en camino al mostrador; la claridad del estado en vivo, las demoras y el PIN de 4 dígitos impactan directamente la experiencia física de retiro.

**Independent Test**: Puede validarse de forma independiente cargando `/checkout/confirmacion/:orderId` y `/pedidos/:orderId/seguimiento` (y su alias `/seguimiento/:orderId`) en cada estado del ciclo de vida del pedido (confirmado/programado con cuenta regresiva, en preparación, con aviso de demora, listo para retirar con PIN, entregado y con desconexión temporal de eventos en vivo).

**Acceptance Scenarios**:

1. **Given** un Comensal que llega a la confirmación o seguimiento de su pedido, **When** la página se carga, **Then** visualiza una cabecera Bento con el acento de marca del establecimiento (`--brand-*`), número de pedido en `JetBrains Mono`, estado de conexión en tiempo real mediante insignia accesible y un indicador visual progresivo de etapas del pedido.
2. **Given** un pedido programado a una franja futura o un pedido en preparación que registra una demora, **When** el Comensal consulta el seguimiento, **Then** los bloques Bento muestran de forma destacada la cuenta regresiva o el aviso de demora actualizado con contraste WCAG 2.2 AA y anuncio accesible no intrusivo.
3. **Given** un pedido que pasa a estado listo para retirar, **When** el Comensal abre o mantiene abierta la vista de seguimiento, **Then** se destaca una tarjeta héroe Bento de alto contraste que exhibe el PIN antifraude en tipografía `JetBrains Mono` de gran tamaño y alta legibilidad a distancia, acompañado de instrucciones claras para mostrarlo en el mostrador.
4. **Given** un pedido ya entregado, **When** el Comensal consulta la vista, **Then** se presenta el bloque Bento de entrega completada con resumen claro del cierre y acceso para volver al catálogo o a sus pedidos.

---

### User Story 5 — Perfil del Comensal, restricciones dietéticas y Mis pedidos en grilla Bento (Priority: P2)

Como Comensal autenticado, quiero gestionar mis datos personales, foto de perfil, restricciones dietéticas e historial de pedidos desde una vista de perfil organizada en bloques Bento equilibrados, para mantener mis preferencias seguras y acceder en un toque al seguimiento de mis pedidos activos o pasados.

**Why this priority**: Consolida la configuración personal que gobierna el filtrado dietético de toda la plataforma y funciona como centro de acceso a los pedidos realizados.

**Independent Test**: Puede validarse de forma independiente ingresando a `/perfil` en escritorio y móvil, editando datos personales, gestionando la foto y el modal de eliminación, visualizando el banner de verificación de correo pendiente, actualizando restricciones dietéticas/alérgenos y recorriendo la sección `Mis pedidos` (incluidos estados de carga, vacío, error y navegación a seguimiento).

**Acceptance Scenarios**:

1. **Given** un Comensal autenticado que accede a `/perfil` o `/perfil#pedidos` en escritorio o móvil, **When** la página se renderiza o alterna entre las pestañas accesibles `Mi perfil` y `Mis pedidos` (`role="tablist"`), **Then** la pestaña `Mi perfil` organiza en grilla Bento de 2 columnas en escritorio (y columna única en móvil) el bloque de identidad/datos personales junto al bloque de restricciones dietéticas, mientras la pestaña `Mis pedidos` despliega una grilla Bento dedicada de tarjetas de estado escaneables con sincronización de ruta `/perfil#pedidos`.
2. **Given** un Comensal que modifica sus datos personales, tiene un cambio de correo pendiente de verificación o solicita eliminar su foto de perfil, **When** interactúa con el formulario, el aviso de correo pendiente o el modal de confirmación, **Then** todos los controles, etiquetas, avisos y acciones destructivas respetan la jerarquía Bento, contraste WCAG 2.2 AA y foco por teclado.
3. **Given** un Comensal que actualiza sus restricciones dietéticas y alérgenos, **When** selecciona o deselecciona opciones y guarda sus preferencias, **Then** cada opción es claramente distinguible en estado activo e inactivo (sin depender únicamente del color), ofrece confirmación clara al guardar y preserva exactamente el comportamiento de persistencia existente.
4. **Given** un Comensal revisando `Mis pedidos`, **When** visualiza pedidos activos o históricos, **Then** cada tarjeta muestra número de pedido, fecha, total en `JetBrains Mono`, insignia semántica de estado y acceso directo al seguimiento en vivo; cuando no existen pedidos o ocurre un fallo de carga, se muestran los primitivos de estado vacío o retroalimentación con reintento.

---

### Edge Cases

- **Enlace profundo de personal hacia `/login`**: Si un Encargado de Mostrador, Chef o Administrador es redirigido a `/login` desde una ruta de personal, la tarjeta centrada polimórfica debe heredar exclusivamente los tokens `--ee-*` del rol destino (incluyendo tema oscuro en Mostrador) sin filtrar fondos claros, bordes ni colores de Bento.
- **Nombres largos de establecimientos, platos o categorías**: Títulos extensos, descripciones largas o múltiples etiquetas de alérgenos no deben provocar desbordamiento horizontal en pantallas móviles de 360 px de ancho ni romper la alineación de las grillas Bento.
- **Ausencia de imagen de plato, logotipo de local o avatar de usuario**: Cuando un recurso visual no existe o falla al cargar, la tarjeta Bento debe renderizar un respaldo limpio y proporcional dentro de la paleta del tema sin roturas de diseño ni texto alternativo desbordado.
- **Establecimiento con colores de marca extremos (especificación `016`)**: Si un restaurante define colores muy claros, muy oscuros o saturados, los acentos de marca enfocados (banner, categoría activa, CTA principal y cabecera de confirmación) deben garantizar contraste WCAG 2.2 AA mediante los tokens de contraste calculados sin alterar la superficie ni las tarjetas Bento.
- **Múltiples modales y paneles deslizables en móvil**: Al abrir el modal de detalle de plato, el panel deslizable de filtros, el carrito deslizable o los modales de riesgo/conflicto/eliminación de foto, el foco del teclado debe gestionarse correctamente, permitir cierre con `Escape` y no producir desplazamiento atrapado ni doble scroll roto.
- **Preferencia del sistema por movimiento reducido**: Cuando el usuario tiene activada la reducción de movimiento, todas las transiciones de paneles deslizables, tarjetas, esqueletos de carga y temporizadores visuales deben reducir o suprimir animaciones no esenciales.
- **Desconexión temporal del seguimiento en vivo**: Si la conexión en tiempo real de seguimiento se interrumpe, la insignia de estado de conexión debe comunicar la reconexión o estado desconectado de manera legible y accesible sin ocultar la última información conocida del pedido ni el PIN si ya estaba disponible.
- **Invariante de pruebas conductuales `001`–`023`**: Ningún cambio de maquetación Bento puede eliminar nombres accesibles, roles semánticos, flujos de eventos o contratos de datos verificados por la suite de pruebas existente; ante cualquier aserción estrictamente acoplada a clases de diseño obsoletas, se permite únicamente su actualización acotada manteniendo intactas todas las aserciones conductuales.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001 (Conformidad estricta con el contrato Bento remediado)**: Todas las pantallas y componentes del alcance de la especificación `025` MUST adherirse integralmente a los tokens visuales, escala tipográfica (`DM Sans` para encabezados y cuerpo; `JetBrains Mono` para importes, códigos, PIN, fechas técnicas y etiquetas en mayúsculas), escala de espaciado (`4/8/12/16/24/32` px), radios de borde (`8/14/20` px) y pares de contraste WCAG 2.2 AA definidos en `.specify/memory/designs/comensal.DESIGN.md`.
- **FR-002 (Consumo de primitivos base y erradicación de estilos ad-hoc)**: Las pantallas y componentes rediseñados MUST adoptar los cinco primitivos base accesibles de la especificación `024` (botón de acción, insignia de estado, estado de retroalimentación, esqueleto de carga y estado vacío) en todos los puntos aplicables, evitando botones, alertas, badges, cargas o estados vacíos duplicados con colores codificados manualmente fuera del contrato de tokens.
- **FR-003 (Política Zero AI-Slop, modo Operate y disciplina Bento)**: La interfaz del Comensal MUST operar bajo criterios de sobriedad y escaneabilidad rápida (modo **Operate**), cumpliendo la política Zero AI-Slop de la constitución y las reglas de calidad Bento: prohibición de tipografía `Inter`, prohibición de degradados genéricos púrpura-azul, prohibición de tarjetas anidadas innecesarias (cards-in-cards), prohibición de texto gris de bajo contraste sobre superficies coloreadas, alineación tabular en tipografía monoespaciada (`JetBrains Mono`) para importes, subtotales, cuentas regresivas y PIN, micro-transiciones discretas (≤ 200 ms, suprimidas ante preferencia de movimiento reducido) y etiquetas claras sin ambigüedad.
- **FR-004 (Pantallas públicas y de acceso con tarjeta Bento polimórfica)**: Las cinco pantallas públicas y de autenticación (`/login`, `/register`, `/forgot-password`, `/reset-password`, `/auth/reset-password`, `/verificar-email`) MUST renderizarse mediante una estructura de tarjeta Bento centrada, responsiva e impulsada exclusivamente por tokens semánticos del tema activo, garantizando legibilidad y coherencia tanto en el tema Comensal (Bento) como cuando `/login` adopta el tema de un rol de personal por enlace profundo.
- **FR-005 (Descubrimiento de establecimientos en composición Bento orientada a búsqueda)**: La pantalla `/descubrimiento` MUST organizarse en bloques Bento alineados al flujo de búsqueda en vivo existente (sin requerir un listado masivo en servidor): tarjeta Bento de acceso rápido al establecimiento activo cuando exista selección previa, bloque héroe Bento de búsqueda accesible (activada desde 3 caracteres) con resultados claros y estados de carga, vacío y error mediante los primitivos base, y grilla Bento de tres bloques informativos de valor operativo.
- **FR-006 (Catálogo Bento mobile-first con filtros responsivos)**: La pantalla `/catalogo/*` MUST organizarse en bloques Bento claros: cabecera de identidad del establecimiento, avisos operativos/bloqueo accesibles, búsqueda y navegación fija de categorías, barra lateral colapsable de filtros dietéticos y alérgenos en escritorio (panel deslizable accesible en móvil), alternador maestro de perfil dietético, grilla modular de tarjetas de plato y modal de detalle de plato con información nutricional/alérgenos legible y accesible.
- **FR-007 (Seguridad alimentaria con contraste reforzado y redundancia visual)**: Las insignias de advertencia de alérgenos, etiquetas neutrales, filtros dietéticos y el modal de riesgo al agregar al carrito MUST garantizar contraste mínimo WCAG 2.2 AA (4.5:1 en texto y 3:1 en bordes/íconos) y transmitir siempre el riesgo mediante texto e indicador visual complementario, nunca únicamente mediante color.
- **FR-008 (Aislamiento enfocado de identidad del establecimiento — Spec 016)**: Dentro de la experiencia del Comensal (Catálogo, Checkout y Confirmación/Seguimiento), los colores personalizados de marca del establecimiento MUST aplicarse exclusivamente en cuatro puntos focales —banner/cabecera del local, indicador de categoría activa, botón principal de compra/agregar al carrito y cabecera de confirmación— reutilizando la capa de marca existente por establecimiento (con respaldo automático a los tokens Bento neutros cuando no haya identificador de establecimiento disponible) y manteniendo validación de contraste WCAG 2.2 AA, mientras superficie base, tarjetas de contenido, paneles de filtro y alertas semánticas permanecen regidos por los tokens Bento del sistema.
- **FR-009 (Carrito deslizable y flujos de confirmación)**: El botón flotante del carrito, el panel deslizable del carrito (`CartDrawer`), los ítems con insignias dietéticas y el modal de cambio de establecimiento MUST seguir la estética Bento, exhibir totales y precios alineados en tipografía monoespaciada, ofrecer objetivos táctiles mínimos de 44×44 px en controles de cantidad/cierre y gestionar correctamente el foco de teclado al abrir y cerrar.
- **FR-010 (Checkout Bento de dos columnas en escritorio y estados de contingencia)**: La pantalla `/checkout` MUST presentar una grilla Bento de 2 columnas en escritorio (bloques de modalidad de retiro, selector de fecha, grilla de franjas horarias y selección de medio de pago a la izquierda; resumen fijo del pedido con desglose en `JetBrains Mono` a la derecha) que colapsa limpiamente a columna única en móvil, integrando en el mismo lenguaje Bento todos los estados críticos (temporizador de orden pendiente, acreditación pendiente, pago en proceso, alertas de pago y modales de conflicto de stock/concurrencia).
- **FR-011 (Retorno de pago accesible)**: La pantalla `/checkout/payment-return` MUST presentar una tarjeta Bento centrada que comunique de forma inequívoca el resultado del pago (aprobado, pendiente, rechazado o error) usando los primitivos semánticos del tema y ofreciendo acciones directas de continuación o reintento.
- **FR-012 (Confirmación y seguimiento en vivo con tarjeta héroe de PIN)**: La pantalla de confirmación y seguimiento (`/checkout/confirmacion/:orderId`, `/pedidos/:orderId/seguimiento`, `/seguimiento/:orderId`) MUST organizarse en bloques Bento que incluyan cabecera con acento de marca del local, insignia accesible de estado de conexión en tiempo real, indicador visual de etapas del pedido, bloque de cuenta regresiva/demoras cuando corresponda, bloque de pedido entregado y, cuando el pedido esté listo para retirar, una tarjeta héroe Bento de alto contraste con el PIN antifraude en `JetBrains Mono` de gran tamaño.
- **FR-013 (Perfil del Comensal con pestañas Bento y grilla de dos columnas)**: La pantalla `/perfil` MUST preservar la navegación por pestañas accesibles (`role="tablist"`) entre `Mi perfil` (`/perfil`) y `Mis pedidos` (`/perfil#pedidos`) estilizada con estética Bento; dentro de `Mi perfil` MUST organizar en grilla Bento de 2 columnas en escritorio (y columna única en móvil) el bloque de identidad/avatar/datos personales junto al bloque de restricciones dietéticas (incluyendo el aviso de correo pendiente de verificación y el modal de eliminación de foto), y dentro de `Mis pedidos` MUST presentar una grilla Bento de tarjetas de estado con acceso directo al seguimiento.
- **FR-014 (Accesibilidad transversal WCAG 2.2 AA e interacción multimodal)**: Todas las vistas, tarjetas, formularios, paneles deslizables y modales del alcance MUST ser completamente operables con teclado, ratón y tacto; exhibir indicadores de foco visible (contraste mínimo 3:1), estados interactivos explícitos (reposo, hover, focus-visible, activo, deshabilitado, carga y error), etiquetas accesibles inequívocas, regiones vivas apropiadas para actualizaciones dinámicas y soporte para preferencia de movimiento reducido.
- **FR-015 (Responsividad sin desbordamiento)**: Todas las pantallas del alcance MUST adaptarse limpiamente desde dispositivos móviles pequeños (360 px de ancho) hasta pantallas de escritorio amplias (1440 px), manteniendo ritmo de espaciado Bento consistente y cero desplazamiento horizontal no intencional.
- **FR-016 (Invariante de lógica de negocio, contratos y auditoría visual)**: El rediseño MUST NOT modificar el servidor, los contratos de intercambio de datos ni la lógica de negocio de las especificaciones `001`–`023`; MUST mantener aprobada el 100 % de la suite de pruebas conductuales existente (autorizándose exclusivamente la actualización acotada de aserciones estrictamente acopladas a clases de maquetación previas si alguna se viera afectada, sin debilitar ninguna aserción conductual); y MUST superar la auditoría visual crítica contra `comensal.DESIGN.md` y WCAG 2.2 AA exigida por la constitución.

### Key Entities

- **Bloque Bento (`Bento Card / Surface Block`)**: Unidad modular de contenido con superficie elevada, borde definido, radio consistente y jerarquía tipográfica clara que agrupa información o acciones cohesivas sin anidamiento redundante de tarjetas.
- **Punto focal de marca del establecimiento (`Focused Brand Accent Slot`)**: Zona explícitamente delimitada dentro de la experiencia del Comensal (cabecera de local, categoría activa, CTA principal de compra y cabecera de confirmación) autorizada a renderizar los colores corporativos del establecimiento (especificación `016`) con contraste WCAG 2.2 AA garantizado.
- **Tarjeta héroe de retiro (`Pickup PIN Hero Block`)**: Bloque Bento de máxima prioridad visual activado cuando un pedido está listo para retirar, diseñado para lectura instantánea del código PIN antifraude a distancia en el mostrador.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001 (Cobertura total del flujo Comensal)**: El 100 % de las 11 pantallas definidas en el alcance (6 pantallas del flujo Comensal + 5 pantallas públicas/de acceso) operan íntegramente bajo el diseño Bento regido por `comensal.DESIGN.md`, sin pantallas mixtas ni componentes visuales heredados sin migrar.
- **SC-002 (Cumplimiento WCAG 2.2 AA verificado)**: El 100 % de las combinaciones texto/fondo (mínimo 4.5:1 en texto normal y 3:1 en texto grande) y componentes interactivos/bordes/foco (mínimo 3:1) en las 11 pantallas —tanto con branding neutro como con branding personalizado extremo de un establecimiento— cumplen WCAG 2.2 AA con cero defectos críticos o serios de accesibilidad.
- **SC-003 (Ergonomía móvil sin desbordamiento)**: El 100 % de las 11 pantallas se renderizan sin desbordamiento horizontal en anchos de 360 px, 768 px y 1440 px, con objetivos táctiles de al menos 44×44 px en las acciones principales de navegación, filtros, carrito, selección de franjas horarias y formularios.
- **SC-004 (Velocidad de reconocimiento en tareas críticas)**: Un Comensal puede identificar el estado actual y el PIN de retiro de un pedido listo en menos de 2 segundos al abrir el seguimiento, y distinguir platos con conflicto de alérgenos en menos de 3 segundos al escanear la grilla del catálogo.
- **SC-005 (Cero regresiones funcionales)**: El 100 % de las pruebas conductuales existentes de las especificaciones `001`–`024` y las nuevas pruebas de cobertura visual/accesibilidad de la especificación `025` pasan sin modificaciones a contratos de servidor ni debilitamiento de aserciones conductuales.
- **SC-006 (Aprobación de auditoría de diseño Zero AI-Slop y modo Operate)**: Las vistas rediseñadas superan la revisión crítica visual exigida por la constitución contra `comensal.DESIGN.md` bajo criterio **Operate**, con cero hallazgos bloqueantes de tipografía prohibida, tarjetas anidadas innecesarias, contraste insuficiente o transiciones superiores a 200 ms que ignoren movimiento reducido.

---

## Assumptions

- La arquitectura fundacional de la especificación `024` (tokens `--ee-*`, carga autoalojada de `DM Sans` y `JetBrains Mono`, `ThemeScopeProvider`, `RoleLayoutShell`, `RoleHamburgerMenu`, aislamiento con `BrandThemeProvider` y los cinco primitivos base) ya se encuentra integrada en la rama base `feat/frontend-redesign` y sirve como plataforma directa para `025`.
- Los contratos de datos y endpoints del servidor definidos en las especificaciones `001`–`023` permanecen estables y no requieren cambios para soportar el rediseño visual.
- Las pantallas específicas de los roles Encargado del Mostrador, Chef y Administrador (y el cambio obligatorio de contraseña de primer ingreso del personal) serán abordadas en especificaciones posteriores (`026`–`028`) y no se ven afectadas negativamente por esta especificación.
- Las pruebas automatizadas de interfaz y las verificaciones visuales en navegador real (móvil y escritorio) serán los medios de evidencia para validar la conformidad con WCAG 2.2 AA, la responsividad y la ausencia de regresiones.
