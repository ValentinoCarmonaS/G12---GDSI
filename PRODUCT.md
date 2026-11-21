# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

EasyEat atiende cuatro perfiles de usuario con contextos operativos diferenciados dentro de locales gastronómicos y pedidos de retiro:

- **Comensal (invitado o autenticado)**: persona que descubre establecimientos, filtra platos según sus restricciones dietéticas y alérgenos, arma su carrito, paga en línea (Mercado Pago) y sigue en vivo la preparación y el retiro de su pedido, mayoritariamente desde dispositivos móviles.
- **Encargado del Mostrador**: personal de salón encargado del despacho de pedidos listos y la validación antifraude mediante PIN de retiro en momentos de alta demanda.
- **Chef (personal de cocina)**: personal culinario que gestiona comandas en tiempo real (Kitchen Display System, KDS), ajusta demoras operativas, crea fichas técnicas de platos con recetas (BOM), pausa platos ante falta de stock y consulta insumos disponibles.
- **Administrador del establecimiento**: responsable de la gestión integral de su local multi-tenant: altas y control de acceso de personal, inventario y umbrales de stock crítico, auditoría de food-cost y fijación de precio de venta (PVP), horarios comerciales y capacidad por franja, identidad de marca del local y métricas financieras y ranking de platos.

## Product Purpose

EasyEat es una plataforma web multi-tenant de pedidos gastronómicos y operación integral de locales. Conecta la experiencia del comensal con el flujo operativo interno de cocina, mostrador y administración, garantizando transparencia nutricional y de alérgenos, trazabilidad de pedidos desde el pago hasta la entrega y control de rentabilidad por plato.

El éxito del producto se mide por pedidos completados sin incidentes de alérgenos ni entregas fraudulentas, tiempos de respuesta claros entre cocina y mostrador, control preciso de stock y costos por establecimiento, y accesibilidad operativa en móvil y escritorio.

## Positioning

A diferencia de plataformas genéricas de pedidos enfocadas únicamente en la vidriera comercial, EasyEat integra en un único sistema multi-tenant:

- Filtrado automático de catálogo basado en restricciones dietéticas y alérgenos del perfil del comensal, con alertas de riesgo explícitas antes de agregar al carrito (especificaciones `003`, `008`, `011`).
- Cálculo automático de food-cost desde recetas (BOM), auditoría administrativa de márgenes y aprobación de platos antes de su publicación (especificaciones `005`, `006`, `007`).
- Sincronización operativa de punta a punta: deducción transaccional de stock al confirmar el pago, cola KDS en tiempo real con ajuste granular de demoras (+5 minutos), seguimiento en vivo para el comensal y protocolo antifraude con PIN en mostrador (especificaciones `014`–`022`).
- Identidad visual diferenciada por rol operativo coexistiendo en capas aisladas con la marca personalizada de cada establecimiento (especificaciones `016` y `024`).

## Operating Context

- **Entornos de uso**: comensales en movilidad (pantallas de 360 px en adelante) y escritorio; personal de cocina y mostrador en terminales o tabletas durante servicio activo de hora pico; administradores en escritorio y móvil revisando inventario, aprobaciones y métricas BI.
- **Arquitectura de ejecución**: SPA en React 18.3, TypeScript 5.6, Vite 5.4, React Router DOM 6.27, Tailwind CSS 3.4, `lucide-react`, Recharts 3.11 y Axios 1.7; backend en Java 21 / Spring Boot con PostgreSQL; despliegue contenedorizado en Docker y Render con servidor estático Nginx (`easyeat/frontend/nginx.conf`).
- **Aislamiento multi-tenant**: cada establecimiento gestiona de forma aislada su personal, inventario, fichas de platos, franjas de retiro, pagos, métricas y marca.
- **Flujo de ingeniería**: Spec-Driven Development (SDD) gobernado por `.specify/memory/constitution.md` (v2.1.0), con contratos funcionales verificados en `specs/001-*` a `specs/024-*`.

## Capabilities and Constraints

- **Capacidades vigentes (especificaciones `001`–`023`)**: autenticación centralizada JWT y recuperación de contraseña (`001`), guardas de rol y cambio obligatorio de contraseña en primer ingreso (`002`), perfil y filtrado dietético (`003`), gestión de personal RBAC e inventario (`004`), gestión de platos por Chef con acceso directo en cabecera escritorio C-08 (`005`), motor de food-cost (`006`), auditoría y publicación PVP (`007`), descubrimiento multi-tenant y catálogo en tiempo real (`008`), carrito con validación de stock y datos demográficos (`009`), configuración operativa y medios de pago (`010`), detalle nutricional y filtros manuales (`011`), franjas horarias de retiro (`012`), stock crítico y pausa manual de platos (`013`), checkout con Mercado Pago y descuento transaccional (`014`, `015`, `018`), confirmación pospago, seguimiento en vivo y branding por local (`016`), despliegue Docker/Render (`017`), KDS (`019`), despacho en mostrador (`020`), demora granular en vivo (`021`), entrega con PIN antifraude (`022`), control de acceso de personal y analíticas BI (`023`).
- **Restricciones técnicas y de gobernanza**:
  - Las refactorizaciones visuales no deben modificar contratos HTTP de Axios, lógica de negocio del cliente ni debilitar las suites de pruebas conductuales existentes en Vitest + React Testing Library.
  - Los activos tipográficos deben servirse autoalojados desde la propia aplicación, sin peticiones a CDN externos en tiempo de ejecución.
  - La capa de marca personalizada por establecimiento (`--brand-*`, especificación `016`) debe permanecer estrictamente aislada del tema de rol y aplicarse solo en las zonas designadas de la experiencia del Comensal.

## Brand Commitments

- **Nombre del producto**: EasyEat.
- **Idioma de interfaz**: español (registro claro y directo, con formato monetario `es-AR` en métricas y fechas).
- **Contratos visuales vinculantes por rol**: `.specify/memory/designs/comensal.DESIGN.md` (Bento), `.specify/memory/designs/mostrador.DESIGN.md` (Bold), `.specify/memory/designs/chef.DESIGN.md` (Brutalism) y `.specify/memory/designs/admin.DESIGN.md` (Glassmorphism).
- **Política constitucional Zero AI-Slop**: prohibidos `Inter`, degradados genéricos púrpura-azul, tarjetas anidadas innecesarias y texto gris de bajo contraste sobre superficies coloreadas.
- **Respaldo neutro de marca de local**: logotipo institucional neutro en `/assets/brand/easyeat-logo-neutral.svg` cuando un restaurante no configuró marca propia.

## Evidence on Hand

- Especificaciones funcionales, planes y contratos verificados: `specs/001-central-auth-recovery/` hasta `specs/024-ui-theme-architecture/`.
- Constitución del proyecto y contratos de diseño: `.specify/memory/constitution.md` y `.specify/memory/designs/*.DESIGN.md`.
- Documentación operativa y credenciales de entorno de desarrollo: `README.md`.
- Código fuente y suites de pruebas vigentes: `easyeat/frontend/src/`, `easyeat/frontend/tests/` y `easyeat/backend/`.
- **Ausencias declaradas**: no existen métricas públicas de adopción en producción, testimonios de clientes ni benchmarks comerciales publicados; ningún artefacto futuro debe fabricarlos.

## Product Principles

1. **Seguridad alimentaria y operativa primero**: las alertas de alérgenos, el estado del stock y la validación antifraude con PIN priman sobre cualquier adorno visual.
2. **Ergonomía adaptada al puesto de trabajo**: cada rol opera en un entorno distinto (movilidad del comensal, hora pico en mostrador, velocidad de un toque en cocina y gestión analítica administrativa); la interfaz refleja ese contexto sin fricción.
3. **Fuente única de verdad y trazabilidad SDD**: toda capacidad y contrato visual responde a una especificación aprobada y verificable mediante pruebas automatizadas.
4. **Aislamiento multi-tenant y por capas**: los datos y la identidad personalizada de un establecimiento nunca se filtran hacia otros locales ni hacia los espacios internos del personal.
5. **Accesibilidad no negociable**: la legibilidad, el contraste y la operabilidad por teclado prevalecen sobre cualquier preferencia estética en conflicto.

## Accessibility & Inclusion

- **Estándar obligatorio**: WCAG 2.2 nivel AA en todos los roles y vistas (contraste mínimo 4.5:1 para texto normal, 3:1 para texto grande, componentes interactivos e indicadores de foco).
- **Operabilidad**: navegación completa por teclado, foco visible consistente, objetivos táctiles mínimos de 44×44 px en acciones principales (nunca inferiores a 24×24 px en controles compactos), atributos ARIA semánticos y soporte explícito de `prefers-reduced-motion`.
- **Comunicación multimodal**: ningún estado semántico ni alerta crítica depende exclusivamente del color; se combinan etiquetas textuales, íconos y regiones vivas diferenciadas (`assertive` para errores críticos, `polite` para confirmaciones e información).
