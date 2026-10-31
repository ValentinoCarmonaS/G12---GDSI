# Feature Specification: Shopping Cart Management & Diner Profile Demographics

**Feature Branch**: `009-shopping-cart`

**Created**: 31/10/2026

**Status**: Draft

**Input**: User description: "Sistema web de pedidos de comida. Iteración actual extiende funcionalidad existente de carrito y perfil de comensal. Cuatro historias de usuario aprobadas y cerradas: COM-6.1.1 (Agregar plato al pedido), COM-6.1.2 (Ver acumulado del carrito), COM-6.1.3 (Modificar cantidades) y COM-1.4.2 (Editar datos demográficos)."

---

## Clarifications

### Session 2026-10-31 (Alineación con Historias Aprobadas y Arquitectura de Dominio)

- Q: ¿Debe la vista de detalle del plato permitir seleccionar la cantidad antes de presionar "Agregar al Carrito", o añade siempre 1 unidad por pulsación? (COM-6.1.1 AC-1.1, FR-001, FR-002) → A: La vista de detalle del plato incluye un selector numérico previo de cantidad (iniciado por defecto en 1 y acotado entre 1 y el stock real disponible en cocina); al pulsar "Agregar al Carrito", se añade la cantidad elegida a la sesión local en una sola interacción.
- Q: ¿En qué momento debe surtir efecto la eliminación de la foto de perfil tras confirmar la acción? (FR-024, FR-025, COM-1.4.2 AC-4.5) → A: Confirmación diferida al formulario: al confirmar la eliminación de la imagen en el diálogo de verificación, la interfaz actualiza la vista previa mostrando el avatar por defecto de forma provisional; la remoción efectiva y la persistencia en el servidor y en la barra de navegación se concretan únicamente al presionar "Guardar cambios" en el formulario demográfico. Si el comensal cancela o navega fuera sin guardar, la foto original se conserva intacta.
- Q: ¿Cómo debe comportarse el carrito si el stock en cocina desciende por debajo de una cantidad ya agregada mientras el comensal navega? (FR-014, INV-3) → A: Preservar el ítem en el carrito marcándolo con una advertencia visible de "Stock insuficiente", deshabilitando la acción de suma y bloqueando el avance a la compra/checkout. La única forma de proceder es que el comensal ajuste manualmente la cantidad de ese producto al stock disponible remanente o lo elimine de la orden.
- Q: ¿En qué pantallas de la aplicación debe permanecer visible el botón flotante del carrito cuando contiene ítems? (FR-005, FR-008) → A: Visible exclusivamente dentro de las vistas del catálogo y detalle de platos del establecimiento gastronómico activo. Se oculta deliberadamente al ingresar a pantallas de edición de perfil, configuración o autenticación para evitar superposiciones con formularios.
- Q: ¿Cómo debe el sistema validar el stock de cocina y sincronizar los cambios de cantidad al presionar suma o resta en el modal del carrito? (FR-014, FR-016) → A: Actualización optimista inmediata en la interfaz con sincronización diferida (*debouncing* de 300 ms) hacia el servicio de catálogo/inventario; si el backend detecta stock insuficiente o hay corte de red, revierte de inmediato el contador al último valor verificado y notifica el error de conexión o inventario.
- Q: ¿Cómo se gestiona el carrito de compras respecto a la sesión y el establecimiento activo? (COM-6.1.1, COM-6.1.2) → A: El carrito de compras se almacena en la sesión local del comensal y se encuentra estrictamente asociado al establecimiento gastronómico activo en el que el comensal está navegando (en concordancia con la spec 008, donde cambiar de local alerta al usuario sobre el vaciado del carrito).
- Q: ¿Qué sucede al agregar un plato que ya existe en el carrito? (COM-6.1.1) → A: No se crea una nueva línea independiente; el sistema acumula la cantidad unitaria del plato existente incrementando su contador en 1 unidad, siempre que no exceda el stock real disponible en cocina.
- Q: ¿Cuál es el comportamiento de visibilidad del botón flotante del carrito? (COM-6.1.2) → A: Rigurosamente biunívoco (INV-6 e INV-7): si el carrito tiene al menos un ítem con cantidad ≥ 1, el botón flotante permanece visible en todas las vistas de navegación del catálogo; si el carrito queda con 0 ítems, el indicador flotante se oculta de inmediato.
- Q: ¿Cómo se valida la disponibilidad de inventario al sumar cantidades? (COM-6.1.3) → A: El sistema consulta el stock real disponible del plato en cocina. Cuando la cantidad en el carrito iguala dicho límite, el control de suma se deshabilita visual y funcionalmente, desplegando una advertencia informativa de límite de inventario o simplemente un "sin stock".
- Q: ¿Cómo responde el sistema ante un fallo de comunicación o de red durante modificaciones rápidas de cantidades? (COM-6.1.3) → A: Se aplica reversión de estado (rollback): el contador del ítem se retrotrae al último valor sincronizado de forma consistente y se presenta una notificación explícita de error de conexión.
- Q: ¿Cuáles son las reglas de validación para la edición de datos demográficos? (COM-1.4.2) → A:
  - Foto de perfil: formato de imagen válido (JPEG/JPG, PNG, WEBP), tamaño estrictamente mayor a 0 y menor o igual a 5 MB (5.242.880 bytes). Archivos que excedan 5 MB o formatos no permitidos son rechazados con mensajes explicativos específicos. La eliminación de foto restablece el avatar predeterminado de la plataforma.
  - Edad: valor numérico entero positivo dentro de un rango biológico coherente (14 a 120 años). Se rechazan números negativos, decimales, valores menores a 14, valores mayores a 120 y caracteres alfanuméricos.
  - Género: selección opcional a partir de una lista cerrada estándar (Femenino, Masculino, Prefiero no responder).
  - Domicilio: campo de texto libre (hasta 255 caracteres) destinado exclusivamente a información de contacto del perfil, sin realizar validaciones geográficas, cálculos de rutas ni conexiones con plataformas externas de logística o delivery.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Agregar plato al pedido (Priority: P1)

Como comensal,  
quiero tocar un botón para añadir un plato al carrito,  
para ir armando mi orden antes de pagar.

**Why this priority**: Es la acción fundamental de conversión en el flujo de compra gastronómica (`COM-6.1.1`). Sin la capacidad de seleccionar y agregar platos al pedido, no existe posibilidad de generar órdenes ni transaccionar en la plataforma.

**Independent Test**: Puede probarse de forma autónoma ingresando al detalle de cualquier plato con disponibilidad de stock, presionando "Agregar al Carrito", y verificando que el producto se sume a la sesión local con una notificación visible de confirmación de éxito.

**Acceptance Scenarios**:

1. **Dado** que el comensal está en la vista de detalle de un plato con stock disponible, **Cuando** ajusta la cantidad deseada mediante el selector numérico previo (por defecto 1 unidad) y presiona "Agregar al Carrito", **Entonces** dicha cantidad del producto se añade a la sesión local del comensal y el sistema despliega una notificación visible de éxito confirmando la incorporación.
2. **Dado** que el comensal agrega un plato que ya se encuentra presente en el carrito con cantidad $k$, **Cuando** selecciona $m$ unidades en el detalle del plato y presiona "Agregar al Carrito", y la cantidad acumulada $k + m$ no supera el stock real disponible, **Entonces** el sistema incrementa la cantidad unitaria existente a $k + m$, actualiza los totales y muestra la notificación de éxito sin duplicar la fila del ítem.
3. **Dado** que el comensal visualiza el detalle de un plato cuyo stock real disponible en cocina es igual a 0 (agotado), **Cuando** inspecciona las acciones del plato, **Entonces** el botón "Agregar al Carrito" se encuentra deshabilitado, impidiendo cualquier intento de agregarlo a la sesión local.
4. **Dado** que un plato ya posee en el carrito una cantidad igual al stock real disponible en cocina, **Cuando** el comensal intenta pulsar nuevamente "Agregar al Carrito" desde el detalle, **Entonces** el sistema bloquea la acción y presenta una advertencia informativa indicando que se ha alcanzado la disponibilidad máxima del producto.
5. **Dado** un comensal con restricciones dietarias configuradas que visualiza un plato en conflicto según las políticas de la especificación 003, **Cuando** presiona "Agregar al Carrito", **Entonces** el sistema despliega la advertencia preventiva de salud; al confirmar el comensal la asunción de riesgo, el plato se suma al carrito reteniendo su distintivo de advertencia dietaria.

---

### User Story 2 - Ver acumulado del carrito y detalle interactivo (Priority: P1)

Como comensal,  
quiero ver un botón flotante que indique la cantidad de ítems seleccionados y el precio total,  
para tener presente cuánto estoy gastando mientras navego.

**Why this priority**: Proporciona visibilidad continua del estado de la orden y control presupuestario (`COM-6.1.2`). Permite al comensal inspeccionar en cualquier instante el desglose de su compra sin interrumpir la exploración del catálogo ni perder el contexto de navegación.

**Independent Test**: Puede probarse de forma autónoma agregando uno o más productos al carrito, recorriendo distintas categorías y pantallas del catálogo, y comprobando la presencia permanente del botón flotante con el conteo total de unidades y el subtotal monetario exacto; al interactuar con el botón flotante, debe desplegarse el modal o panel lateral con el desglose de productos; al vaciar el carrito, el botón debe desaparecer inmediatamente.

**Acceptance Scenarios**:

1. **Dado** que el comensal posee al menos un producto en su sesión local de carrito, **Cuando** navega por las distintas secciones del catálogo del establecimiento, **Entonces** el sistema despliega un botón flotante visible en todo momento indicando la cantidad total de unidades seleccionadas y el subtotal monetario acumulado.
2. **Dado** que el comensal visualiza el botón flotante con productos cargados, **Cuando** elimina el último producto restante dejando la sesión de carrito vacía (cero unidades), **Entonces** el sistema oculta inmediatamente el indicador flotante de la interfaz.
3. **Dado** que el comensal presiona el botón flotante desde cualquier vista de navegación del catálogo, **Cuando** el sistema registra la interacción, **Entonces** despliega un modal superpuesto o pantalla lateral mostrando el detalle completo de los productos seleccionados (nombre de cada plato, precio unitario, cantidad asignada y subtotal por línea).
4. **Dado** que el comensal agrega o remueve productos desde cualquier punto de la aplicación, **Cuando** el sistema procesa la modificación, **Entonces** el botón flotante actualiza la cantidad acumulada de ítems y el subtotal monetario en tiempo real sin requerir recargar la página web.

---

### User Story 3 - Modificar cantidades y remover productos (Priority: P1)

Como comensal revisando mi orden,  
quiero sumar o restar unidades de un producto ya agregado desde la vista del carrito,  
para corregir errores rápidos sin volver al menú principal.

**Why this priority**: Otorga control y flexibilidad inmediata sobre el pedido (`COM-6.1.3`). Evita la fricción de navegación para corregir cantidades y protege la consistencia con el inventario físico disponible en cocina antes de proceder al pago.

**Independent Test**: Puede probarse de forma autónoma abriendo el modal del carrito, presionando los controles de incremento y decremento de un producto, verificando el recálculo instantáneo de subtotales y total; comprobando que al alcanzar el stock real se deshabilite la adición; verificando que restar sobre cantidad 1 retire el plato; y simulando una falla de red para verificar la reversión del contador y el mensaje de alerta.

**Acceptance Scenarios**:

1. **Dado** que el comensal tiene abierto el modal del carrito con productos agregados, **Cuando** presiona el control de suma sobre un plato, **Entonces** el sistema incrementa en 1 la cantidad unitaria del plato y recalcula al instante el subtotal de esa línea y el total general de la orden.
2. **Dado** que el comensal tiene un plato con cantidad mayor a 1 en el modal del carrito, **Cuando** presiona el control de resta sobre dicho plato, **Entonces** el sistema reduce en 1 la cantidad unitaria y recalcula al instante el subtotal de la línea y el monto total de la orden.
3. **Dado** que el comensal presiona el control de suma para incrementar unidades de un plato, **Cuando** la cantidad seleccionada alcanza el límite máximo de stock real disponible en cocina para dicho producto, **Entonces** el sistema deshabilita la acción de suma y despliega una advertencia visible indicando el límite de inventario alcanzado.
4. **Dado** que el comensal tiene exactamente una sola unidad (cantidad = 1) de un plato en el carrito, **Cuando** presiona el control de resta sobre dicho plato, **Entonces** el sistema retira el producto completamente de la orden, remueve la línea correspondiente y actualiza los montos monetarios asociados.
5. **Dado** que el comensal modifica rápidamente las cantidades de uno o más platos, **Cuando** el sistema experimenta un fallo de red o pérdida de comunicación antes de sincronizar el nuevo estado con el servicio, **Entonces** revierte el contador del ítem a su estado numérico previo confirmado y notifica al usuario el error de conexión.

---

### User Story 4 - Editar datos demográficos y foto de perfil (Priority: P2)

Como comensal,  
quiero agregar foto de perfil, edad, género y domicilio,  
para personalizar mi experiencia y agilizar futuros pedidos a domicilio.

**Why this priority**: Personaliza la identidad del usuario en la plataforma y completa su expediente de contacto (`COM-1.4.2`). Complementa los datos personales base (nombre, apellido, correo y teléfono gestionados en la especificación 003).

**Independent Test**: Puede probarse de forma autónoma ingresando a la sección de edición demográfica del perfil, subiendo una imagen válida ≤ 5 MB y confirmando la actualización del avatar en la barra de navegación; probando la subida de archivos > 5 MB o formatos no permitidos para verificar su rechazo; ingresando valores de edad válidos e inválidos; eliminando la foto para validar el restablecimiento del avatar por defecto; y guardando el domicilio para verificar su almacenamiento exclusivamente como dato de contacto.

**Acceptance Scenarios**:

1. **Dado** que el comensal edita sus datos demográficos en el formulario de perfil, **Cuando** sube una imagen de perfil en formato válido admitido con tamaño menor o igual a 5 MB y guarda los cambios, **Entonces** el sistema actualiza su avatar y la nueva foto se visualiza inmediatamente en la barra de navegación superior.
2. **Dado** que el comensal intenta cargar una imagen de perfil, **When** el archivo supera el límite de 5 MB (5.242.880 bytes), **Entonces** el sistema rechaza el archivo, bloquea la subida y despliega un mensaje visible indicando el peso máximo permitido (5 MB).
3. **Dado** que el comensal selecciona un archivo para su foto de perfil, **When** el formato del archivo no corresponde a una imagen admitida (formatos permitidos: JPEG, PNG, WEBP), **Entonces** el sistema bloquea la subida y despliega un mensaje especificando los formatos soportados.
4. **Dado** que el comensal completa el campo de edad en el formulario, **When** ingresa un valor numérico negativo, menor a 14, mayor a 120, con decimales o con caracteres alfanuméricos, **Entonces** el sistema invalida el campo impidiendo guardar y solicita ingresar una edad coherente.
5. **Dado** que el comensal completa el campo de edad con un valor entero comprendido entre 14 y 120 años y guarda, **Entonces** el sistema valida y almacena exitosamente la edad en el perfil del usuario.
6. **Dado** que el comensal posee una foto de perfil personalizada configurada, **When** selecciona la opción de eliminar imagen, confirma la acción en el diálogo de verificación y presiona "Guardar cambios", **Entonces** el sistema remueve la foto personalizada de forma persistente y restablece el avatar predeterminado de la plataforma en la barra de navegación y en el perfil. Si no guarda, la foto previa se conserva intacta.
7. **Dado** que el comensal carga su domicilio en el formulario de perfil, **When** guarda exitosamente los cambios, **Entonces** el sistema almacena la dirección únicamente como dato de contacto de su perfil personal, sin realizar cálculos de rutas de envío, estimaciones de distancias ni integraciones con servicios de delivery externos.
8. **Dado** que el comensal selecciona su género a partir de las opciones estándar disponibles (Femenino, Masculino, No binario, Prefiero no responder) o lo deja sin especificar, **When** guarda el formulario, **Entonces** el sistema almacena la preferencia seleccionada en su perfil.

---

### Edge Cases

- **Agregar el mismo plato reiteradamente**: Cada adición sucesiva acumula $+1$ a la cantidad del ítem existente; en ningún caso se generan registros duplicados para el mismo plato en el carrito.
- **Cotas del selector de cantidad en el detalle**: El selector numérico previo en la vista de detalle del plato restringe la selección a valores enteros entre 1 y el stock real disponible en cocina; no admite números negativos, cero, decimales ni valores que superen el inventario disponible.
- **Stock exacto y pulsación repetida**: Si el plato tiene stock real de 5 unidades y el carrito ya cuenta con 5 unidades, la opción de suma permanece desactivada para ese plato; pulsaciones adicionales no modifican la cantidad ni generan inconsistencias en los subtotales.
- **Reducción concurrente de stock en cocina**: Si un plato ya está en el carrito con cierta cantidad y el stock de cocina se reduce por debajo de esa cantidad (incluso a cero), el producto no se desaloja automáticamente; se despliega una advertencia prominente de "Stock insuficiente", se deshabilita la suma y se bloquea el avance a la compra hasta que el comensal ajuste manualmente la cantidad al stock real o lo retire.
- **Restar sobre la última unidad disponible**: Si la cantidad de un producto es 1 y se presiona resta, el sistema elimina completamente el ítem de la lista del carrito sin dejar cantidades en 0 ni números negativos.
- **Vaciado completo del carrito desde el modal**: Si el carrito contiene un único producto de cantidad 1 y se presiona resta, la línea se elimina, el carrito pasa a estado vacío, el modal refleja el estado vacío y el botón flotante exterior se oculta de inmediato.
- **Modificaciones rápidas sucesivas y corte de red**: Si el comensal presiona varias veces incremento o decremento en rápida sucesión, el sistema actualiza la interfaz optimistamente y emite la sincronización al cumplirse 300 ms de inactividad (*debouncing*); si dicha llamada falla por pérdida de conexión o rechazo de stock, el contador del ítem se retrotrae al último valor sincronizado confirmado y se muestra una notificación de error.
- **Precisión en centavos y sumas acumuladas**: Platos con valores fraccionarios (ej. \$9,99, \$14,50) acumulados en diversas cantidades deben computar sumas y multiplicaciones mediante aritmética decimal exacta de punto fijo a dos decimales, garantizando que no existan discrepancias por redondeo de punto flotante en subtotales ni totales generales.
- **Plato con stock inicial igual a 0**: Si un plato se encuentra sin existencias en cocina, la acción de añadir al carrito se presenta deshabilitada tanto en la tarjeta del catálogo como en la vista de detalle, impidiendo que entre a la sesión de compra.
- **Archivo de imagen de perfil de exactamente 5,0 MB**: Un archivo de exactamente 5.242.880 bytes se considera válido y dentro del límite permitido (límite superior inclusivo). Un archivo de 5.242.881 bytes (5,000001 MB) es rechazado.
- **Archivo con formato MIME no admitido**: Archivos con extensiones o contenidos incompatibles (ej. `.pdf`, `.gif`, `.svg`, `.exe`, `.bmp`) son bloqueados en el momento de la selección antes de cualquier intento de persistencia, notificando los tipos permitidos (JPEG, PNG, WEBP).
- **Límites numéricos de edad**: Una edad de 13 años o menor, o de 121 años o mayor, es rechazada como valor biológicamente incoherente para un comensal titular. Los valores límites de 14 y 120 años son aceptados. La presencia de símbolos, signos negativos o letras (ej. `-25`, `25a`, `30.5`) invalida el campo inmediatamente.
- **Eliminación de foto sin foto previa**: Si el usuario no tiene cargada una foto personalizada previa y solo dispone del avatar predeterminado, la acción de eliminar foto se mantiene oculta o deshabilitada, impidiendo estados inconsistentes.
- **Domicilio en formato de texto libre**: El domicilio admite cadenas de caracteres alfanuméricas con espacios y signos de puntuación habituales (calle, numeración, piso, departamento, código postal) hasta 255 caracteres, tratándose puramente como información descriptiva de contacto.
- **Cierre del modal sin descartar cambios**: Al cerrar el modal superpuesto o pantalla lateral del carrito, los productos y cantidades seleccionadas se conservan íntegramente en la sesión local.

---

## Requirements *(mandatory)*

### Mathematical & Business Invariants

- **INV-1 (Cantidad total del carrito)**: La cantidad total de ítems exhibida en el indicador flotante y en el resumen del pedido debe ser exactamente igual a la suma aritmética de las cantidades unitarias de todos los ítems contenidos en la sesión local:
  $$\text{cantidad\_total\_carrito} = \sum_{i=1}^{n} \text{cantidad\_unitaria}_i$$
  *Ejemplo numérico*: Si el carrito contiene 2 unidades del Plato A y 3 unidades del Plato B, la cantidad total del carrito es estrictamente $2 + 3 = 5$ unidades.

- **INV-2 (Subtotal y total monetario exacto)**: El subtotal monetario de cada ítem corresponde a su precio unitario de venta multiplicado por su cantidad, y el subtotal total del carrito corresponde a la suma de los subtotales de todas las líneas:
  $$\text{subtotal\_item}_i = \text{precio\_unitario}_i \times \text{cantidad\_unitaria}_i$$
  $$\text{subtotal\_carrito} = \sum_{i=1}^{n} \text{subtotal\_item}_i$$
  Todos los cálculos deben realizarse con precisión decimal exacta a dos decimales monetarios, sin drift de punto flotante ni discrepancias por redondeo. La comparación de montos se realiza por igualdad aritmética estricta.
  *Ejemplo numérico*: 3 ítems a \$12,33 totalizan exactamente \$36,99; sumados a 1 ítem a \$7,01 dan un total exacto de \$44,00.

- **INV-3 (Cota superior de stock disponible)**: Para todo ítem presente en el carrito, su cantidad solicitada nunca debe exceder el stock real disponible en cocina para dicho producto:
  $$\forall i \in \text{Carrito}, \quad \text{cantidad\_unitaria}_i \le \text{stock\_real\_disponible}_i$$
  Al alcanzarse la igualdad ($\text{cantidad\_unitaria}_i = \text{stock\_real\_disponible}_i$), cualquier acción de incremento debe quedar deshabilitada. Si el stock en cocina desciende concurrentemente quedando por debajo de la cantidad ya agregada ($\text{cantidad\_unitaria}_i > \text{stock\_real\_disponible}_i$), el ítem entra en estado de bloqueo ("Stock insuficiente") y el sistema impide proceder a la compra hasta que el comensal ajuste manualmente la cantidad a la disponibilidad real o remueva el producto.

- **INV-4 (Cota de tamaño de imagen de perfil)**: Todo archivo cargado como foto de perfil debe poseer un tamaño en bytes estrictamente mayor a cero y menor o igual a 5 MB (5.242.880 bytes):
  $$0 < \text{tamaño\_imagen\_bytes} \le 5.242.880$$
  Cualquier archivo fuera de este intervalo debe ser rechazado.

- **INV-5 (Rango biológico coherente de edad)**: La edad ingresada por el comensal debe ser un número entero estrictamente comprendido entre 14 y 120 años:
  $$\text{edad} \in \mathbb{Z} \quad \land \quad 14 \le \text{edad} \le 120$$
  Se rechazan valores nulos no permitidos, números negativos, valores con coma/punto decimal o caracteres no numéricos.

- **INV-6 (Ocultamiento de carrito vacío)**: La sesión del carrito se encuentra vacía si y solo si el botón flotante se encuentra completamente oculto de la interfaz:
  $$\text{sesión\_carrito\_vacía} \iff \text{botón\_flotante\_oculto}$$

- **INV-7 (Visibilidad contextual de carrito no vacío)**: La sesión del carrito contiene al menos un producto con cantidad $\ge 1$ y el comensal navega dentro del catálogo del establecimiento activo si y solo si el botón flotante se encuentra visible en la interfaz:
  $$\neg(\text{sesión\_carrito\_vacía}) \land \text{vista\_en\_catálogo} \iff \text{botón\_flotante\_visible}$$
  Exhibiendo en todo momento el valor de $\text{cantidad\_total\_carrito}$ e $\text{subtotal\_carrito}$ actualizado.

- **INV-8 (Aislamiento funcional del domicilio)**: El domicilio del perfil se almacena y consulta única y exclusivamente como texto informativo de contacto:
  $$\text{domicilio} \implies \text{dato\_contacto\_perfil}$$
  No alimenta motores de ruteo, no calcula tarifas de envío, no genera coordenadas espaciales ni se envía a servicios logísticos externos en esta iteración.

- **INV-9 (Reversibilidad y sincronización optimista diferida)**: Ante modificaciones continuas de cantidades, el cliente aplica respuesta visual inmediata y consolida los cambios con una ventana de amortiguación (*debouncing* de 300 ms); ante cualquier interrupción de red o inconsistencia reportada por el servicio de inventario, el contador del ítem debe revertirse de forma determinista al último valor confirmado:
  $$\text{fallo\_red\_o\_stock\_insuficiente} \implies \text{contador\_revertido\_al\_previo} \land \text{notificación\_error}$$

---

### Functional Requirements

- **FR-001**: El sistema DEBE ofrecer en la vista de detalle del plato un selector previo de cantidad (con valor inicial predeterminado de 1 unidad, configurable por el comensal entre 1 y el stock real disponible en cocina) junto con la acción "Agregar al Carrito". *(Cubre COM-6.1.1 AC-1.1)*
- **FR-002**: Al presionar "Agregar al Carrito", el sistema DEBE registrar la cantidad seleccionada del producto en la sesión local del comensal y desplegar una notificación visible de confirmación de éxito. *(Cubre COM-6.1.1 AC-1.1)*
- **FR-003**: Si el comensal agrega un plato que ya existe en el carrito con cantidad $k$, el sistema DEBE sumar la cantidad seleccionada $m$ a la cantidad existente ($k + m$) sin crear líneas duplicadas, siempre que la cantidad total acumulada no supere el stock real disponible en cocina. *(Cubre COM-6.1.1 AC-1.1, INV-1, INV-3)*
- **FR-004**: El sistema DEBE mantener el carrito de compras asociado al contexto del establecimiento gastronómico activo en el que el comensal está interactuando. *(Consistencia con Spec 008 FR-029)*
- **FR-005**: El sistema DEBE desplegar un botón flotante interactivo exclusivamente dentro de las vistas de navegación del catálogo y pantallas de detalle de platos del establecimiento gastronómico activo, siempre que la sesión local contenga al menos un producto (cantidad total $\ge 1$), ocultándose en vistas de perfil o ajustes. *(Cubre COM-6.1.2 AC-2.1, INV-7)*
- **FR-006**: El botón flotante DEBE exhibir en todo momento la cantidad total de unidades acumuladas en el carrito y el subtotal monetario consolidado. *(Cubre COM-6.1.2 AC-2.1, INV-1, INV-2)*
- **FR-007**: El sistema DEBE actualizar en tiempo real los valores de cantidad y subtotal exhibidos en el botón flotante inmediatamente después de cualquier adición, modificación o eliminación de ítems, sin requerir recarga de la página. *(Cubre COM-6.1.2 AC-2.4)*
- **FR-008**: Cuando la cantidad total de productos en el carrito llegue a cero (sesión vacía), el sistema DEBE ocultar el botón flotante de manera inmediata. *(Cubre COM-6.1.2 AC-2.2, INV-6)*
- **FR-009**: Al recibir una pulsación sobre el botón flotante desde cualquier pantalla del catálogo, el sistema DEBE desplegar un modal superpuesto o panel lateral con el detalle íntegro de la orden. *(Cubre COM-6.1.2 AC-2.3)*
- **FR-010**: La vista detallada del carrito DEBE mostrar, para cada plato seleccionado, su nombre, precio unitario de venta, controles de incremento y decremento de cantidad, subtotal por ítem y el total consolidado de la orden. *(Cubre COM-6.1.2 AC-2.3, COM-6.1.3 AC-3.1)*
- **FR-011**: Desde la vista detallada del carrito, el sistema DEBE permitir al comensal presionar controles de suma (+) o resta (-) sobre cada plato individual. *(Cubre COM-6.1.3 AC-3.1)*
- **FR-012**: Al presionar el control de suma sobre un plato, el sistema DEBE incrementar en 1 su cantidad unitaria y recalcular de forma instantánea el subtotal del ítem y el monto total de la orden. *(Cubre COM-6.1.3 AC-3.1, INV-1, INV-2)*
- **FR-013**: Al presionar el control de resta sobre un plato con cantidad $> 1$, el sistema DEBE reducir en 1 su cantidad unitaria y recalcular de forma instantánea el subtotal del ítem y el total de la orden. *(Cubre COM-6.1.3 AC-3.1, INV-1, INV-2)*
- **FR-014**: Si el comensal intenta presionar el control de suma y la cantidad seleccionada iguala el stock real disponible en cocina para dicho plato, el sistema DEBE deshabilitar el control de suma y presentar una advertencia visible indicando que se alcanzó el límite de inventario. *(Cubre COM-6.1.3 AC-3.2, INV-3)*
- **FR-014a**: Si el stock real en cocina desciende por debajo de la cantidad ya presente en el carrito mientras el comensal navega o revisa la orden, el sistema DEBE preservar el producto sin expulsarlo forzosamente, exhibir una advertencia visible de "Stock insuficiente", deshabilitar la acción de suma y bloquear el avance a la compra/checkout hasta que el comensal ajuste manualmente la cantidad al stock remanente o retire el ítem. *(Cubre COM-6.1.3 AC-3.2, INV-3)*
- **FR-015**: Si un plato posee una cantidad igual a 1 en el carrito y el comensal presiona el control de resta (-), el sistema DEBE retirar el plato íntegramente de la orden, eliminar su fila del carrito y actualizar inmediatamente la cantidad total y el monto acumulado. *(Cubre COM-6.1.3 AC-3.3)*
- **FR-016**: El sistema DEBE aplicar actualización optimista inmediata en la interfaz ante pulsaciones de incremento o decremento, agrupando las modificaciones mediante sincronización diferida (*debouncing* de 300 ms) hacia el servicio de inventario; si ocurre un fallo de red o el servidor rechaza el cambio por stock insuficiente, el sistema DEBE revertir de forma determinista el contador al último valor confirmado y presentar una notificación visible de error. *(Cubre COM-6.1.3 AC-3.4, INV-9)*
- **FR-017**: Todos los cálculos monetarios (subtotales de ítem y total general del carrito) DEBEN procesarse con precisión decimal estricta a dos decimales, garantizando correspondencia matemática exacta con la suma de los productos de precio unitario por cantidad. *(Cubre INV-2)*
- **FR-018**: El sistema DEBE ofrecer un formulario de edición de datos demográficos dentro del perfil del comensal que incluya los campos: foto de perfil (avatar), edad, género y domicilio. *(Cubre COM-1.4.2)*
- **FR-019**: El sistema DEBE validar que los archivos seleccionados para la foto de perfil pertenezcan a los formatos de imagen permitidos: JPEG (`.jpg`, `.jpeg`), PNG (`.png`) o WEBP (`.webp`). *(Cubre COM-1.4.2 AC-4.3)*
- **FR-020**: Si el comensal selecciona un archivo en formato no soportado para su foto de perfil, el sistema DEBE bloquear la subida y presentar un mensaje que detalle explícitamente los formatos admitidos. *(Cubre COM-1.4.2 AC-4.3)*
- **FR-021**: El sistema DEBE validar que el tamaño de la imagen de perfil no exceda el límite máximo de 5 MB (5.242.880 bytes). *(Cubre COM-1.4.2 AC-4.1, AC-4.2, INV-4)*
- **FR-022**: Si el comensal intenta subir una imagen que sobrepasa los 5 MB, el sistema DEBE rechazar el archivo, impedir la carga y mostrar un mensaje indicando el peso máximo permitido. *(Cubre COM-1.4.2 AC-4.2, INV-4)*
- **FR-023**: Al guardar exitosamente una nueva imagen de perfil válida, el sistema DEBE persistir la imagen en el expediente del comensal y actualizar de inmediato el avatar visible en la barra de navegación superior. *(Cubre COM-1.4.2 AC-4.1)*
- **FR-024**: El sistema DEBE proporcionar una acción explícita para eliminar la foto de perfil existente, solicitando confirmación al usuario mediante un diálogo de verificación antes de proceder. *(Cubre COM-1.4.2 AC-4.5)*
- **FR-025**: Al confirmar la eliminación en el diálogo de verificación, el sistema DEBE actualizar la vista previa del formulario exhibiendo provisionalmente el avatar predeterminado; la persistencia y remoción efectiva del archivo en el servidor y en la barra de navegación superior DEBE consolidarse únicamente al presionar la acción de "Guardar cambios" del formulario demográfico. Si el comensal cancela o cierra sin guardar, se preserva la foto previa. *(Cubre COM-1.4.2 AC-4.5)*
- **FR-026**: El sistema DEBE validar que el valor ingresado en el campo de edad sea un número entero comprendido dentro del rango biológico de 14 a 120 años. *(Cubre COM-1.4.2 AC-4.4, INV-5)*
- **FR-027**: Si el usuario ingresa un valor de edad negativo, menor a 14, superior a 120, con parte decimal o con caracteres alfanuméricos, el sistema DEBE marcar el campo como inválido, impedir el guardado y solicitar una edad coherente. *(Cubre COM-1.4.2 AC-4.4, INV-5)*
- **FR-028**: El sistema DEBE permitir al comensal seleccionar su género entre las opciones: "Femenino", "Masculino", "No binario", "Prefiero no responder", o mantenerlo sin definir como campo opcional. *(Cubre COM-1.4.2)*
- **FR-029**: El sistema DEBE permitir registrar el domicilio del comensal mediante un campo de texto de hasta 255 caracteres como dato opcional de contacto. *(Cubre COM-1.4.2 AC-4.6, INV-8)*
- **FR-030**: El sistema DEBE almacenar el domicilio provisto exclusivamente como dato de contacto del perfil, sin ejecutar geocodificación, estimaciones de ruta, cálculos de costo de envío ni integraciones con servicios logísticos externos. *(Cubre COM-1.4.2 AC-4.6, INV-8)*
- **FR-031**: La edición de datos demográficos DEBE mantener la compatibilidad y no sobreescribir los datos personales existentes (nombre, apellido, teléfono, correo) ni las restricciones dietarias y alérgenos configurados en la especificación 003. *(Consistencia con Spec 003)*
- **FR-032**: Si el comensal cierra o cancela la edición de su perfil antes de presionar guardar, el sistema DEBE descartar las modificaciones transitorias no confirmadas y conservar los valores demográficos previamente persistidos. *(Cubre COM-1.4.2)*

---

### Key Entities *(include if feature involves data)*

- **Carrito de Compras (ShoppingCart / Sesión Local)**: Representa el contenedor transitorio de la orden en construcción por parte del comensal. Está vinculado a la sesión local del comensal y al establecimiento activo. Atributos conceptuales: identificador de sesión, identificador de establecimiento, colección de ítems, cantidad total consolidada, subtotal acumulado y estado de visibilidad del botón flotante.
- **Ítem del Carrito (CartItem)**: Representa cada producto gastronómico incorporado a la orden. Atributos conceptuales: identificador del plato, nombre del plato, precio unitario de venta (PVP vigente), cantidad seleccionada (entero positivo $\ge 1$), subtotal de la línea (precio unitario $\times$ cantidad), stock real disponible en cocina asociado al producto, e indicadores de advertencia dietaria heredados del perfil.
- **Plato / Producto Gastronómico (Dish Reference)**: Referencia al plato publicado del catálogo activo del establecimiento. Provee al carrito su nombre, precio unitario de venta y el stock real disponible en cocina derivado del inventario de insumos.
- **Perfil Demográfico del Comensal (DinerDemographics)**: Extensión de la entidad de perfil del comensal que almacena sus atributos demográficos y de personalización. Atributos conceptuales: identificador del usuario comensal, referencia a la imagen de perfil (avatar personalizado), edad (número entero entre 14 y 120, o sin especificar), género (clasificación cerrada estándar) y domicilio (texto descriptivo de contacto hasta 255 caracteres).
- **Avatar de Usuario (ProfileAvatar)**: Representación visual del comensal en la barra de navegación y en la cabecera de perfil. Puede ser un archivo gráfico personalizado (JPEG/PNG/WEBP $\le 5$ MB) o el recurso gráfico predeterminado del sistema cuando no se ha provisto imagen o tras su eliminación.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El comensal puede añadir la cantidad deseada de un plato al carrito en un máximo de 2 interacciones desde la vista de detalle del plato (ajuste del selector numérico + pulsación de "Agregar al Carrito").
- **SC-002**: El botón flotante del carrito refleja la cantidad total y el subtotal monetario actualizados en $\le 1$ segundo tras cualquier adición, modificación o remoción de producto, sin requerir recargar la página.
- **SC-003**: El 100% de los intentos de añadir o incrementar unidades de un plato por encima del stock real disponible en cocina son bloqueados de forma inmediata por el sistema, mostrando una advertencia visible al usuario.
- **SC-004**: El 100% de los fallos de red o desconexión durante la modificación rápida de cantidades resultan en la reversión determinista del contador a su estado previo confirmado y en la presentación de una notificación de error al comensal.
- **SC-005**: El 100% de los archivos de imagen de perfil que exceden 5 MB (5.242.880 bytes) o cuyo formato no corresponda a JPEG, PNG o WEBP son rechazados con mensajes informativos claros sin alterar el avatar previo.
- **SC-006**: El 100% de los valores de edad incoherentes (menores a 14, mayores a 120, negativos, decimales o alfanuméricos) son interceptados e invalidados, impidiendo guardar datos inconsistentes en el perfil.
- **SC-007**: El subtotal monetario exhibido en el botón flotante y en el modal del carrito coincide con exactitud matemática estricta con la suma de $(\text{precio\_unitario} \times \text{cantidad})$ de todos los productos presentes, sin discrepancias por redondeo de punto flotante.
- **SC-008**: Al guardar exitosamente el formulario tras confirmar la eliminación de la foto de perfil, el avatar predeterminado se restablece tanto en la barra de navegación superior como en la vista de perfil en $\le 1$ segundo.
- **SC-009**: Al remover el último producto del carrito, el botón flotante desaparece de la vista en $\le 500$ milisegundos.

---

## Assumptions

- **A-001 (Sesión y contexto de usuario)**: El comensal se encuentra previamente registrado y autenticado en la plataforma con rol `DINER` (COMENSAL), conforme a los mecanismos provistos por las especificaciones 001 y 002.
- **A-002 (Catálogo y fijación de precios)**: Los platos visualizados en el catálogo cuentan con un precio de venta al público (PVP) debidamente aprobado y publicado, según la especificación 007, y stock gobernado por las recetas e ingredientes de las especificaciones 004, 005 y 006.
- **A-003 (Persistencia de la sesión del carrito)**: La sesión del carrito se gestiona de manera local para el comensal mientras navega en el establecimiento activo; si el comensal cambia de establecimiento en el catálogo, aplica la política establecida en la especificación 008 (aviso de confirmación previo al vaciado de la orden).
- **A-004 (Rango biológico de edad)**: Se define como default razonable para la edad de comensales titulares un rango cerrado de 14 a 120 años inclusive, considerando 14 años la edad mínima típica de uso autónomo de aplicaciones de consumo y 120 años el límite biológico estándar en sistemas demográficos.
- **A-005 (Formato del domicilio)**: El domicilio se ingresa como una única cadena de texto libre (calle, número, piso/depto, localidad) con un límite máximo de 255 caracteres, suficiente para registrar una dirección de contacto sin requerir esquemas complejos de normalización postal en esta etapa.
- **A-006 (Formatos de imagen soportados)**: Los formatos admitidos para la foto de perfil comprenden exclusivamente los estándares web universales: JPEG/JPG, PNG y WEBP.
- **A-007 (Límite superior de tamaño de imagen)**: El límite de 5 MB es estrictamente inclusivo respecto a 5.242.880 bytes ($5 \times 1024 \times 1024$ bytes). Archivos de hasta 5.242.880 bytes son procesados; archivos de 5.242.881 bytes o más son rechazados.
- **A-008 (Avatar predeterminado)**: La plataforma provee un avatar genérico institucional ligero para todos los comensales que no hayan subido una foto personalizada o que hayan eliminado su fotografía previa.

---

## Out of Scope

- **Pasarela de pagos y procesamiento de cobro**: Autorización de pagos, integración con tarjetas de crédito/débito, pasarelas externas o confirmación de transferencias (diferido formalmente a Sprint 4).
- **Checkout formal de la orden**: Generación de códigos de retiro de pedido, despacho a cocina (KDS) y emisión de comprobantes transaccionales (diferido a Sprint 4).
- **Cálculo de rutas de envío o logística de delivery**: Cálculo de distancias kilométricas, geocodificación de direcciones, asignación de repartidores o integración con servicios externos de logística (el domicilio es estrictamente dato de contacto del perfil).
- **Autenticación, registro y recuperación de contraseñas**: Mecanismos de login, signup o reseteo de claves (cubiertos por las especificaciones 001 y 002).
- **Gestión de datos de contacto base y preferencias dietarias**: Edición de nombre, apellido, teléfono, email y taxonomía de restricciones alimentarias (cubiertos por la especificación 003).
- **Búsqueda, filtros y navegación de catálogo**: Búsqueda por texto, desplazamiento por categorías gastronómicas y descubrimiento multi-tenant (cubiertos por la especificación 008).
- **Historial de pedidos y compras pasadas**: Consulta de órdenes completadas o pedidos históricos del comensal.
- **Cupones, promociones y descuentos de marketing**: Aplicación de códigos promocionales, programas de fidelidad o descuentos sobre el subtotal.
- **Sincronización multi-dispositivo en tiempo real del carrito**: Compartir o sincronizar en simultáneo el carrito entre múltiples navegadores o dispositivos distintos de un mismo usuario.
- **Soporte multi-moneda**: Gestión de divisas extranjeras o conversión cambiaria; la plataforma opera en la moneda de curso legal configurada para el establecimiento.

