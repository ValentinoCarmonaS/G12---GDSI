# Feature Specification: Detalle de Plato, Información Nutricional, Precios y Filtros Manuales de Catálogo

**Feature Branch**: `011-dish-detail-manual-filters`

**Created**: 01/11/2026 | **Approved**: 01/11/2026

**Status**: Approved (Audited via grill-me & speckit-checklist)

**Input**: User description: "Sistema web de pedidos de comida. Iteración actual extiende la experiencia del comensal sobre el catálogo existente: detalle de plato, ingredientes/alérgenos, precio y filtros manuales de búsqueda. Cuatro historias de usuario aprobadas y cerradas: COM-5.1.1 (Visualizar multimedia y nombre), COM-5.1.2 (Ver ingredientes y alérgenos), COM-5.1.3 (Ver precio) y COM-4.2.2 (Búsqueda por filtros manuales)."

---

## Clarifications

### Session 2026-11-01
- Q: ¿Cómo deben combinarse los filtros manuales (categoría y precio) con la barra de búsqueda de texto del catálogo (COM-4.2.3) cuando ambos están activos simultáneamente? → A: Acumulativa conjuntiva (AND) con limpieza unificada: la búsqueda por texto y los filtros manuales operan en conjunto mediante una intersección lógica AND ($(\text{Categorías}) \land (\text{Rango de precio}) \land (\text{Texto de búsqueda})$). Al presionar el botón de limpiar filtros, se restablecen de forma unificada tanto los filtros manuales de la barra lateral (categorías y rango de precios) como el término ingresado en la barra de búsqueda de texto del catálogo.
- Q: Con el selector de cantidad ubicado en el modal de detalle del plato (AC-3.5): ¿cómo debe limitarse la cantidad máxima si el comensal ya tiene unidades de ese plato en su carrito? → A: Sin tope artificial en el selector del modal: el selector permite elegir libremente cualquier número entero positivo ($n \ge 1$) para estimar y calcular el subtotal provisorio; la validación de inventario frente al stock de cocina remanente se realiza al presionar "Agregar al Carrito", bloqueando la acción e informando al comensal si la cantidad acumulada excede la disponibilidad real.
- Q: ¿Cómo deben persistir los filtros manuales de la barra lateral (categorías y precio) si el comensal navega a otra sección (como su perfil o el carrito) y luego regresa al catálogo del mismo restaurante? → A: Persistentes durante la sesión en el establecimiento: los filtros manuales aplicados se conservan en la sesión local mientras el comensal permanezca navegando en el mismo establecimiento, permitiéndole ir a su perfil o carrito y regresar al catálogo con sus filtros intactos. Los filtros se restablecen únicamente al accionar el botón "Limpiar filtros" o al cambiar de establecimiento gastronómico (spec 008).
- Q: En el modal de detalle del plato: ¿cómo deben visualizarse los alérgenos cuando coinciden con restricciones alimentarias del perfil del comensal autenticado (spec 003)? → A: Etiquetas neutras estándar: el modal de detalle expone las etiquetas de alérgenos y la lista de componentes del plato de forma objetiva, uniforme e idéntica para todos los comensales, sin aplicar resaltes condicionales ni alertas preventivas personalizadas basadas en el perfil dietario en esta vista informativa.
- Q: ¿Cómo debe reaccionar el modal de detalle abierto si el Administrador cancela o despublica el plato mientras el comensal lo está visualizando? → A: Deshabilitación en tiempo real en el modal: el panel actualiza su vista a "Plato no disponible", inhabilita los controles de compra en tiempo real (lapso $\le 3$ segundos) sin cerrar intempestivamente el panel, y al cerrar el modal, el plato se oculta o actualiza correspondientemente en el catálogo.

---

## Invariantes de Negocio

Los siguientes invariantes definen las reglas funcionales y operativas inviolables de la experiencia de catálogo para el comensal:

- **INV-1: Preservación del estado de navegación en catálogo subyacente**  
  $$\text{panel\_detalle\_abierto} \iff \text{vista\_catálogo\_subyacente\_en\_segundo\_plano}$$  
  Cerrar el panel (mediante botón de cierre o clic fuera del área del panel) retorna al comensal a la vista del catálogo manteniendo exactamente la coordenada de desplazamiento vertical previa.  
  *Ejemplo observable*: Si el comensal navega hacia abajo 1200 píxeles en la grilla y pulsa una tarjeta, al cerrar el panel superpuesto vuelve a la posición exacta de 1200 píxeles, sin reinicio al inicio de la página.

- **INV-2: Ausencia de fotografía en el plato**  
  $$\neg\,\text{posee\_fotografía}(\text{plato}) \implies \text{panel\_muestra}(\text{nombre} \land \text{descripción}) \land \neg\,\text{espacio\_vacío\_o\_placeholder\_roto}$$  
  Si un plato no tiene imagen cargada, el panel presenta directamente el nombre y la descripción ocupando la cabecera sin desplegar íconos de error de imagen ni reservar un espacio rectangular en blanco.

- **INV-3: Degradación elegante y prioridad de contenido ante conexión de red lenta o intermitente**  
  $$\text{conexión\_lenta} \implies (\text{texto\_visible\_inmediato}) \land (\text{indicador\_de\_carga\_visible}) \land (\text{indicador\_reemplazado\_por\_imagen\_al\_completar})$$  
  El nombre y la descripción textual se muestran de inmediato junto con un indicador visual temporal de carga; la imagen en alta resolución sustituye dicho indicador al finalizar su transferencia.

- **INV-4: Adaptación estructural de textos largos**  
  $$\text{longitud\_texto} > \text{límite\_visual} \implies \text{desplazamiento\_interno\_activo} \land \text{estructura\_panel\_intacta}$$  
  Descripciones comerciales extensas activan barras de desplazamiento vertical interno dentro del panel, garantizando que los contenedores, botones de acción y títulos mantengan su integridad visual sin desbordamientos de pantalla.

- **INV-5: Invalidación reactiva de disponibilidad ante agotamiento de stock o baja administrativa**  
  $$(\text{stock\_insumo\_principal} = 0) \lor \text{plato\_cancelado\_o\_despublicado} \implies (\text{plato\_marcado\_no\_disponible}) \land (\text{acción\_compra\_deshabilitada}) \land \neg\,\text{requiere\_recarga\_manual}$$  
  Si el insumo principal de un plato se agota por una orden concurrente o el administrador cancela/despublica el plato mientras el panel está abierto, en un lapso no superior a 3 segundos el panel refleja la indisponibilidad ("Plato no disponible") e inactiva los controles de compra de manera automática sin forzar el cierre del panel; al cerrarlo, la vista del catálogo se actualiza ocultando o inhabilitando el plato.

- **INV-6: Integridad y neutralidad de información nutricional y alérgenos**  
  $$\text{alérgenos\_configurados}(\text{plato}) \implies \text{lista\_completa\_ingredientes} \land \text{etiquetas\_alérgenos\_neutras\_visibles}$$  
  Todo plato con alérgenos asociados expone obligatoriamente su desglose completo de ingredientes y las etiquetas identificatorias correspondientes de forma estándar, objetiva y uniforme para todos los comensales, sin aplicar resaltes ni alertas personalizadas basadas en el perfil del usuario en esta vista informativa.

- **INV-7: Reflejo de modificaciones de receta en cocina**  
  $$\text{receta\_modificada} \implies \text{siguiente\_apertura\_muestra\_ingredientes\_actualizados}$$  
  Cualquier reemplazo o ajuste de ingredientes ejecutado en cocina se refleja inmediatamente en la siguiente apertura del panel o actualización en pantalla sin requerir reinicio de sesión del comensal.

- **INV-8: Ausencia de información de ingredientes**  
  $$\neg\,\text{ingredientes\_registrados}(\text{plato}) \implies \text{mensaje\_explícito}("Lista de componentes no disponible") \land \neg\,\text{lista\_vacía} \land \neg\,\text{error\_genérico}$$  
  Un plato sin receta registrada presenta de forma explícita la leyenda informativa de no disponibilidad de componentes, evitando secciones en blanco o mensajes de fallo del sistema.

- **INV-9: Transparencia fiscal del precio exhibido**  
  $$\text{precio\_mostrado} = \text{precio\_final\_unitario\_con\_impuestos\_incluidos}$$  
  El valor monetario exhibido en el catálogo y en el panel es estrictamente el precio final de venta al público fijado por la administración, sin adicionar cargos sorpresa posteriores.

- **INV-10: Formateo monetario conforme a configuración regional**  
  $$\text{formato}(\text{precio}) \equiv \text{símbolo\_moneda} + \text{separador\_miles} + \text{precisión\_decimal}$$  
  El precio visible se formatea aplicando las reglas regionales vigentes del establecimiento (por ejemplo: `$ 12.500,00` o `$ 12.500` según corresponda).

- **INV-11: Actualización de precio administrativo**  
  $$\text{precio\_modificado\_por\_admin} \implies \text{visible\_en\_siguiente\_recarga\_o\_reapertura} \land \neg\,\text{requiere\_nuevo\_inicio\_sesión}$$  
  Las alteraciones comerciales de precios impactan directamente en la vista del comensal al volver a abrir el panel o refrescar la pantalla, sin invalidar su sesión activa.

- **INV-12: Preservación de precio referencial en platos agotados**  
  $$\text{stock}(\text{plato}) = 0 \implies \text{precio\_visible\_como\_referencia} \land \text{controles\_cantidad\_y\_confirmación\_deshabilitados}$$  
  Un plato sin stock conserva legible su precio final histórico a título informativo, pero inhabilita la selección de unidades y el botón de agregar al pedido.

- **INV-13: Cálculo dinámico de subtotal provisorio y libertad de estimación**  
  $$\text{subtotal\_provisorio} = \text{precio\_unitario\_mostrado} \times \text{cantidad\_seleccionada} \quad (\text{para toda cantidad } n \in \mathbb{N}^+)$$  
  Cada interacción con el selector numérico de unidades en el panel recalcula inmediatamente el subtotal proyectado sin imponer un tope artificial en la fase de estimación; la validación de inventario frente al stock de cocina remanente se efectúa al confirmar la acción de agregar al carrito.  
  *Ejemplo observable*: A un precio unitario de `$ 4.000`, al cambiar la cantidad a 3 unidades, el subtotal provisorio exhibe instantáneamente `$ 12.000`. Si el comensal intenta agregar una cantidad que sumada a su carrito supere el stock disponible, la acción de compra se bloquea informando el límite de disponibilidad.

- **INV-14: Álgebra de combinación de filtros manuales e interoperabilidad con búsqueda por texto**  
  $$\text{coincidencia} \iff \left(\bigvee_{c \in C_{\text{sel}}} \text{pertenece}(p, c)\right) \land \left(P_{\min} \le \text{precio}(p) \le P_{\max}\right) \land \text{coincide\_búsqueda\_texto}(p, T_{\text{busq}})$$  
  Los filtros manuales aplican una unión lógica (OR) entre opciones de la misma dimensión (múltiples categorías) y una intersección lógica (AND) entre dimensiones heterogéneas (categorías y rango de precio). Cuando además existe un término activo en la barra de búsqueda de texto del catálogo (COM-4.2.3), este se compone conjuntivamente con lógica AND sobre los filtros manuales.  
  *Ejemplo observable*: Si se seleccionan categorías "Pastas" y "Pizzas" con precio entre `$ 5.000` y `$ 10.000` y en la búsqueda de texto figura "casero", se muestran únicamente los platos que pertenezcan a Pastas o Pizzas, cuyo precio final se ubique en `[$ 5.000, $ 10.000]` y cuyo título o ingredientes contengan el término "casero".

- **INV-15: Manejo explícito de combinaciones sin resultados**  
  $$\text{resultados} = \emptyset \implies \text{aviso\_falta\_resultados} \land \text{botón\_limpiar\_filtros\_disponible}$$  
  Cuando ningún plato cumple las condiciones de filtrado, el sistema despliega un mensaje explicativo y provee una acción visible para limpiar los filtros aplicados.

- **INV-16: Reversibilidad de filtros manuales, persistencia en sesión y limpieza unificada**  
  $$\text{limpiar\_filtros} \implies (\text{categorías\_desmarcadas}) \land (\text{rango\_precio\_vaciado}) \land (\text{texto\_búsqueda\_vaciado}) \land (\text{catálogo\_restaurado})$$  
  Los filtros manuales aplicados se conservan en la sesión local mientras el comensal permanezca navegando en el mismo establecimiento gastronómico (incluso si transita hacia su perfil o al carrito de compras). Al activar la acción de limpiar o restablecer filtros, o al cambiar de establecimiento (spec 008), se remueven todas las selecciones de categorías y rangos de precio en la barra lateral y se vacía simultáneamente el término de búsqueda de texto del catálogo, restaurando la totalidad del menú disponible en su estado inicial.

- **INV-17: Visibilidad no excluyente de productos agotados bajo filtros activos**  
  $$(\text{stock}(p) = 0) \land \text{cumple\_filtros}(p) \implies \text{visible\_en\_resultados} \land \text{estado\_deshabilitado}$$  
  Los platos sin inventario pertenecientes a una categoría filtrada se mantienen visibles en la grilla bajo estado atenuado y deshabilitado, indicando su condición de agotado.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Visualizar multimedia y nombre del plato (Priority: P1)

**COM-5.1.1**: Como comensal, quiero tocar un plato y ver su foto en tamaño grande y su descripción detallada, para decidir si la presentación me resulta apetitosa.

**Why this priority**: Crítica para la compra (AC-1.1 a AC-1.6). Sin la visualización en detalle de la presentación, nombre y descripción, el comensal carece de información suficiente para tomar la decisión de consumo, bloqueando la conversión comercial.

**Independent Test**: Puede probarse de forma autónoma seleccionando una tarjeta de plato en el catálogo, confirmando el despliegue del panel superpuesto con fotografía en alta resolución, nombre y descripción, cerrando el panel (por botón o clic externo) y verificando el retorno a la posición exacta de desplazamiento en el catálogo.

**Acceptance Scenarios**:

1. **Dado** que el comensal navega por el catálogo del establecimiento, **Cuando** hace clic sobre la tarjeta de un plato, **Entonces** el sistema despliega un panel superpuesto sobre el catálogo sin abandonar la vista, mostrando la fotografía en alta resolución, el nombre completo y la descripción detallada del producto (AC-1.1).
2. **Dado** que la administración no registró una fotografía específica para el plato, **Cuando** el comensal abre su detalle, **Entonces** el sistema presenta exclusivamente el nombre y la descripción del producto, sin exhibir recuadros vacíos ni indicadores rotos de imagen (AC-1.2).
3. **Dado** que el comensal se encuentra visualizando el detalle multimedia de un plato en el panel superpuesto, **Cuando** presiona el botón de cierre o hace clic fuera del panel, **Entonces** el sistema cierra el panel y lo devuelve a la vista del catálogo manteniendo la posición exacta de desplazamiento vertical que tenía en la grilla (AC-1.3).
4. **Dado** que el comensal tiene una conexión de red lenta o intermitente, **Cuando** intenta abrir el detalle del plato, **Entonces** el sistema muestra de inmediato el nombre y la descripción en texto junto con un indicador visual temporal de carga hasta completar la visualización de la imagen en alta resolución (AC-1.4).
5. **Dado** que el plato cuenta con un nombre o una descripción comercial excesivamente largos, **Cuando** el sistema presenta el panel superpuesto, **Entonces** ajusta el texto habilitando barras de desplazamiento interno vertical para preservar la estructura visual y dimensiones del panel (AC-1.5).
6. **Dado** que el comensal se encuentra con el panel superpuesto abierto evaluando un plato, **Cuando** el stock del insumo principal se agota sin requerir recarga manual debido a un pedido ajeno, **Entonces** el sistema actualiza inmediatamente la vista del panel marcándolo como no disponible y deshabilitando la acción de compra (AC-1.6).

---

### User Story 2 - Ver precio final y subtotal provisorio (Priority: P1)

**COM-5.1.3**: Como comensal, quiero ver el precio final con impuestos incluidos de forma visible, para evaluar si se ajusta a mi presupuesto.

**Why this priority**: Crítica para la compra (AC-3.1 a AC-3.5). Sin un precio visible, transparente, exacto y formateado regionalmente, la transacción no puede concretarse con confianza ni previsibilidad presupuestaria.

**Independent Test**: Puede probarse de forma autónoma abriendo el panel de un plato con precio comercial asignado, comprobando que el monto final unitario con impuestos se visualice destacado junto al botón de acción con el formato regional correspondiente, ajustando el selector de cantidad y verificando que el subtotal provisorio se actualice dinámicamente como el producto exacto de precio unitario por cantidad.

**Acceptance Scenarios**:

1. **Dado** que el comensal ingresa al detalle del plato, **Cuando** la administración tiene fijado un valor comercial, **Entonces** el sistema muestra el precio final unitario con impuestos incluidos en un tamaño destacado junto al botón de agregar al carrito (AC-3.1).
2. **Dado** que el sistema opera bajo una configuración regional determinada, **Cuando** muestra el precio del plato, **Entonces** aplica el formato de moneda local respetando el símbolo monetario, los separadores de miles y la precisión decimal vigente (AC-3.2).
3. **Dado** que la administración modifica el precio de un plato desde el panel de gestión, **Cuando** el comensal recarga el catálogo o vuelve a abrir el panel superpuesto, **Entonces** el sistema refleja instantáneamente el nuevo valor comercial actualizado sin exigir un nuevo inicio de sesión (AC-3.3).
4. **Dado** que un plato se encuentra temporalmente sin stock en cocina, **Cuando** el comensal abre su detalle, **Entonces** el sistema mantiene visible el precio final histórico como referencia informativa pero inactiva la visualización o estado activo de los botones de suma y confirmación (AC-3.4).
5. **Dado** que el comensal evalúa el precio de un plato en el panel superpuesto, **Cuando** decide sumar más de una unidad utilizando el selector de cantidad, **Entonces** el sistema actualiza dinámicamente un subtotal provisorio en pantalla multiplicando el precio unitario por la cantidad elegida (AC-3.5).

---

### User Story 3 - Ver ingredientes y alérgenos (Priority: P2)

**COM-5.1.2**: Como comensal cuidadoso, quiero poder leer la lista exacta de ingredientes que componen el plato y sus etiquetas de alérgenos, para garantizar que no voy a tener una reacción alérgica ni comer algo que no deseo.

**Why this priority**: Importante para la seguridad alimentaria y prevención de incidentes de salud (AC-2.1 a AC-2.3), pero catalogada P2 debido a que los comensales sin restricciones dietarias pueden adquirir el plato sin inspeccionar la receta completa y aquellos con dudas pueden consultar por canales asistidos.

**Independent Test**: Puede probarse de forma autónoma ingresando a platos con y sin ingredientes/alérgenos registrados, corroborando el despliegue de las listas completas y sellos de alérgenos, la visualización del mensaje explicativo cuando no hay datos disponibles, y la actualización inmediata ante un reemplazo de receta en cocina.

**Acceptance Scenarios**:

1. **Dado** que el comensal visualiza el detalle de un plato configurado con alérgenos, **Cuando** revisa la sección nutricional del panel, **Entonces** el sistema despliega la lista completa de ingredientes y las etiquetas identificadoras de alérgenos (AC-2.1).
2. **Dado** que la receta de un plato sufre un reemplazo de ingredientes en tiempo real en la cocina, **Cuando** el comensal abre su ficha de detalle, **Entonces** el sistema muestra la lista de componentes actualizada dinámicamente para reflejar la modificación real del inventario (AC-2.2).
3. **Dado** que un plato fue registrado sin información de ingredientes, **Cuando** el comensal intenta consultar dicha sección, **Entonces** el sistema muestra un mensaje indicando que la lista de componentes no se encuentra disponible (AC-2.3).

---

### User Story 4 - Búsqueda por filtros manuales de catálogo (Priority: P2)

**COM-4.2.2**: Como comensal, quiero poder filtrar por categorías o rango de precio, para encontrar algo accesible o de un tipo específico de manera rápida.

**Why this priority**: Optimiza la navegabilidad y velocidad de descubrimiento en menús extensos (AC-4.1 a AC-4.6), pero no bloquea la navegación manual básica por categorías existente en el catálogo.

**Independent Test**: Puede probarse de forma autónoma seleccionando una o más categorías en la barra lateral de filtros, estableciendo márgenes de precio mínimo y máximo, combinando ambos criterios, provocando escenarios sin resultados para probar el botón de limpieza, y verificando que los platos agotados se mantengan visibles en estado deshabilitado.

**Acceptance Scenarios**:

1. **Dado** que el comensal se encuentra en la vista de catálogo, **Cuando** selecciona una o más categorías desde la barra lateral de filtros manuales, **Entonces** el sistema actualiza la vista inmediatamente mostrando únicamente los platos que pertenecen a cualquiera de las categorías marcadas (semántica disyuntiva OR) (AC-4.1).
2. **Dado** que el comensal utiliza el filtro de rango de precio, **Cuando** establece un valor monetario mínimo y un valor máximo, **Entonces** el sistema ajusta la grilla para exhibir exclusivamente los productos cuyo costo final se encuentre dentro de dicho intervalo cerrado e inclusivo (AC-4.2).
3. **Dado** que el comensal combina selecciones de categorías y un rango de precio simultáneamente, **When** el sistema procesa la solicitud, **Then** muestra únicamente los resultados que satisfacen ambas dimensiones en forma conjunta (semántica conjuntiva AND) (AC-4.3).
4. **Dado** que el comensal aplica una combinación de filtros manuales restrictiva que no arroja coincidencias, **Cuando** el catálogo evalúa la grilla, **Entonces** el sistema despliega un aviso informando la falta de resultados y ofrece un botón destacado para limpiar los filtros (AC-4.4).
5. **Dado** que el comensal tiene varios filtros manuales aplicados, **Cuando** presiona la opción de restablecer o limpiar filtros, **Entonces** el sistema remueve todas las selecciones y vuelve a mostrar el catálogo en su estado original completo (AC-4.5).
6. **Dado** que el comensal aplica un filtro manual por categoría, **Cuando** algunos platos de dicha sección se encuentran sin stock, **Entonces** el sistema los sigue mostrando dentro de los resultados filtrados pero en estado visual atenuado y deshabilitado (AC-4.6).

---

### Edge Cases

- **EC-001**: Administrador no cargó fotografía del plato (AC-1.2) → El panel superpuesto presenta exclusivamente el nombre y la descripción en la parte superior, sin reservar espacio vacío ni renderizar íconos rotos.
- **EC-002**: Conexión de red lenta o intermitente al abrir el panel superpuesto (AC-1.4) → El texto descriptivo y el nombre se exhiben de forma inmediata mientras un indicador visual de carga informa la transferencia de la imagen hasta su renderizado final.
- **EC-003**: Nombre comercial o descripción excesivamente largos (AC-1.5) → El panel contiene el texto habilitando barras de desplazamiento vertical interno sin alterar sus límites ni romper los elementos de la interfaz.
- **EC-004**: Agotamiento del insumo principal o cancelación/despublicación del plato mientras el panel está abierto (AC-1.6) → El sistema actualiza en tiempo real (lapso ≤ 3 segundos) el estado del plato a no disponible en el panel y deshabilita la compra sin forzar el cierre del modal; al cerrar el panel, el plato se oculta o actualiza en la grilla del catálogo.
- **EC-005**: Reabastecimiento del insumo principal mientras el panel está abierto → El panel reactiva automáticamente los controles de compra y remueve el distintivo de agotado sin requerir recarga manual.
- **EC-006**: Modificación o reemplazo de ingredientes en receta de cocina mientras el comensal interactúa → La siguiente apertura de la ficha del plato presenta la lista actualizada de insumos y alérgenos.
- **EC-007**: Plato sin información de ingredientes registrada (AC-2.3) → Se exhibe un mensaje explícito indicando "Lista de componentes no disponible" en lugar de un listado vacío o un mensaje de error genérico.
- **EC-008**: Plato sin alérgenos configurados pero con ingredientes registrados → Se presenta la lista completa de ingredientes y se omite la sección de sellos o se informa que no contiene alérgenos declarados.
- **EC-009**: Modificación de precio administrativo mientras el comensal navega el menú (AC-3.3) → El nuevo valor se refleja en la siguiente recarga o reapertura de la ficha sin exigir un nuevo inicio de sesión.
- **EC-010**: Apertura del detalle de un plato sin stock (AC-3.4) → El precio histórico final se visualiza a título informativo pero los botones de suma de unidades y de agregar al pedido se presentan deshabilitados.
- **EC-011**: Modificación de cantidad en el selector y validación de inventario al agregar → El comensal puede modificar la cantidad a $N$ unidades para estimar subtotales; al presionar "Agregar al Carrito", si $N$ más lo ya existente en el carrito supera el stock disponible en cocina, el sistema rechaza la incorporación y notifica el límite de disponibilidad. Si se cierra el panel sin confirmar, el subtotal se descarta y el selector reinicia en 1 para aperturas posteriores.
- **EC-012**: Selección simultánea de múltiples categorías en el filtro manual (AC-4.1) → El sistema aplica la unión lógica de categorías (OR), exhibiendo platos que correspondan a cualquiera de las seleccionadas.
- **EC-013**: Aplicación concurrente de filtro de categoría, rango de precio y búsqueda por texto (AC-4.3, COM-4.2.3) → El sistema aplica la intersección lógica (AND entre todas las dimensiones activas), mostrando solo platos que cumplan la categoría, se encuentren dentro del rango de precio y contengan el término ingresado en la búsqueda de texto.
- **EC-014**: Filtro de rango de precio con valor mínimo estrictamente mayor al máximo ($P_{\min} > P_{\max}$) → El sistema despliega un mensaje explicativo de validación ("El precio mínimo no puede superar al precio máximo") y bloquea la aplicación del filtro hasta que los valores sean corregidos (`[SUPUESTO]`).
- **EC-015**: Filtro de rango de precio con valor mínimo idéntico al valor máximo ($P_{\min} = P_{\max}$) → El sistema filtra evaluando la igualdad estricta, exhibiendo platos cuyo precio final coincida exactamente con dicho valor o mostrando el mensaje de falta de resultados si no hubiera coincidencias.
- **EC-016**: Plato cuyo precio final coincide exactamente con el límite inferior o superior del rango de precio → Semántica inclusiva en ambos extremos: el plato se considera dentro del margen y se exhibe en los resultados (`[SUPUESTO]`).
- **EC-017**: Combinación de filtros manuales sin ningún producto coincidente (AC-4.4) → La grilla se sustituye por un aviso visible de ausencia de resultados junto a un botón interactivo para limpiar los filtros.
- **EC-018**: Limpieza de filtros cuando múltiples criterios y búsqueda por texto están activos (AC-4.5) → La acción de limpiar desmarca todas las casillas de categorías, vacía los límites de precio y vacía simultáneamente la barra de búsqueda de texto, restableciendo el catálogo a su estado completo.
- **EC-019**: Platos sin stock dentro de los resultados de una categoría filtrada (AC-4.6) → Permanecen visibles en la lista filtrada pero exhibiendo el distintivo de agotado y sus botones de compra deshabilitados.
- **EC-020**: Variación de stock mientras la grilla tiene filtros manuales activos → Los platos que se agotan o reponen actualizan su estado en la grilla filtrada en tiempo real sin reiniciar la búsqueda ni alterar los filtros seleccionados.
- **EC-021**: Cierre del panel mediante clic en el fondo exterior (AC-1.3) → El panel se oculta inmediatamente y el catálogo conserva la posición de desplazamiento vertical previa sin salto visual.
- **EC-022**: Redimensionamiento de ventana o cambio de orientación con panel abierto → El panel superpuesto adapta su tamaño de forma responsive y la posición relativa de desplazamiento en el catálogo se mantiene dentro del mismo estado de disposición visual (`[SUPUESTO]`).

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE desplegar un panel superpuesto informativo sobre la vista del catálogo al hacer clic en cualquier tarjeta de plato sin abandonar la pantalla del catálogo (cubre AC-1.1).
- **FR-002**: El panel superpuesto DEBE mostrar la fotografía en alta resolución, el nombre comercial completo y la descripción detallada del plato cuando estos datos se encuentren registrados (cubre AC-1.1).
- **FR-003**: Ante un plato que no posea fotografía cargada, el sistema DEBE exhibir únicamente el nombre y la descripción en la cabecera del panel sin mostrar indicadores de imagen rota ni dejar áreas rectangulares vacías (cubre AC-1.2, INV-2).
- **FR-004**: Al presionar el botón de cierre del panel o al hacer clic fuera de sus límites visuales, el sistema DEBE cerrar el panel y retornar al comensal a la vista del catálogo conservando la coordenada exacta de desplazamiento vertical que tenía previa a la apertura (cubre AC-1.3, INV-1).
- **FR-005**: Ante conexiones de red lentas o intermitentes, el sistema DEBE visualizar de inmediato el nombre y la descripción textual del plato junto con un indicador de carga temporal, sustituyéndolo por la fotografía cuando esta complete su transferencia (cubre AC-1.4, INV-3).
- **FR-006**: Cuando el nombre o la descripción del plato excedan las dimensiones predeterminadas del contenedor, el sistema DEBE incorporar barras de desplazamiento vertical interno dentro del panel para preservar la integridad visual de la interfaz (cubre AC-1.5, INV-4).
- **FR-007**: Si el stock del insumo principal se agota debido a un pedido concurrente o el administrador cancela/despublica el plato mientras el panel está abierto, el sistema DEBE actualizar la vista en tiempo real (en un tiempo $\le 3$ segundos) marcando el plato como no disponible e inhabilitando las acciones de compra sin exigir el cierre o reapertura manual (cubre AC-1.6, INV-5).
- **FR-008**: Al consultar la información nutricional de un plato configurado con alérgenos, el sistema DEBE presentar la lista completa de ingredientes y las etiquetas identificadoras de alérgenos de forma neutra y estándar para todos los comensales (cubre AC-2.1, INV-6).
- **FR-009**: Cuando se registre un cambio o reemplazo de ingredientes en la receta de cocina, el sistema DEBE reflejar los componentes actualizados dinámicamente en la siguiente visualización del panel (cubre AC-2.2, INV-7).
- **FR-010**: Si un plato carece de información de ingredientes registrada, el sistema DEBE exhibir un mensaje explícito indicando que la lista de componentes no se encuentra disponible, evitando mostrar secciones vacías o mensajes de error del sistema (cubre AC-2.3, INV-8).
- **FR-011**: El sistema DEBE exhibir el precio final unitario con impuestos incluidos en un tamaño destacado dentro del panel junto al botón de agregar al pedido (cubre AC-3.1, INV-9).
- **FR-012**: El precio del plato DEBE formatearse aplicando las reglas de la configuración regional vigente del establecimiento, contemplando el símbolo monetario, el separador de miles y la precisión de decimales (cubre AC-3.2, INV-10).
- **FR-013**: Cuando la administración modifique el precio de un plato, el sistema DEBE reflejar el nuevo valor comercial en la siguiente recarga del catálogo o reapertura del panel sin requerir que el comensal inicie una nueva sesión (cubre AC-3.3, INV-11).
- **FR-014**: Si un plato se encuentra sin stock, el sistema DEBE mantener visible su precio final de referencia pero DEBE desactivar los controles de cantidad y el botón de confirmación de compra (cubre AC-3.4, INV-12).
- **FR-015**: El panel DEBE proveer un selector numérico de cantidad que recalcule dinámicamente un subtotal provisorio en pantalla como el producto del precio unitario por las unidades seleccionadas ($n \ge 1$), permitiendo libre estimación sin bloqueo previo de cantidad; al presionar el botón de agregar al pedido, el sistema DEBE validar la disponibilidad contra el stock remanente de cocina (descontando unidades ya existentes en el carrito) y bloquear la incorporación si la cantidad excede el inventario (cubre AC-3.5, INV-13).
- **FR-016**: La barra lateral de filtros manuales DEBE permitir la selección de una o más categorías simultáneamente, actualizando la grilla del catálogo de inmediato para exhibir platos que pertenezcan a cualquiera de las categorías marcadas mediante semántica de unión lógica (OR) (cubre AC-4.1, INV-14).
- **FR-017**: El filtro manual DEBE permitir fijar un rango de precios mediante valores mínimo y máximo, ajustando la grilla para exhibir exclusivamente productos cuyo precio final se ubique dentro de dicho intervalo cerrado e inclusivo (cubre AC-4.2, INV-14).
- **FR-018**: Cuando el comensal combine filtros de categorías y de rango de precios en forma simultánea (y eventualmente con un término en la barra de búsqueda de texto del catálogo), el sistema DEBE aplicar una intersección lógica (AND entre todas las dimensiones activas), mostrando platos que pertenezcan a las categorías marcadas, cuyo precio esté dentro del margen fijado y coincidan con el término buscado (cubre AC-4.3, INV-14).
- **FR-019**: Cuando ninguna opción del catálogo cumpla con las condiciones de los filtros aplicados, el sistema DEBE desplegar un aviso explícito de falta de resultados y proveer una acción visible para limpiar los filtros (cubre AC-4.4, INV-15).
- **FR-020**: Al presionar la acción de restablecer o limpiar filtros, el sistema DEBE remover todas las opciones seleccionadas en la barra lateral (categorías y rango de precios) y DEBE vaciar simultáneamente el término activo en la barra de búsqueda de texto, restableciendo el catálogo a su estado original completo (cubre AC-4.5, INV-16).
- **FR-021**: Cuando se aplique un filtro manual por categoría, los platos pertenecientes a dicha sección que no cuenten con stock DEBEN continuar mostrándose dentro de los resultados filtrados pero en estado visual atenuado y deshabilitado (cubre AC-4.6, INV-17).
- **FR-022**: Si el comensal ingresa un valor de precio mínimo estrictamente mayor al precio máximo, el sistema DEBE desplegar una advertencia de validación y bloquear la aplicación del filtro hasta su corrección (cubre EC-014).
- **FR-023**: Las variaciones de inventario ocurridas en cocina DEBEN actualizar la disponibilidad de los platos en la grilla filtrada en tiempo real sin remover los filtros manuales activos (cubre EC-020).
- **FR-024**: El sistema DEBE preservar los filtros manuales activos (categorías y rango de precio) en la sesión local del comensal cuando este navega hacia otras vistas de la aplicación (como el perfil o el carrito) y regresa al catálogo del mismo establecimiento, restableciéndolos exclusivamente ante la acción de limpiar filtros o al cambiar de establecimiento (cubre INV-16).

---

### Key Entities

- **Plato**: Representa el producto gastronómico publicado y disponible para exploración del comensal. Atributos conceptuales observables: identificador, nombre comercial, descripción detallada, fotografía en alta resolución, precio final unitario con impuestos incluidos, categoría gastronómica asignada, indicador de disponibilidad según inventario, desglose de ingredientes y alérgenos vinculados.
- **Categoría Gastronómica**: Clasificación temática que agrupa platos dentro del menú del restaurante (por ejemplo: Entradas, Platos principales, Postres, Bebidas, Ensaladas). Sirve de criterio para los filtros manuales por categoría.
- **Ingrediente**: Componente o insumo culinario que integra la receta del plato. Posee existencia registrada en cocina y puede sufrir sustituciones o actualizaciones reflejadas en la información nutricional.
- **Alérgeno**: Advertencia de salud asociada a uno o más componentes del plato (por ejemplo: Gluten, Frutos secos, Lactosa), expuesta mediante distintivos y etiquetas visibles en la ficha del plato.
- **Configuración Regional de Moneda**: Conjunto de reglas comerciales del establecimiento que gobierna la presentación visual del precio: símbolo de moneda (por ejemplo `$`), carácter separador de miles y cantidad de cifras decimales admitidas.
- **Filtro Manual**: Conjunto de parámetros de selección aplicados voluntariamente por el comensal en la barra lateral del catálogo. Comprende una lista de categorías elegidas (unión lógica OR) y un rango monetario delimitado por precio mínimo y máximo (intersección lógica AND con las categorías).
- **Resultado Filtrado**: Subconjunto dinámico de platos del catálogo que satisfacen las condiciones impuestas por los filtros manuales activos, conservando la visualización deshabilitada de aquellos productos que carezcan de stock.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los clics realizados sobre una tarjeta de plato en el catálogo abre el panel superpuesto con su información completa (fotografía o texto adaptado, nombre comercial y descripción detallada).
- **SC-002**: El 100% de los cierres del panel (mediante botón de cierre o clic fuera del área del panel) retorna al catálogo conservando la posición de desplazamiento vertical previa sin desfasajes de pantalla.
- **SC-003**: El 100% de los platos sin fotografía registrada muestran su nombre y descripción en la cabecera sin desplegar íconos rotos ni dejar espacios vacíos asignados a imagen.
- **SC-004**: En conexiones lentas, el 100% de las aperturas de panel despliegan de inmediato el texto y el indicador de carga temporal, sin que la transferencia de la imagen bloquee la lectura de los datos del plato.
- **SC-005**: El 100% de los nombres y descripciones extensas se acomodan mediante barras de desplazamiento vertical interno sin alterar la estructura visual ni las dimensiones del panel.
- **SC-006**: El 100% de los platos sin información de ingredientes registrada exhiben de forma explícita el mensaje "Lista de componentes no disponible" (sin listas vacías ni errores genéricos).
- **SC-007**: El 100% de los precios presentados en el catálogo y en el panel aplican el formato de moneda local conforme al símbolo, separador de miles y decimales de la configuración regional vigente.
- **SC-008**: El 100% de los cambios de precio efectuados por la administración se reflejan en el catálogo y en el panel en la siguiente recarga o reapertura, sin exigir un nuevo inicio de sesión del comensal.
- **SC-009**: El 100% de los platos agotados exhiben su precio de venta final a título informativo y mantienen inactivos y deshabilitados los controles de cantidad y confirmación de compra.
- **SC-010**: El 100% de las variaciones de stock ocurridas mientras el panel está abierto se reflejan en la interfaz en un tiempo no mayor a 3 segundos sin exigir recarga manual de página.
- **SC-011**: El subtotal provisorio exhibido en el panel coincide exactamente con el producto del precio unitario por la cantidad seleccionada en el 100% de las modificaciones del selector numérico.
- **SC-012**: El 100% de las combinaciones de filtros manuales respetan estrictamente la semántica disyuntiva (OR) entre categorías de la misma dimensión y la semántica conjuntiva (AND) entre categorías y rangos de precio.
- **SC-013**: El 100% de las consultas filtradas que resulten en cero coincidencias despliegan el aviso de falta de resultados y habilitan el botón para limpiar filtros; al pulsarlo, el catálogo retorna al 100% de su estado original.
- **SC-014**: El 100% de los platos sin stock que pertenezcan a una categoría seleccionada permanecen visibles en la grilla filtrada bajo estado visual atenuado y deshabilitado.

---

## Assumptions

- `[SUPUESTO]` Se asume la existencia operativa previa del catálogo de platos, tarjetas de producto, panel de administración, carrito de compras de sesión, sistema de control de inventario de cocina, recetas, categorías y configuración regional de moneda, según lo establecido en las especificaciones 001 a 010.
- `[SUPUESTO]` El panel superpuesto de detalle de plato funciona como una extensión visual sobre el catálogo existente, preservando el estado de la vista de navegación subyacente.
- `[SUPUESTO]` El selector de cantidad de unidades del panel de detalle reutiliza el patrón conceptual y de interacción provisto por el módulo de carrito de compras (especificación 009).
- `[SUPUESTO]` Los precios registrados en el sistema ya contemplan la inclusión de impuestos en su valor de venta al público (especificación 007); la presente iteración se enfoca en su visualización prominente, formateo regional y cálculo del subtotal provisorio.
- `[SUPUESTO]` La configuración regional establece el símbolo monetario, el carácter separador de miles y la cantidad de decimales válidos para el establecimiento.
- `[SUPUESTO]` La sincronización en tiempo real ante cambios de stock, recetas y precios refiere a la actualización observable en la interfaz del comensal sin exigir recarga manual de página, respaldada por la infraestructura preexistente y con una latencia máxima admisible de 3 segundos (en coherencia con la especificación 008).
- `[SUPUESTO]` En el filtro manual por rango de precios, la evaluación es un intervalo cerrado e inclusivo en ambos límites: $\text{precio} \in [P_{\min}, P_{\max}]$.
- `[SUPUESTO]` Si el comensal ingresa un precio mínimo estrictamente superior al máximo ($P_{\min} > P_{\max}$), el sistema muestra una advertencia de validación y bloquea la aplicación del filtro hasta que los valores sean consistentes.
- `[SUPUESTO]` La preservación de la coordenada de desplazamiento vertical en el catálogo al cerrar el panel superpuesto aplica estrictamente dentro del mismo estado de disposición visual y tamaño de ventana (layout).
- `[SUPUESTO]` Al cerrar el panel de detalle sin agregar el producto al pedido, el subtotal provisorio calculado se descarta y el selector de cantidad se reinicia en 1 unidad para la siguiente apertura.

---

## Out of Scope

Quedan formal y explícitamente fuera del alcance de la presente especificación e iteración las siguientes funcionalidades y comportamientos:

- Edición de platos, recetas, categorías o precios desde el panel del comensal.
- Búsqueda por texto libre, autocompletado y búsqueda por voz (pertenecientes a otras historias y especificaciones).
- Filtros por valoración, popularidad, tiempo de preparación, distancia física o alérgenos excluyentes (excluir platos que contienen determinado alérgeno).
- Ordenamiento de resultados (por precio ascendente o descendente, por popularidad o alfabético).
- Recomendaciones personalizadas, platos similares o sección de sugerencias cruzadas ("también te puede gustar").
- Gestión de platos favoritos, historial de búsquedas y guardado de listas de comensales.
- Compartir fichas de platos en redes sociales o canales de mensajería externa.
- Carga de imágenes o archivos multimedia por parte del comensal.
- Reservas de mesa, pedidos programados diferidos y gestión logística de despacho por delivery.
- Carrito de compras integral, pasarela de pagos online, transacciones monetarias y flujo de checkout.
- Mecanismos de autenticación, registro de cuentas y asignación de permisos (cubiertos por las especificaciones 001, 002 y 003).
- Notificaciones push al dispositivo móvil, mensajes por correo electrónico o SMS.
- Soporte multi-idioma del catálogo gastronómico (más allá del idioma español neutro de la interfaz de usuario).
- Multi-moneda y conversión dinámica de tipos de cambio internacionales.
- Programas de fidelización comercial, acumulación de puntos, cupones de descuento y promociones temporales.
- Servicios de geolocalización, mapas interactivos y cálculo de distancias de entrega.
- Generación de reportes comerciales, analítica de clics y paneles de métricas para la administración.
- Modificación reactiva o vaciado del carrito de compras motivada por variaciones de stock o de precio posteriores a la incorporación del plato.

