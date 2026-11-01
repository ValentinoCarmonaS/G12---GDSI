# Feature Specification: Umbral de Stock Crítico en Inventario y Pausa Manual de Platos por Chef

**Feature Branch**: `013-critical-stock-chef-pause`

**Created**: 01/11/2026 | **Approved**: 01/11/2026

**Status**: Approved (Audited via grill-me & speckit-checklist)

**Input**: User description: "Sistema web de pedidos de comida. Iteración 013 extiende el control operativo del inventario (admin) y la visibilidad del catálogo (chef). Tres historias de usuario aprobadas y cerradas: ADM-4.1.3 (Definir el umbral de stock Crítico), ADM-4.1.4 (Visualizar alertas de reabastecimiento en estado de umbral Crítico) y CHF-2.2.5 (Pausa manual de platos)."

---

## Clarifications

### Session 2026-11-01 (Alineación Operativa de Inventario, Control de Cocina e Invariantes de Disponibilidad)

- Q: ¿Qué valor o condición matemática define que un insumo cruce la barrera de su stock crítico según el criterio AC-2.1? → A: Condición estrictamente menor ($\text{cantidad\_disponible} < \text{umbral\_critico}$). Si la cantidad disponible es exactamente igual al umbral configurado ($\text{cantidad\_disponible} = \text{umbral\_critico}$), el ingrediente se encuentra en el límite de seguridad y no se considera en estado crítico ni dispara alerta visual.
- Q: ¿Cuál es el alcance operativo y destino de las alertas de reabastecimiento según ADM-4.1.4? → A: La alerta es exclusivamente visual dentro del panel administrativo (Back-Office) al ingresar al módulo de inventario. No emite correos electrónicos a proveedores, mensajes SMS, notificaciones push a dispositivos móviles, notificaciones al chef ni genera órdenes de compra automáticas.
- Q: ¿Cómo se configura el umbral de stock crítico en el módulo de inventario según ADM-4.1.3? → A: El valor se configura de forma individual por insumo durante la creación o edición del ingrediente. No existe un umbral global, por categoría o general para todo el inventario, ya que la tolerancia de urgencia varía entre un insumo y otro.
- Q: ¿Cómo se articulan y distinguen las tres fuentes de indisponibilidad de un plato en el catálogo? → A: Coexisten tres causas mutuamente distinguibles con actores, disparadores y vías de recuperación independientes:
  1. **Falta de stock (`sin_stock`)**: Derivada de manera automática del inventario cuando los insumos de su receta se agotan; se recupera automáticamente mediante el reabastecimiento de los ingredientes.
  2. **Pausa del chef (`pausa_chef`)**: Control manual, temporal y arbitrario accionado por el chef (por ejemplo, por plancha quemada o demora excesiva en cocina); se recupera exclusivamente cuando el chef desactiva la pausa.
  3. **Desactivación del administrador (`desactivacion_admin`)**: Gestión manual y permanente del administrador sobre la oferta comercial del catálogo; se recupera exclusivamente cuando el administrador vuelve a activar el plato.  
  Ningún actor puede operar el mecanismo de otra causa ni sustituirlo.
- Q: ¿Dónde y cómo debe ubicarse el control de alternancia (toggle) en la interfaz del chef? → A: La especificación funcional no prescribe la posición visual, el color, la pantalla ni el layout del control de alternancia. La especificación define estrictamente el comportamiento observable: al accionar el control a estado pausado, el plato desaparece de la vista del comensal; al accionarlo a activo, se restaura la visibilidad del plato (sujeto a las demás condiciones de disponibilidad).
- Q: ¿Cómo interactúa el umbral de stock crítico con las ventas de platos y la visualización del comensal? → A: El umbral de stock crítico es una referencia operativa interna de reposición para el administrador. No modifica la cantidad disponible real, no bloquea ventas, no altera el precio y no afecta la visibilidad ni la posibilidad de compra de los platos para el comensal mientras la cantidad disponible del insumo sea mayor a cero.

---

## Invariantes de Negocio

Los siguientes invariantes definen las reglas funcionales y operativas inviolables de la presente iteración:

- **INV-1: Individualidad estricta del umbral de stock crítico**  
  $$\forall i \in \text{Ingredientes}, \quad \text{umbral\_critico}(i) \text{ se define de manera individual por insumo}$$  
  No existe un umbral de stock crítico global, promedio o por categoría general para todo el inventario. Cada insumo posee su propia tolerancia operativa de urgencia fijada por el administrador.  
  *Ejemplo observable*: El administrador puede definir un umbral de 5 unidades para "Carne de hamburguesa" y un umbral de 20 unidades para "Pan de papa" dentro del mismo inventario del establecimiento.

- **INV-2: Dominio numérico del umbral de stock crítico**  
  $$\text{umbral\_critico}(i) \in \mathbb{N}_0 \quad (\text{entero mayor o igual a 0})$$  
  El campo "Stock mínimo/crítico" admite únicamente valores numéricos enteros positivos o cero. Valores negativos, con cifras decimales o cadenas no numéricas son rechazados al intentar guardar el registro del ingrediente.

- **INV-3: Definición del estado de ingrediente en stock crítico**  
  $$\text{ingrediente\_en\_estado\_critico}(i) \iff \text{cantidad\_disponible}(i) < \text{umbral\_critico}(i)$$  
  Un insumo se encuentra en estado crítico si y solo si su cantidad disponible real en inventario es estrictamente menor a su umbral crítico configurado.  
  *Ejemplo observable*: Si el umbral es 10 y la cantidad disponible es 10, no está en estado crítico. Si la cantidad baja a 9, pasa de inmediato a estado crítico.

- **INV-4: Despliegue de alerta visual en el módulo de inventario**  
  $$\text{alerta\_visual\_visible}(i) \text{ al consultar inventario} \iff \text{ingrediente\_en\_estado\_critico}(i) = \text{true}$$  
  Al ingresar al módulo de inventario en el panel administrativo, el sistema resalta visualmente mediante un indicador destacado (por ejemplo, un ícono o etiqueta informativa de falta de stock) a todos los ingredientes cuyo estado crítico sea verdadero. Los ingredientes cuya cantidad disponible sea mayor o igual al umbral crítico no presentan dicho resaltado.

- **INV-5: Exclusividad visual de la alerta dentro del panel administrativo**  
  $$\text{alerta\_reabastecimiento}(i) \implies \text{indicador\_visual\_en\_panel\_administrativo} \land \neg\,\text{notificaciones\_externas}$$  
  La alerta de reabastecimiento es estrictamente visual y confinada a la pantalla de inventario del panel administrativo (Back-Office). El sistema no envía correos a proveedores ni personal, no emite SMS ni mensajes push, y no automatiza órdenes de compra.

- **INV-6: No interferencia del umbral crítico con la venta comercial**  
  $$\text{umbral\_critico\_configurado}(i) \implies \neg\,\text{bloqueo\_venta} \land \neg\,\text{modificación\_stock} \land \neg\,\text{alteración\_precio} \land \neg\,\text{ocultamiento\_catálogo}$$  
  El umbral crítico funciona como parámetro de referencia para reposición y no como mecanismo de bloqueo de órdenes. Un insumo en estado crítico cuya cantidad disponible sea estrictamente mayor a cero ($> 0$) continúa consumiéndose normalmente en los pedidos de comensales.

- **INV-7: Tratamiento de insumo sin umbral configurado**  
  $$\neg\,\text{tiene\_umbral}(i) \implies \text{ingrediente\_en\_estado\_critico}(i) = \text{false} \land \neg\,\text{alerta\_visual\_visible}(i)$$  
  Un ingrediente que no posee un umbral de stock crítico configurado por el administrador permanece en condición de referencia no definida y no dispara alertas visuales de reabastecimiento bajo ninguna cantidad disponible.

- **INV-8: Efecto observable de la pausa manual del chef sobre el comensal**  
  $$\text{pausa\_chef}(p) = \text{true} \iff \text{plato\_oculto\_en\_catálogo\_comensal}(p)$$  
  Cuando el chef acciona el control de alternancia (toggle) a estado apagado/pausado, el plato desaparece instantáneamente de la vista del comensal en el catálogo gastronómico, sin requerir recarga manual del comensal.

- **INV-9: Reversibilidad de la pausa del chef**  
  $$\text{pausa\_chef}(p) = \text{false} \implies \text{visibilidad\_comensal}(p) \equiv \text{estado\_previo\_según\_otras\_causas}(p)$$  
  Cuando el chef enciende o reactiva el control de alternancia de un plato pausado, el plato recupera su visibilidad para el comensal, siempre y cuando no existan otras causas de indisponibilidad vigentes (desactivación administrativa o falta de stock).

- **INV-10: Distinción formal de las tres fuentes de indisponibilidad de un plato (Invariante Central)**  
  Un plato publicado puede quedar no disponible o no comprable para el comensal por tres causas conceptual y operativamente independientes:

  | Dimensión | 1. Falta de Stock (`sin_stock`) | 2. Pausa Manual del Chef (`pausa_chef`) | 3. Desactivación Administrativa (`desactivacion_admin`) |
  |---|---|---|---|
  | **Naturaleza** | Automática, reactiva al inventario de ingredientes. | Manual, operativa, temporal y arbitraria. | Manual, comercial, permanente o de gestión de catálogo. |
  | **Actor con Autoridad** | Sistema (derivado del stock de insumos de la receta). | Chef. | Administrador. |
  | **Motivo Operativo** | Insumos de la receta con cantidad insuficiente o en cero. | Contingencia en cocina (plancha quemada, demora excesiva, saturación). | Decisión comercial, retiro de carta o estacionalidad. |
  | **Vía de Recuperación** | Reabastecimiento de stock de los ingredientes faltantes. | Despausa voluntaria mediante el control de alternancia del chef. | Reactivación manual desde el panel administrativo. |
  | **Efecto en el Comensal** | Plato visible pero atenuado con distintivo "Agotado" y compra deshabilitada (spec 008/011). | Plato totalmente oculto de la vista del catálogo. | Plato oculto o no publicado en el catálogo del comensal. |
  | **Intercambiabilidad** | Ninguna. No puede ser forzado por el chef ni por el admin como stock. | Ninguna. El administrador no opera el toggle del chef; el sistema no lo pausa por stock. | Ninguna. El chef no posee permisos para desactivar definitivamente platos del catálogo. |

- **INV-11: Desacoplamiento operativo de la pausa del chef**  
  $$\text{pausa\_chef}(p) \implies \neg\,\text{modificación\_stock\_receta} \land \neg\,\text{modificación\_precio} \land \neg\,\text{alerta\_reabastecimiento}$$  
  Accionar la pausa manual del chef no altera la cantidad de stock de los ingredientes asociados a la receta del plato, no modifica su precio comercial ni dispara alertas de reposición en el inventario.

- **INV-12: Coexistencia e independencia de causas de indisponibilidad**  
  $$(\text{pausa\_chef}(p) = \text{false}) \land (\text{sin\_stock}(p) = \text{true}) \implies \text{plato\_despausado\_por\_chef} \land \text{no\_comprable\_por\_stock}$$  
  Si el chef despausa un plato cuyos ingredientes se encuentran agotados, la pausa del chef se remueve exitosamente, pero el plato continúa no comprable debido a la falta de stock. Ambas condiciones son observables de manera independiente en los paneles del personal.

- **INV-13: Reversibilidad sin bloqueo mutuo**  
  Cada una de las tres causas de indisponibilidad puede ser modificada por su actor o evento correspondiente sin que la presencia de otra causa bloquee la acción. La desactivación administrativa no impide al chef alternar el estado de pausa; la pausa del chef no impide registrar reabastecimientos de stock.

- **INV-14: Recálculo reactivo ante actualización de umbral**  
  $$\text{actualización}(\text{umbral\_critico}(i)) \implies \text{recálculo\_inmediato\_de\_criticidad}(i) \text{ en siguiente consulta}$$  
  Cualquier modificación en el valor del umbral de stock crítico recalcula inmediatamente la condición de criticidad del insumo sin requerir procesos batch o acciones complementarias por parte del administrador.

- **INV-15: Propagación observable de la visibilidad del comensal**  
  $$\text{cambio}(\text{pausa\_chef}(p)) \implies \text{reflejado\_en\_comensal\_en\_lapso} \le 3 \text{ segundos}$$  
  El cambio de visibilidad producido por la pausa o despausa manual del chef se refleja en el catálogo del comensal sin requerir recarga manual de página, dentro del límite de propagación observable establecido para el catálogo gastronómico ($\le 3$ segundos).

- **INV-16: Autorización estricta por rol operativo**  
  $$\text{configurar\_umbral} \subset \text{Permisos}(\text{ADMIN}) \quad \land \quad \text{operar\_pausa\_chef} \subset \text{Permisos}(\text{CHEF})$$  
  Únicamente los usuarios autenticados con rol Administrador tienen autorización para fijar o modificar el umbral de stock crítico. Únicamente los usuarios autenticados con rol Chef tienen autorización para operar el control de alternancia de pausa manual de platos.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Definir el umbral de stock crítico individual por ingrediente (Priority: P1)

**ADM-4.1.3**: Como administrador, quiero definir un nivel de "stock mínimo" individual para cada ingrediente, para que el sistema tenga un parámetro de cuándo debe advertirme que es necesario reponer mercadería.

**Why this priority**: P1 fundacional (AC-1.1 y Nota de Scope). Sin la capacidad de parametrizar individualmente el umbral por insumo, el sistema carece del dato de referencia indispensable para evaluar la criticidad y disparar alertas de reabastecimiento en la historia ADM-4.1.4.

**Independent Test**: Puede probarse de forma autónoma ingresando al módulo de inventario con rol Administrador, abriendo el formulario de creación o edición de un ingrediente, introduciendo un valor numérico entero en el campo "Stock mínimo/crítico", confirmando el guardado y verificando que el umbral queda registrado como atributo del insumo.

**Acceptance Scenarios**:

1. **Dado** que un administrador crea un nuevo ingrediente en el inventario, **Cuando** ingresa un valor numérico entero mayor o igual a 0 en el campo "Stock mínimo/crítico" y guarda el ingrediente, **Entonces** el sistema guarda este límite como referencia individual asociada al insumo para disparar futuras alertas visuales (AC-1.1, NS-1).
2. **Dado** que un administrador edita un ingrediente existente en el inventario, **Cuando** modifica el valor del campo "Stock mínimo/crítico" ingresando un nuevo número entero válido y confirma los cambios, **Entonces** el sistema actualiza el umbral guardado para ese ingrediente específico sin alterar los umbrales de los demás insumos (AC-1.1, NS-1).
3. **Dado** que un administrador intenta registrar o editar un ingrediente, **Cuando** ingresa en el campo de stock crítico un valor negativo, un número con decimales o caracteres alfabéticos, **Entonces** el sistema rechaza el guardado y presenta un mensaje de validación informando que el umbral debe ser un número entero mayor o igual a cero.
4. **Dado** que un usuario con rol Chef intenta acceder al formulario de edición o fijar el umbral de stock crítico de un ingrediente, **Cuando** intenta realizar la operación, **Entonces** el sistema deniega el acceso y bloquea la modificación por falta de autorización.

---

### User Story 2 - Visualizar alertas de reabastecimiento en estado de umbral crítico (Priority: P1)

**ADM-4.1.4**: Como administrador, quiero visualizar una alerta o indicador en el sistema cuando un ingrediente caiga por debajo de su umbral mínimo, para identificar rápidamente qué insumos debo comprar a los proveedores sin tener que revisar la lista completa manualmente.

**Why this priority**: P1 de entrega de valor operativo (AC-2.1 y Nota de Scope). Constituye el objetivo directo del feature de inventario; sin una alerta visual prominente en el panel, el administrador debe auditar fila por fila cada insumo para detectar faltantes.

**Independent Test**: Puede probarse de forma autónoma ajustando la cantidad disponible de un insumo por debajo de su umbral crítico (o fijando un umbral mayor al stock actual), accediendo al módulo de inventario del panel administrativo con rol Administrador y confirmando que el ingrediente aparece resaltado con un indicador visual destacado de falta de stock, sin que se generen comunicaciones externas.

**Acceptance Scenarios**:

1. **Dado** que la cantidad disponible de un ingrediente cruza la barrera de su stock crítico quedando estrictamente menor a su umbral configurado por efecto de una venta, **Cuando** el administrador ingresa al módulo de inventario, **Entonces** el sistema resalta visualmente ese ingrediente con un indicador destacado de falta de stock (AC-2.1).
2. **Dado** que la cantidad disponible de un ingrediente se reduce por debajo de su umbral crítico debido a un ajuste manual de inventario, **Cuando** el administrador consulta el módulo de inventario, **Entonces** el sistema presenta el ingrediente con su respectivo resaltado visual de stock crítico (AC-2.1).
3. **Dado** que un ingrediente se encuentra resaltado en estado crítico, **Cuando** se registra una reposición o ajuste positivo de mercadería que eleva la cantidad disponible a un valor mayor o igual que su umbral crítico, **Entonces** el sistema remueve el indicador visual de alerta en la siguiente visualización del inventario.
4. **Dado** que un ingrediente entra en estado crítico, **Cuando** el sistema procesa la condición, **Entonces** la alerta permanece confinada exclusivamente a la visualización del panel administrativo, sin enviar correos electrónicos a proveedores, mensajes SMS ni órdenes de compra automáticas (NS-2).
5. **Dado** que existen múltiples ingredientes en el inventario con cantidades inferiores a sus respectivos umbrales críticos, **Cuando** el administrador accede a la lista, **Entonces** todos los insumos afectados presentan su indicador de alerta visual simultáneamente.

---

### User Story 3 - Pausa manual de platos por el chef durante el servicio (Priority: P1)

**CHF-2.2.5**: Como chef, quiero poder pausar o inhabilitar manualmente un plato del catálogo con un botón tipo toggle para ocultarlo del menú temporalmente de forma arbitraria (ej.: se quemó la plancha o hay demora excesiva), sin depender del stock.

**Why this priority**: P1 de protección operativa del servicio (AC-3.1 y Advertencia Operativa). Ocurre durante la operación viva del restaurante; si el chef no puede ocultar un plato de inmediato ante una contingencia de cocina, los comensales continuarán ordenando preparaciones que la cocina no está en condiciones de elaborar.

**Independent Test**: Puede probarse de forma autónoma autenticándose con rol Chef, seleccionando un plato activo en el catálogo del chef, accionando el control de alternancia (toggle) a estado pausado y verificando que dicho plato deja de mostrarse en el catálogo del comensal en la siguiente interacción observable sin afectar el stock de sus ingredientes.

**Acceptance Scenarios**:

1. **Dado** que un plato publicado se encuentra visible en el menú del comensal, **Cuando** el chef acciona el control de alternancia (toggle) de visibilidad a estado apagado, **Entonces** el plato desaparece instantáneamente de la vista del comensal en el catálogo gastronómico (AC-3.1).
2. **Dado** que un plato se encuentra pausado manualmente por el chef, **Cuando** el chef vuelve a accionar el control de alternancia encendiéndolo, **Entonces** el plato reaparece de inmediato en la vista del comensal (siempre que cuente con stock suficiente y no esté desactivado por el administrador).
3. **Dado** que el chef pausa manualmente un plato, **Cuando** se consulta el inventario de ingredientes, **Entonces** el sistema mantiene intactas las cantidades de stock, las recetas y los precios de los productos, confirmando que la pausa es independiente del stock.
4. **Dado** que un plato carece de stock de sus ingredientes en cocina y además el chef acciona la pausa manual, **Cuando** el chef desactiva posteriormente la pausa manual, **Entonces** el plato permanece no disponible para el comensal debido a la falta de stock remanente en inventario.
5. **Dado** que un usuario con rol Administrador interactúa con el sistema, **Cuando** accede a sus funciones de gestión, **Entonces** no opera el control de pausa manual del chef, preservando su facultad independiente de activación/desactivación administrativa permanente.

---

### Edge Cases

- **EC-001**: Umbral crítico configurado con valor 0 → El insumo solo entra en estado crítico si su cantidad disponible es estrictamente menor a 0. Dado que el stock físico no puede ser negativo bajo condiciones normales, el ingrediente con umbral 0 no dispara alerta de stock crítico por defecto (salvo eventual desajuste de inventario registrado negativamente).
- **EC-002**: Ingrediente sin umbral crítico configurado (valor ausente/nulo) → El ingrediente no dispara alertas visuales de reabastecimiento bajo ninguna cantidad disponible; se considera sin umbral de referencia hasta que el administrador lo defina explícitamente.
- **EC-003**: Umbral crítico fijado con un valor mayor a la cantidad disponible existente al momento de guardar → El ingrediente entra en estado crítico inmediatamente tras confirmar el guardado del umbral y se resalta en la lista de inventario.
- **EC-004**: Cantidad disponible igual a 0 con umbral configurado mayor a 0 → El insumo satisface la condición ($0 < \text{umbral}$) y se visualiza en estado crítico activo.
- **EC-005**: Cruce del umbral crítico originado por una venta de platos → Al confirmarse la venta y descontarse el insumo de la receta, si la cantidad disponible cruza y queda por debajo del umbral, el ingrediente transiciona a estado crítico para la próxima consulta del módulo de inventario.
- **EC-006**: Cruce del umbral originado por un ajuste manual de inventario (mermas, desperdicio o conteo físico) → La reducción manual del stock por debajo del umbral activa de inmediato el estado crítico del insumo.
- **EC-007**: Reabastecimiento de insumos que supera el umbral crítico → Al registrar un ingreso de mercadería que sitúa la cantidad disponible en un valor mayor o igual al umbral, el ingrediente abandona el estado crítico y el indicador visual de alerta se retira en la siguiente consulta.
- **EC-008**: Ingrediente en estado crítico compartido por múltiples platos en sus recetas → La alerta de reabastecimiento se visualiza estrictamente sobre el ingrediente en el módulo de inventario. Los platos que utilizan dicho insumo no reciben alertas de reabastecimiento en sus fichas técnicas, y solo se ven afectados en el catálogo del comensal si la cantidad disponible llega a cero.
- **EC-009**: Ingrediente sin recetas asociadas que cae por debajo de su umbral → El sistema permite configurar su umbral y despliega la alerta visual en el inventario al cruzar el límite, independientemente de que ningún plato dependa actualmente de él.
- **EC-010**: Modificación de umbral de stock crítico mientras hay comensales explorando el catálogo → La modificación es un evento interno del panel administrativo; no altera la visibilidad de los platos ni interrumpe la navegación de los comensales.
- **EC-011**: El chef pausa un plato que ya se encontraba agotado por falta de stock → Ambas causas de indisponibilidad coexisten en el sistema. Al despausar el plato, este continúa no comprable debido a la falta de stock.
- **EC-012**: El chef pausa un plato que previamente fue desactivado por el administrador → La desactivación administrativa prevalece a nivel de publicación comercial. Si el chef despausa el plato, este continúa no visible en el catálogo del comensal hasta que el administrador lo reactive.
- **EC-013**: El administrador desactiva un plato que se encuentra pausado por el chef → La desactivación administrativa tiene prevalencia comercial; si el administrador posteriormente reactiva el plato, este no se vuelve visible para el comensal de forma automática si la pausa manual del chef sigue activa. Cada actor gestiona y recupera su propio estado.
- **EC-014**: El chef despausa un plato cuyos ingredientes están agotados → El plato queda formalmente despausado por el chef, pero el catálogo del comensal lo presenta bajo las reglas de falta de stock (deshabilitado o atenuado según las reglas de catálogo preexistentes de la especificación 008/011).
- **EC-015**: Alternancia rápida y repetida del control de pausa por el chef (toggle sucesivo) → El sistema procesa los cambios de forma secuencial y consistente; el estado final observable del plato corresponde al último valor confirmado por el chef, sin dejar estados intermedios inconsistentes.
- **EC-016**: El chef requiere pausar la totalidad de los platos de una categoría gastronómica → Cada plato se pausa y despausa de forma individual mediante su respectivo control de alternancia; la presente iteración no contempla acciones masivas por categoría.
- **EC-017**: Comensal con el modal de detalle de plato abierto en el momento exacto en que el chef acciona la pausa manual → El panel de detalle actualiza la disponibilidad del plato reflejando su condición de no disponible en tiempo real ($\le 3$ segundos) e inhabilitando las acciones de compra, de manera coherente con el tratamiento de indisponibilidad de la especificación 011; al cerrar el panel, el plato desaparece del catálogo.
- **EC-018**: Un usuario autenticado con rol Chef intenta acceder al endpoint o formulario de configuración de umbrales críticos → La acción es rechazada por el control de autorización con denegación formal de acceso; solo el rol Administrador está facultado para definir umbrales.
- **EC-019**: Un usuario autenticado con rol Administrador intenta accionar el control de pausa manual del chef → La acción no está disponible en la interfaz del administrador ni forma parte de sus mecanismos operativos; el administrador mantiene su propia facultad de desactivación definitiva.
- **EC-020**: Múltiples ingredientes entran en estado crítico de forma simultánea → El módulo de inventario resalta visualmente todos los ingredientes que cumplan la condición, sin que la presencia de múltiples alertas degrade la visualización.
- **EC-021**: Múltiples platos pausados manualmente por el chef en el mismo establecimiento → Cada plato pausado se oculta individualmente del menú del comensal.
- **EC-022**: Umbral de stock crítico configurado en un valor arbitrariamente alto en relación a la capacidad física de almacenamiento → El sistema acepta el valor si cumple con ser un número entero mayor o igual a 0, sin imponer topes máximos artificiales a la decisión del administrador.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al administrador asignar y modificar un valor numérico entero en el campo "Stock mínimo/crítico" de manera individual para cada ingrediente en el módulo de inventario (cubre ADM-4.1.3, AC-1.1, NS-1, INV-1, INV-2).
- **FR-002**: El sistema DEBE validar que el valor ingresado en el umbral de stock crítico sea un número entero mayor o igual a cero ($\ge 0$), rechazando el guardado y emitiendo un mensaje de error ante valores negativos, decimales o no numéricos (cubre AC-1.1, INV-2, EC-001).
- **FR-003**: El sistema DEBE guardar el umbral de stock crítico como un parámetro de referencia individual asociado al registro del ingrediente dentro del establecimiento gastronómico (cubre AC-1.1, NS-1, INV-1).
- **FR-004**: El sistema DEBE evaluar dinámicamente si un ingrediente se encuentra en estado crítico verificando si su cantidad disponible real es estrictamente menor a su umbral de stock crítico configurado ($\text{cantidad\_disponible} < \text{umbral\_critico}$) (cubre ADM-4.1.4, AC-2.1, INV-3).
- **FR-005**: Al acceder el administrador al módulo de inventario, el sistema DEBE resaltar visualmente mediante un indicador destacado (por ejemplo, un ícono o etiqueta de falta de stock) a todo ingrediente que se encuentre en estado crítico (cubre AC-2.1, INV-4).
- **FR-006**: Si un ingrediente posee una cantidad disponible mayor o igual a su umbral crítico, el sistema DEBE mostrar dicho ingrediente sin el indicador visual de falta de stock (cubre AC-2.1, INV-4).
- **FR-007**: Si un ingrediente no posee un umbral de stock crítico configurado, el sistema DEBE tratarlo como insumo sin umbral y NO DEBE resaltar alerta visual de reabastecimiento (cubre INV-7, EC-002).
- **FR-008**: La alerta de reabastecimiento en estado de umbral crítico DEBE ser estrictamente visual dentro del módulo de inventario del panel administrativo (Back-Office) y NO DEBE enviar correos electrónicos, mensajes SMS, notificaciones push ni generar órdenes de compra automatizadas (cubre AC-2.1, NS-2, INV-5).
- **FR-009**: La condición de umbral crítico NO DEBE bloquear la venta comercial, NO DEBE alterar la cantidad de stock real disponible, NO DEBE modificar precios y NO DEBE alterar la visibilidad del plato en el catálogo del comensal mientras la cantidad disponible del insumo sea mayor a cero (cubre INV-6, EC-010).
- **FR-010**: El sistema DEBE recalcular el estado de criticidad de un ingrediente de forma automática ante cualquier variación de su cantidad disponible (originada por ventas de platos o ajustes manuales de stock) y ante cualquier modificación de su umbral crítico (cubre AC-2.1, INV-14, EC-005, EC-006, EC-007).
- **FR-011**: El sistema DEBE proveer al personal con rol Chef un control de alternancia (toggle) individual para cada plato activo del catálogo culinario, permitiendo conmutar su estado entre activo y pausado temporalmente (cubre CHF-2.2.5, AC-3.1).
- **FR-012**: Cuando el chef acciona el control de alternancia a estado pausado, el sistema DEBE ocultar el plato inmediatamente de la vista del comensal en el catálogo gastronómico sin requerir recarga manual de página (cubre AC-3.1, INV-8, INV-15).
- **FR-013**: Cuando el chef acciona el control de alternancia a estado activo, el sistema DEBE restaurar la visibilidad del plato para el comensal en el catálogo gastronómico, siempre que no existan otras restricciones activas de falta de stock o desactivación administrativa (cubre AC-3.1, INV-9, INV-12).
- **FR-014**: La pausa manual accionada por el chef DEBE ser operativa, temporal y arbitraria, y NO DEBE modificar las cantidades de stock en inventario, NO DEBE modificar recetas, NO DEBE modificar precios y NO DEBE disparar alertas de reabastecimiento (cubre CHF-2.2.5, INV-11).
- **FR-015**: El sistema DEBE mantener claramente diferenciadas las tres fuentes de indisponibilidad de un plato: falta de stock (`sin_stock`), pausa manual del chef (`pausa_chef`) y desactivación del administrador (`desactivacion_admin`), asegurando que ningún actor pueda operar ni suplantar el mecanismo propio de otro rol (cubre INV-10, INV-13, INV-16).
- **FR-016**: Si un comensal se encuentra visualizando el panel de detalle de un plato en el instante en que el chef acciona la pausa manual, el sistema DEBE actualizar en tiempo real ($\le 3$ segundos) la vista marcando el plato como no disponible e inhabilitando las acciones de compra, y al cerrar el panel, el plato DEBE quedar excluido de la grilla del catálogo (cubre EC-017, INV-15).
- **FR-017**: El sistema DEBE restringir la configuración del umbral de stock crítico exclusivamente a usuarios autenticados con rol Administrador, rechazando cualquier intento de configuración por parte de otros roles (cubre INV-16, EC-018).
- **FR-018**: El sistema DEBE restringir la operación del control de alternancia de pausa manual exclusivamente a usuarios autenticados con rol Chef dentro de su propio establecimiento (cubre INV-16, EC-019).

---

### Key Entities

- **Ingrediente**: Insumo o materia prima culinaria administrable en el módulo de inventario del establecimiento. Posee nombre, categoría, unidad de medida, cantidad de stock disponible real y el atributo configurable de umbral de stock crítico.
- **Umbral de Stock Crítico**: Parámetro numérico entero ($\ge 0$) definido individualmente por el administrador para un ingrediente específico. Establece el límite por debajo del cual el insumo se considera en necesidad de reposición urgente.
- **Estado de Criticidad**: Condición lógica derivada del insumo ($\text{cantidad\_disponible} < \text{umbral\_critico}$). Cuando es verdadera, provoca el resaltado visual de alerta de reabastecimiento en la lista de inventario del panel administrativo.
- **Plato**: Producto culinario del catálogo del restaurante vinculado a una receta de ingredientes y a un precio comercial aprobado. Su visibilidad en el catálogo del comensal está supeditada al cumplimiento simultáneo de las tres condiciones de disponibilidad.
- **Estado de Pausa del Chef (`pausa_chef`)**: Estado operativo binario gobernado exclusivamente por el chef mediante un control de alternancia (toggle). En estado verdadero, oculta el plato del menú del comensal de manera inmediata y arbitraria sin afectar el stock.
- **Estado de Sin Stock (`sin_stock`)**: Condición de indisponibilidad derivada automáticamente del inventario cuando uno o más ingredientes de la receta del plato tienen cantidad disponible insuficiente o igual a cero. En el catálogo del comensal mantiene el plato visible pero atenuado con distintivo de agotado y compra deshabilitada.
- **Estado de Desactivación Administrativa (`desactivacion_admin`)**: Condición de inactividad comercial permanente o semipermanente decidida por el administrador sobre el plato (a través de la despublicación o desactivación del catálogo). Oculta el plato del comensal a nivel de gestión de catálogo.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los umbrales de stock crítico guardados constituyen atributos individuales asociados a cada ingrediente específico, sin existencia de parámetros globales o únicos para todo el inventario.
- **SC-002**: El 100% de los intentos de ingresar un valor negativo, decimal o no numérico en el campo de stock crítico son rechazados antes del guardado, mostrando un mensaje de validación explícito.
- **SC-003**: El 100% de los ingredientes cuya cantidad disponible sea estrictamente menor a su umbral crítico configurado aparecen resaltados con un indicador visual destacado al acceder al módulo de inventario en el panel administrativo.
- **SC-004**: El 100% de los ingredientes cuya cantidad disponible sea mayor o igual a su umbral crítico configurado se muestran sin indicadores visuales de alerta de falta de stock.
- **SC-005**: El 0% de las alertas de reabastecimiento generadas por stock crítico emiten correos electrónicos, mensajes SMS, notificaciones push a comensales/personal u órdenes de compra automatizadas.
- **SC-006**: El 100% de los platos con pausa manual del chef activa (`pausa_chef = true`) quedan ocultos de la vista del comensal en el catálogo gastronómico dentro de un lapso no superior a 3 segundos (o en la siguiente interacción observable).
- **SC-007**: El 100% de los platos cuyo control de pausa manual sea desactivado por el chef (`pausa_chef = false`) y que no presenten otras causas de indisponibilidad activas reaparecen en el catálogo del comensal dentro de un lapso no superior a 3 segundos.
- **SC-008**: El 100% de los platos pausados manualmente por el chef continúan accesibles y visibles en el panel del chef, distinguiéndose unívocamente la condición de "pausado por chef" frente a "sin stock" y "desactivado por administrador".
- **SC-009**: El 100% de los intentos de usuarios con rol Chef de fijar o editar umbrales de stock crítico son rechazados con una respuesta formal de denegación de autorización.
- **SC-010**: El 100% de las interfaces del rol Administrador carecen del control de pausa manual del chef, preservando la exclusividad operativa del chef sobre dicha acción y la exclusividad del administrador sobre la desactivación administrativa.
- **SC-011**: El 100% de los platos asociados a ingredientes en estado crítico que cuenten con stock disponible remanente ($> 0$) continúan disponibles para su venta y consumo por parte del comensal.
- **SC-012**: El 100% de los ingredientes que cruzan el umbral crítico por efecto de una venta o ajuste manual reflejan el indicador visual de alerta en la siguiente consulta del módulo de inventario.
- **SC-013**: El 100% de los ingredientes reabastecidos por encima de su umbral crítico dejan de exhibir el indicador visual de alerta en la siguiente consulta del módulo de inventario.
- **SC-014**: Ante la coexistencia de múltiples causas de indisponibilidad sobre un mismo plato (ejemplo: pausa manual del chef y falta de stock de cocina), el sistema preserva la independencia de cada estado y las refleja de forma distinguible en los paneles de personal correspondientes.

---

## Assumptions

- `[SUPUESTO]` Existe un módulo de inventario con ingredientes administrables (crear/editar) gobernado por el rol Administrador, según lo definido en la especificación 004.
- `[SUPUESTO]` Existe un módulo de catálogo gastronómico con platos publicados y un mecanismo de visualización para el comensal, según lo establecido en las especificaciones 007 y 008.
- `[SUPUESTO]` Existe un registro de stock por ingrediente que varía de forma dinámica por efecto de ventas comerciales y de ajustes manuales de inventario, conforme a las especificaciones 004, 008 y 011.
- `[SUPUESTO]` Existen roles de usuario diferenciados ("Administrador" y "Chef") con mecanismos de autenticación y autorización preexistentes, según las especificaciones 001, 002 y 004.
- `[SUPUESTO]` Existe infraestructura de sincronización en tiempo real para propagar cambios de disponibilidad y visibilidad en el catálogo del comensal con una latencia objetivo no mayor a 3 segundos, conforme a la especificación 008.
- `[SUPUESTO]` Existen recetas técnicas que vinculan insumos del inventario con platos del catálogo, según lo especificado en las especificaciones 005 y 006.
- `[SUPUESTO]` Existe el estado de "sin stock" de un plato derivado de manera automática del inventario de insumos de cocina, conceptual y operativamente diferenciado de la pausa manual del chef, de acuerdo con la especificación 008.
- `[SUPUESTO]` Existe la facultad y autoridad del administrador para desactivar y despublicar platos de manera permanente o comercial del catálogo, preexistente e independiente de la pausa de servicio del chef, conforme a las especificaciones 007 y 008.
- `[SUPUESTO]` La interpretación operativa de "caer por debajo" del umbral de stock crítico en el criterio AC-2.1 es estrictamente menor ($\text{cantidad} < \text{umbral}$). Valores iguales al umbral se consideran dentro del margen de seguridad.
- `[SUPUESTO]` Si el administrador define un umbral de stock crítico igual a 0, se asume que el ingrediente solo alertará si la cantidad física llega a registrarse negativamente por un desajuste manual de inventario; bajo operación estándar sin valores negativos, el umbral 0 desactiva efectivamente la alerta.
- `[SUPUESTO]` Un ingrediente recién creado o sin configuración previa de umbral crítico asume un estado sin umbral de referencia, no emitiendo alertas hasta que el administrador defina un valor explícito.
- `[SUPUESTO]` No se imponen límites superiores arbitrarios al valor del umbral de stock crítico, admitiéndose cualquier número entero mayor o igual a cero que el administrador considere adecuado para su operación.
- `[SUPUESTO]` La ubicación, estilo visual, paleta de colores y disposición del control de alternancia (toggle) en la interfaz del chef constituyen decisiones de diseño de interfaz de usuario que corresponden a la fase de planificación técnica, manteniéndose esta especificación puramente orientada al comportamiento funcional observable.

---

## Out of Scope

Quedan formal y explícitamente fuera del alcance de la presente especificación e iteración las siguientes funcionalidades y comportamientos:

- Envío de correos electrónicos, mensajes SMS, notificaciones push o comunicaciones externas dirigidas al chef, administrador o proveedores ante alertas de stock crítico o pausas de platos.
- Generación automatizada de órdenes de compra, integración con proveedores externos, intercambio electrónico de datos (EDI) o portales marketplace B2B.
- Configuración de umbrales críticos globales, únicos o por categoría de producto; la configuración se limita estrictamente a la modalidad individual por insumo.
- Múltiples niveles escalonados de umbrales de stock (por ejemplo: advertencia preventiva, nivel medio y crítico) por ingrediente.
- Algoritmos de pronóstico de consumo, estimación proyectada de agotamiento o sugerencias automáticas de cantidades a comprar.
- Programación diferida de pausas por fecha u horario, pausas automáticas por franjas del día o desactivaciones por temporada.
- Temporizadores de auto-expiración o duración máxima predeterminada para la pausa manual del chef.
- Campos de texto libre o selectores de motivos obligatorios asociados a la activación de la pausa manual del chef.
- Notificaciones directas o avisos emergentes dirigidos al comensal cuando un plato se pausa o despausa durante su navegación.
- Mecanismos de autenticación, creación de cuentas o gestión de permisos de roles (funcionalidades preexistentes provistas por las especificaciones 001, 002 y 004).
- Facultad del administrador para anular o conmutar la pausa manual del chef desde el mecanismo específico del chef (el administrador utiliza su propio canal de desactivación administrativa).
- Edición o alteración de recetas, insumos o fichas técnicas por parte del chef desde el control de pausa.
- Modificación de precios comerciales de venta al público por parte del chef.
- Generación de reportes de auditoría de umbrales, tableros de analítica de criticidad o historiales de cambios de umbral y pausas.
- Afectación de los filtros de catálogo manuales por la pausa (los filtros operan sobre platos visibles; la pausa oculta el plato antes de la evaluación de filtros).
- Gestión multi-sucursal, depósitos distribuidos o umbrales diferenciados por proveedor.
- Historial acumulado de pausas por plato o registros históricos de reposición en esta pantalla.
- Alertas visibles dirigidas al comensal sobre el estado de reposición de los ingredientes de cocina.
- Umbrales de stock mínimo configurados a nivel de plato terminado (la gestión se restringe exclusivamente al inventario de ingredientes).

