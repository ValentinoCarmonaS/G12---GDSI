# Especificación funcional: seguimiento en vivo y demora granular (+5)

**Identificador de especificación**: `021-live-tracking-delay-adjustment`  
**Rama de trabajo**: `021-live-tracking-delay-adjustment`  
**Fecha de creación**: 15/11/2026  
**Estado**: Aprobado  
**Entrada**: Requerimientos funcionales derivados de las historias de usuario `COM-8.1.1` y `COM-7.3.2 (BIS)`.

---

## Contexto y objetivo

En el modelo de servicio gastronómico de EasyEat, la visibilidad en tiempo real del progreso de un pedido constituye el factor determinante para brindar certidumbre al comensal, mitigar su ansiedad de espera y permitirle optimizar sus tiempos antes de acercarse a retirar su comida por mostrador.

La presente especificación, correspondiente a la Spec 021, establece el enlace funcional observable entre la producción en cocina y la experiencia visual del comensal, montándose de forma natural sobre la comanda en preparación activa ya existente en el KDS de cocina (originada en la Spec 019 y coordinada en la Spec 020) y enriqueciendo la pantalla de seguimiento del cliente sin acoplarse a la verificación de despacho en mostrador.

El objetivo funcional de esta especificación es resolver dos capacidades complementarias:
1. **Seguimiento adaptativo en vivo por etapas operativas (`COM-8.1.1`)**: Presentar al comensal una interfaz limpia y contextualizada que evoluciona de manera determinista según el estado real de la orden en cocina: exhibiendo la posición numérica en la cola de espera previa ($N$ pedidos por delante bajo criterio FIFO) mientras esté en cola; activando automáticamente la cuenta regresiva en minutos y la barra de progreso porcentual únicamente cuando la cocina inicia la preparación; y conmutando al aviso destacado de retiro con código numérico de seguridad cuando los alimentos están listos.
2. **Ajuste granular acumulativo de demoras de cocina (`COM-7.3.2 (BIS)`)**: Proveer al personal de cocina un control táctil directo y ágil mediante un botón rotulado "+5" en la comanda activa para añadir demoras operativas puntuales (+5 minutos por pulsación de forma acumulativa) sobre órdenes específicas que experimenten imprevistos culinarios, reflejando dicho incremento de manera reactiva y transparente en el temporizador del comensal durante su siguiente sincronización silenciosa, y purgando tanto el tiempo estimado como cualquier indicador de demora en el instante en que cocina finaliza la comanda.

Esta especificación es 100% agnóstica a la tecnología, no prescribe modelos de datos relacionales, contratos de red, endpoints ni librerías de implementación, y describe con precisión matemática y funcional el comportamiento observable del sistema.

---

## Clarifications

### Session 2026-11-15

- Q: ¿Cómo debe computarse y expresarse la posición en cola $N$ cuando una orden se encuentra en estado "en cola"? → A: La posición $N$ representa el recuento determinista de órdenes previas calculadas bajo la regla FIFO unificada de la cola de cocina (conforme a `CHF-3.1.2`, conviviendo órdenes inmediatas y programadas). Se exhibe al comensal bajo la leyenda explícita: "Tenés $N$ pedidos por delante". Cuando no existen órdenes previas en cola ($N = 0$), se visualiza de forma determinista: "Tenés 0 pedidos por delante", indicando que la orden es la próxima en turno para ser iniciada.
- Q: ¿Qué visibilidad tienen el temporizador de minutos restantes y la barra de progreso mientras la orden está en estado "en cola"? → A: Permanecen estrictamente ocultos. Las métricas temporales dinámicas y la barra porcentual se activan única y exclusivamente a partir del instante en que la orden pasa a estado "en preparación" (cuando el chef pulsa "EMPEZAR" en cocina), evitando generar expectativas temporales imprecisas mientras el pedido aguarda en cola pasiva.
- Q: ¿Cuál es el comportamiento del botón "+5" en la comanda de cocina y cómo se refleja en el comensal? → A: El botón "+5" se ubica en la comanda en preparación activa de la cocina; cada pulsación suma de manera atómica y acumulativa exactamente 5 minutos a la demora específica de esa orden en particular ($1 \text{ pulsación} = +5 \text{ min}$, $2 \text{ pulsaciones} = +10 \text{ min}$). Dicho incremento se refleja de forma automática en la pantalla de seguimiento del comensal en la siguiente sincronización silenciosa, aumentando su tiempo restante de $M$ a $M + 5$ minutos y recalculando proporcionalmente la barra de progreso.
- Q: ¿Cuál es la cadencia y modalidad de sincronización de la pantalla de seguimiento del comensal? → A: Sincronización automática y silenciosa con un intervalo continuo de entre 5 y 8 segundos, sin requerir recargas manuales de página, sin parpadeos visuales y preservando la estabilidad de los componentes en pantalla.
- Q: ¿Cómo debe comportarse el temporizador dinámico si la preparación física insume más tiempo del previsto originalmente y la cuenta regresiva alcanza cero minutos antes de que cocina presione "LISTO"? → A: El temporizador no desciende por debajo de 1 minuto ni exhibe valores negativos o mensajes erráticos; se mantiene congelado en "1 min restante" y la barra de progreso no supera el 92% hasta que el personal de cocina marque efectivamente la orden como lista (garantizando consistencia con la regla operativa establecida en la Spec 016).
- Q: ¿Qué sucede con las demoras acumuladas y el tiempo estimado cuando cocina finaliza la preparación pulsando "LISTO"? → A: Al transicionar la orden a estado "listo para retirar", el tiempo estimado, el temporizador dinámico, la barra de progreso y cualquier indicador de demora se purgan por completo. La pantalla conmuta al mensaje destacado de éxito en color verde "¡Tu pedido está listo para retirar!" y exhibe de forma prominente el código numérico de seguridad para recitar en mostrador.
- Q: ¿Cómo responde la pantalla de seguimiento del comensal ante una desconexión momentánea de red durante el ciclo de actualización silenciosa? → A: La pantalla conserva en todo momento el último estado, tiempo y barra visual previamente confirmados sin bloquear la vista ni presentar carteles intrusivos de falla; al restablecerse la conectividad, el siguiente ciclo silencioso absorbe y renderiza suavemente el estado actual del servidor.

---

## Personas

1. **Comensal que confirmó su pedido y aguarda su preparación**: Cliente que ha finalizado el pago de su orden y consulta la pantalla de seguimiento para conocer su ubicación en la cola de producción y saber con antelación cuándo la cocina comenzará a elaborar sus platos.
2. **Comensal con un pedido en preparación**: Cliente cuya orden ya está siendo elaborada en los fuegos de la cocina y que monitorea el temporizador dinámico de minutos restantes y la barra de avance para planificar su desplazamiento físico hacia el local o acercarse a ventanilla.
3. **Chef que opera la comanda en preparación activa en cocina**: Personal gastronómico que elabora la comanda en la estación de cocción y que, ante eventualidades técnicas o tiempos adicionales de cocción, acciona el control táctil "+5" para informar demoras de manera granular y acumulativa sin interrumpir su ritmo operativo.

---

## Historias de usuario y escenarios de aceptación

### Historia de usuario 1 — Visualización base del seguimiento en vivo por estados y posición en cola (Prioridad: P1)

**Identificador de referencia**: `COM-8.1.1`  
**Persona**: Comensal que confirmó su pedido y aguarda su preparación / Comensal con un pedido en preparación.

Como comensal que confirmó su pedido y aguarda su preparación, quiero ver en la pantalla de seguimiento cuántos pedidos tengo por delante mientras esté en cola, y que al comenzar a cocinarse se activen el temporizador y la barra de progreso, para saber exactamente en qué etapa se encuentra mi comida y cuándo acercarme al mostrador.

**Por qué tiene prioridad P1**: Constituye la columna vertebral del seguimiento para el cliente. Sin la visualización de la posición en cola y la conmutación clara de etapas entre espera, preparación y retiro, el comensal desconoce el estado de su orden, saturando el mostrador con consultas prematuras o sufriendo incertidumbre.

**Prueba independiente**: Se puede validar creando una orden confirmada en cola: la pantalla exhibe la insignia "En cola en cocina", el número de pedido y la leyenda con el número de órdenes previas, manteniendo ocultos el temporizador y la barra. Al pulsar "EMPEZAR" en cocina, la pantalla conmuta silenciosamente en 5 a 8 segundos a "Cocinando / En preparación", removiendo la leyenda de pedidos por delante y activando el temporizador y la barra. Al pulsar "LISTO", la pantalla conmuta al aviso verde con el código de seguridad.

**Escenarios de aceptación**:

1. **AC-1 (Posición en la cola durante estado "en cola")**:
   - **Dado** que la orden del comensal se encuentra en estado "en cola",
   - **Cuando** el comensal ingresa o consulta la pantalla de seguimiento,
   - **Entonces** visualiza su número de pedido, la insignia informativa "En cola en cocina" y la leyenda explícita "Tenés $N$ pedidos por delante", donde $N$ es el recuento FIFO de órdenes previas de la cola de cocina, manteniéndose ocultos el temporizador dinámico y la barra de progreso.

2. **AC-2 (Transición reactiva a "en preparación" y activación de métricas)**:
   - **Dado** que el chef pulsa el botón "EMPEZAR" en la comanda de cocina y la orden pasa a estado "en preparación",
   - **Cuando** el cliente del comensal actualiza el seguimiento de forma automática y silenciosa en su ciclo de 5 a 8 segundos,
   - **Entonces** desaparece de la pantalla la leyenda de pedidos por delante, la insignia de estado cambia a "Cocinando / En preparación" acompañada de un icono dinámico, y se activan visualmente el temporizador dinámico de minutos restantes y la barra de progreso porcentual.

3. **AC-3 (Transición a "listo para retirar" y exhibición de código de seguridad)**:
   - **Dado** que la cocina finaliza la preparación física de la comanda y presiona "LISTO",
   - **Cuando** el estado de la orden cambia a "listo para retirar",
   - **Entonces** el temporizador dinámico y la barra de progreso desaparecen por completo, y la pantalla destaca en color verde de éxito el mensaje prominente "¡Tu pedido está listo para retirar!", exhibiendo el código numérico de seguridad para recitar en mostrador.

---

### Historia de usuario 2 — Tiempo estimado en vivo y ajuste granular de demora (+5) (Prioridad: P2)

**Identificador de referencia**: `COM-7.3.2 (BIS)`  
**Persona**: Comensal con un pedido en preparación / Chef que opera la comanda en preparación activa en cocina.

Como comensal con un pedido en preparación, quiero que mi temporizador de entrega se actualice de forma reactiva si la cocina necesita 5 minutos adicionales, para planificar mis tiempos con información real y no sufrir demoras inesperadas en ventanilla.

**Por qué tiene prioridad P2**: Agrega flexibilidad operacional a la cocina y certidumbre fina al comensal ante imprevistos puntuales de producción, evitando demoras sorpresivas y sincronizando las expectativas de tiempo entre ambas partes.

**Prueba independiente**: Se puede comprobar visualizando una comanda activa en la Columna 2 de cocina y pulsando el botón "+5": el sistema registra el incremento acumulativo (+5 min por pulsación). En la pantalla de seguimiento del comensal asociada, que indicaba $M$ minutos restantes, el temporizador refleja $M + 5$ minutos en la siguiente sincronización automática de 5 a 8 segundos, recalculando la barra porcentual. Al pulsar "LISTO", la demora se purga y la pantalla pasa al estado de entrega.

**Escenarios de aceptación**:

1. **AC-1 (Acción de demora granular acumulativa "+5" en cocina)**:
   - **Dado** un ticket de comanda en estado "en preparación" en la comanda activa de cocina,
   - **Cuando** el chef pulsa el control táctil "+5",
   - **Entonces** el sistema incrementa en exactamente 5 minutos la demora específica asignada a esa orden de forma acumulativa y determinista ($1 \text{ pulsación} = +5 \text{ min}$, $2 \text{ pulsaciones} = +10 \text{ min}$).

2. **AC-2 (Reflejo reactivo en el temporizador y barra de progreso del comensal)**:
   - **Dado** un comensal cuyo seguimiento indicaba $M$ minutos restantes de cocción en estado "en preparación",
   - **Cuando** la cocina aplica una acción de "+5" sobre su ticket,
   - **Entonces** en la siguiente sincronización automática y silenciosa del seguimiento (cada 5 a 8 segundos), el temporizador del comensal aumenta automáticamente a $M + 5$ minutos y la barra de progreso porcentual recalcula proporcionalmente su porcentaje de avance de forma fluida y sin recargar la pantalla.

3. **AC-3 (Purga de demora e indicadores temporales al finalizar la preparación)**:
   - **Dado** un pedido que acumuló minutos de demora específica por pulsaciones previas de "+5",
   - **Cuando** la cocina presiona el botón "LISTO",
   - **Entonces** el tiempo estimado, el temporizador dinámico y cualquier indicador de demora se purgan de inmediato, conmutando la pantalla del comensal al aviso destacado de entrega en mostrador.

---

## Casos borde (Edge cases)

1. **Orden en cola con cero pedidos por delante ($N = 0$)**:  
   Cuando una orden se encuentra en estado "en cola" pero no existen órdenes previas sin iniciar que le antecedan en la cola FIFO, el sistema computa $N = 0$ y renderiza explícitamente: "Tenés 0 pedidos por delante". El temporizador dinámico y la barra de progreso permanecen estrictamente ocultos hasta que cocina inicie la preparación.

2. **Orden en cola con un volumen elevado de pedidos por delante ($N \ge 10$)**:  
   Cuando la cola de cocina experimenta alta saturación (por ejemplo, $N = 15$), el sistema formatea el valor entero sin truncamiento ni desbordamiento visual del contenedor, manteniendo la coherencia con el orden cronológico estricto de recepción.

3. **Transición de "en cola" a "en preparación" entre dos actualizaciones automáticas**:  
   Si el chef pulsa "EMPEZAR" mientras el comensal tiene la pantalla abierta, el siguiente ciclo silencioso (en un lapso no mayor a 8 segundos) realiza una conmutación limpia: remueve el mensaje de pedidos por delante, actualiza la insignia a "Cocinando / En preparación" y despliega el temporizador con la estimación calculada y la barra porcentual, sin requerir intervención manual del usuario.

4. **Varias pulsaciones consecutivas y rápidas de "+5" sobre el mismo ticket en cocina**:  
   Si el chef pulsa el control "+5" en rápida sucesión (por ejemplo, tres veces consecutivas), el sistema consolida los incrementos de manera determinista y acumulativa sumando exactamente 15 minutos ($3 \times 5 \text{ min}$). En el siguiente ciclo de actualización del comensal, el temporizador absorbe íntegramente el incremento total ($M + 15 \text{ min}$) ajustando la barra porcentual correspondiente.

5. **Acción de "+5" aplicada mientras el comensal visualiza activamente la pantalla**:  
   Cuando la cocina incrementa la demora y el comensal ya se encuentra visualizando la cuenta regresiva, la transición de $M$ a $M + 5$ minutos ocurre de manera no intrusiva en la siguiente sincronización silenciosa (5-8 s), sin refrescos de página, sin pérdidas de foco ni alteraciones abruptas de la vista.

6. **Acción de "+5" aplicada y orden marcada como "LISTO" antes de la siguiente actualización del comensal**:  
   Si la cocina aplica "+5" y casi de inmediato pulsa "LISTO" dentro del mismo intervalo de actualización del comensal, la pantalla del cliente procesa de forma directa el estado terminal de la secuencia: no presenta un incremento efímero de minutos, sino que pasa directamente al estado "listo para retirar" en color verde, purgando cualquier cómputo de demora previo.

7. **Corte momentáneo de red durante el ciclo de actualización del seguimiento**:  
   Ante una interrupción transitoria de conectividad durante el sondeo silencioso, la interfaz del comensal retiene de manera estable el último estado, tiempo y progreso conocidos. No se muestran mensajes de error disruptivos ni pantallas en blanco. Al recuperar la señal de red, el sondeo subsiguiente reanuda la sincronización de datos con el servidor de manera transparente.

8. **Actualización automática repetida durante períodos prolongados sin recarga manual**:  
   La ejecución continua del ciclo de sincronización silenciosa (cada 5 a 8 segundos) no debe provocar degradación de la interfaz, duplicación de elementos de texto, fugas de rendimiento ni reinicios espurios de contadores ya presentados.

9. **Temporizador en cero minutos restantes al prolongarse la cocción física**:  
   Si los minutos transcurridos de cocción alcanzan el tiempo estimado total antes de que el chef pulse "LISTO", el temporizador dinámico no desciende por debajo de "1 min restante" ni adopta valores negativos, y la barra de progreso no supera el 92%, manteniéndose en dicho estado hasta que la cocina accione formalmente la finalización del pedido.

10. **Comportamiento en los extremos de la barra de progreso (0% y 100%)**:  
    Al iniciar la preparación física ("EMPEZAR"), la barra de progreso porcentual se activa marcando su valor base inicial (mayor o igual a 0% según los minutos transcurridos desde el inicio). La barra no alcanza el 100% de plenitud visual mientras la orden permanezca en preparación activa; el 100% queda reservado para la conmutación al estado "listo para retirar", momento en el cual la barra se purga junto con el temporizador para dar lugar al panel verde de éxito.

11. **Orden que transiciona a "listo para retirar" habiendo acumulado demora previa**:  
    Si un pedido acumuló una o más adiciones de "+5", al recibir la orden de finalización ("LISTO") la pantalla del comensal purga de manera irreversible todos los minutos acumulados y el tiempo estimado, garantizando que el estado "listo para retirar" se despliegue limpio y sin residuos informativos de la demora que estuvo vigente.

---

## Requerimientos funcionales

### Identificación y estado en cola (COM-8.1.1)

- **RF-001**: El sistema DEBE presentar en la pantalla de seguimiento del comensal el identificador correlativo de pedido (`#ticketNumber`) correspondiente a la orden confirmada.
- **RF-002**: Mientras la orden permanezca en estado "en cola", el sistema DEBE exhibir de manera visible la insignia de estado "En cola en cocina".
- **RF-003**: Mientras la orden permanezca en estado "en cola", el sistema DEBE calcular y exhibir de forma explícita la posición en cola mediante la leyenda: "Tenés $N$ pedidos por delante", donde $N$ es el recuento determinista de órdenes pendientes previas bajo criterio FIFO en cocina.
- **RF-004**: Cuando $N = 0$ (no existen órdenes previas en cola), el sistema DEBE exhibir exactamente la leyenda: "Tenés 0 pedidos por delante".
- **RF-005**: Mientras la orden se encuentre en estado "en cola", el temporizador dinámico de minutos restantes y la barra de progreso porcentual DEBEN permanecer completamente ocultos en la interfaz.

### Transición a preparación y activación de métricas (COM-8.1.1)

- **RF-006**: Al transicionar la orden a estado "en preparación" (disparado por la acción "EMPEZAR" del chef en cocina), el sistema DEBE remover de manera inmediata y definitiva la leyenda de pedidos por delante de la pantalla de seguimiento.
- **RF-007**: En estado "en preparación", el sistema DEBE actualizar la insignia de estado a "Cocinando / En preparación", incorporando un indicador gráfico dinámico representativo de actividad culinaria.
- **RF-008**: En estado "en preparación", el sistema DEBE activar y exhibir de forma visible el temporizador dinámico de minutos restantes estimados hasta la entrega.
- **RF-009**: En estado "en preparación", el sistema DEBE activar y exhibir la barra de progreso porcentual representativa del avance de la preparación.
- **RF-010**: El temporizador dinámico DEBE calcular los minutos restantes descontando los minutos transcurridos de cocción al tiempo total estimado asignado al pedido.
- **RF-011**: Mientras la orden continúe en estado "en preparación", el minutero del temporizador no DEBE descender por debajo de 1 minuto restante ni la barra de progreso DEBE superar el 92%, aun cuando el tiempo transcurrido supere la estimación inicial, hasta que cocina confirme la finalización.

### Ajuste granular acumulativo de demoras de cocina (+5) (COM-7.3.2 BIS)

- **RF-012**: El sistema DEBE proveer un control táctil rotulado "+5" en la comanda en preparación activa de la cocina (Columna 2 del KDS).
- **RF-013**: Cada pulsación sobre el control "+5" DEBE incrementar de forma inmediata, atómica y acumulativa en exactamente 5 minutos la demora específica asignada a esa orden ($1 \text{ pulsación} = +5 \text{ min}$, $2 \text{ pulsaciones} = +10 \text{ min}$).
- **RF-014**: El incremento de demora específica DEBE sumarse al tiempo total estimado de cocción de la orden sin alterar los tiempos ni demoras de otros pedidos concurrentes del establecimiento.
- **RF-015**: Al aplicarse una demora de "+5", el temporizador dinámico en la pantalla de seguimiento del comensal DEBE aumentar automáticamente su cuenta en 5 minutos en el ciclo de sincronización subsiguiente ($M \to M + 5 \text{ minutos}$).
- **RF-016**: Al reflejarse la demora añadida, la barra de progreso porcentual en la pantalla del comensal DEBE recalcular proporcionalmente su porcentaje de avance de acuerdo al nuevo horizonte temporal ampliado.

### Transición a listo para retirar y purga de indicadores (COM-8.1.1 y COM-7.3.2 BIS)

- **RF-017**: Cuando el personal de cocina acciona el control "LISTO", el sistema DEBE transicionar la orden al estado "listo para retirar".
- **RF-018**: Al pasar a estado "listo para retirar", el sistema DEBE remover y purgar definitivamente de la pantalla del comensal el temporizador dinámico, la barra de progreso y cualquier indicador o registro de demora acumulada.
- **RF-019**: En estado "listo para retirar", la pantalla de seguimiento DEBE conmutar a un contenedor destacado en color verde de éxito que exhiba el mensaje prominente: "¡Tu pedido está listo para retirar!".
- **RF-020**: En estado "listo para retirar", la pantalla DEBE exhibir claramente el código numérico de seguridad de 4 dígitos correspondiente al pedido para ser recitado verbalmente por el comensal en el mostrador.

### Ciclo de actualización y estabilidad visual

- **RF-021**: La pantalla de seguimiento del comensal DEBE sincronizarse de forma automática y silenciosa con el estado del sistema en un intervalo periódico regular de entre 5 y 8 segundos, sin requerir recarga manual del navegador o aplicación.
- **RF-022**: La sincronización periódica DEBE ejecutarse de forma no intrusiva, sin saltos bruscos de estructura (layout shifts), sin parpadeos de interfaz y sin desestructurar componentes abiertos.
- **RF-023**: El sistema DEBE garantizar consistencia e idempotencia visual ante consultas repetidas: los datos idénticos recibidos en sucesivas actualizaciones no deben provocar reinicios ni duplicaciones visuales.
- **RF-024**: Ante una falla transitoria de red durante el ciclo de sincronización, la interfaz DEBE preservar de forma continua la última información válida renderizada y reintentar de manera silenciosa en el siguiente intervalo sin bloquear la interacción del comensal.

---

## Entidades clave

```
┌────────────────────────────────────────────────────────────────────────┐
│                                 ORDEN                                  │
├────────────────────────────────────────────────────────────────────────┤
│ - Identificador único de orden                                         │
│ - Número correlativo de ticket (#ticketNumber)                         │
│ - Código numérico de seguridad (4 dígitos)                             │
│ - Estado operativo: EN COLA | EN PREPARACIÓN | LISTO PARA RETIRAR      │
│ - Minutos de demora específica acumulada (+5 por pulsación)            │
│ - Marca temporal de inicio de preparación                              │
│ - Marca temporal de finalización de preparación                        │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
                                     │ Se refleja en tiempo real en
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   SEGUIMIENTO EN VIVO DEL COMENSAL                     │
├────────────────────────────────────────────────────────────────────────┤
│ - Número de ticket visible                                             │
│ - Insignia de estado contextual:                                       │
│     * "En cola en cocina"                                              │
│     * "Cocinando / En preparación" (con icono dinámico)                │
│     * "¡Tu pedido está listo para retirar!" (en verde de éxito)        │
│ - Posición en cola FIFO ("Tenés N pedidos por delante") [Sólo en cola] │
│ - Temporizador dinámico de minutos restantes [Sólo en preparación]     │
│ - Barra de progreso porcentual [Sólo en preparación]                   │
│ - Código numérico de seguridad [Exhibido en listo para retirar]        │
└────────────────────────────────────────────────────────────────────────┘
```

- **Orden**: Pedido gastronómico confirmado y pagado que transita por el ciclo de producción culinaria. Contiene su número correlativo de ticket (#ticketNumber), el código numérico de seguridad de 4 dígitos para retiro, su estado operativo unificado, y el registro de la demora específica acumulada en minutos.
- **Comanda en preparación activa (KDS)**: Representación operativa del pedido en la pantalla táctil de cocina durante su cocción activa, portadora del botón táctil "+5" para la suma granular de demora y del botón "LISTO" para la finalización de bandeja completa.
- **Seguimiento en vivo del comensal**: Vista interactiva del comensal que consume el estado del pedido y adapta su renderizado: en cola exhibe la leyenda FIFO de pedidos por delante; en preparación activa el minutero y la barra porcentual reactivos a demoras; y en listo para retirar exhibe el cartel verde de éxito y el código numérico de seguridad.
- **Ajuste granular de demora**: Incremento cuantitativo discreto de tiempo (+5 minutos) aplicado exclusivamente a una comanda en preparación activa, que amplía su tiempo estimado de entrega y se propaga a la vista del cliente.

---

## Invariantes y reglas deterministas de negocio

1. **Invariante de cálculo determinista de posición FIFO ($N$)**:  
   Mientras una orden se encuentre en estado "en cola", el valor $N$ exhibido en la leyenda "Tenés $N$ pedidos por delante" DEBE coincidir de forma estricta y reproducible con la cantidad de comandas pendientes que le anteceden en la cola cronológica de cocina conforme a la regla FIFO de `CHF-3.1.2`. Cuando la orden se encuentre al tope de la cola para ser la siguiente en iniciar, $N$ DEBE ser exactamente 0.

2. **Invariante de exclusión visual de temporizador y barra en cola**:  
   El temporizador dinámico de minutos restantes y la barra de progreso porcentual NO DEBEN mostrarse bajo ninguna circunstancia mientras la orden permanezca en estado "en cola". Su renderizado está reservado exclusivamente para órdenes que hayan transicionado al estado "en preparación".

3. **Invariante de transición unívoca de insignias de estado**:  
   La pantalla de seguimiento DEBE exhibir en todo momento exactamente una única insignia de estado principal, transitando de manera secuencial e irreversible a lo largo de las etapas:
   $$\text{"En cola en cocina"} \longrightarrow \text{"Cocinando / En preparación"} \longrightarrow \text{"¡Tu pedido está listo para retirar!"}$$

4. **Invariante de activación y absorción de demora granular (+5)**:  
   Cada activación del control "+5" en la comanda activa de cocina suma de forma inmediata, acumulativa y estrictamente lineal exactamente 5 minutos a la demora específica de la orden intervenida. Dicha demora incrementa el tiempo total estimado y se traduce en una suma directa de 5 minutos al valor de minutos restantes exhibido en la pantalla del comensal en la siguiente actualización ($M \to M + 5$).

5. **Invariante de recálculo proporcional de la barra de progreso**:  
   Al alterarse el tiempo estimado total por una adición de demora "+5", la barra de progreso porcentual DEBE recalcular su porcentaje de avance como la proporción de minutos transcurridos sobre el nuevo tiempo total ampliado, preservando una progresión visual suave y coherente sin reinicios espurios.

6. **Invariante de cota inferior del minutero y cota superior de barra en preparación**:  
   Mientras una orden permanezca en estado "en preparación", el temporizador dinámico no DEBE descender nunca por debajo de "1 min restante" ni exhibir valores negativos o iguales a cero, y la barra de progreso porcentual no DEBE superar el 92% de plenitud, aun si el tiempo transcurrido real excede el estimado previsto, hasta tanto cocina presione "LISTO".

7. **Invariante de purga integral al marcar "LISTO"**:  
   En el instante en que la orden pasa a estado "listo para retirar" por acción del botón "LISTO" en cocina, el temporizador dinámico, la barra de progreso porcentual y cualquier residuo o cómputo de demora acumulada DEBEN purgarse por completo. La pantalla DEBE conmutar al panel verde de éxito destacando el código numérico de seguridad.

8. **Invariante de cadencia determinista de sincronización silenciosa**:  
   La pantalla de seguimiento del comensal DEBE efectuar su ciclo de actualización automática y silenciosa dentro de una ventana temporal estricta de entre 5 y 8 segundos de periodicidad regular, sin requerir recarga manual de la página ni acción alguna del usuario.

9. **Invariante de idempotencia y estabilidad ante sincronizaciones repetidas**:  
   Sucesivas sincronizaciones silenciosas que devuelvan el mismo estado operativo, posición o tiempo restante no DEBEN producir efectos colaterales observables, parpadeos de interfaz, reinicios de animaciones en curso ni duplicaciones de texto o componentes en la pantalla del comensal.

10. **Invariante de desacoplamiento de entrega y mostrador**:  
    La pantalla de seguimiento del comensal en estado "listo para retirar" se limita exclusivamente a exhibir el aviso de retiro y el código numérico de seguridad para su recitado verbal en ventanilla; cualquier interacción posterior de cotejo físico de ítems, validación de PIN en terminal de mostrador o cierre de orden corresponde a fases operativas posteriores y no interviene en esta especificación.

---

## Supuestos

- El comensal accede a la pantalla de seguimiento a través de un navegador web estándar en dispositivo móvil o de escritorio con soporte para ejecución de scripts y renderizado visual dinámico.
- La pantalla de cocina (KDS) y el cliente del comensal operan sobre un reloj de sistema coordinado y sincronizado, permitiendo que el cómputo de minutos transcurridos desde el inicio de preparación sea coherente en ambos extremos.
- Toda orden que ingresa a la cola de cocina posee previamente asignado un identificador correlativo de ticket (#ticketNumber) y un código numérico de seguridad de 4 dígitos generado al confirmarse el pago.
- La acción de pulsar "EMPEZAR" y la acción de pulsar "LISTO" en la pantalla de cocina forman parte del ciclo de vida operativo formalizado en las Specs 019 y 020, sobre las cuales la presente especificación se enlaza limpiamente.

---

## Fuera de alcance

- Verificación efectiva del código numérico de seguridad o validación oral del PIN en la ventanilla de mostrador (asignada formalmente a la especificación `022-pin-antifraud-delivery-completion`).
- Confirmación de entrega física, cierre definitivo de la orden, archivado del ticket o liberación de cupo/mesa (asignados formalmente a la especificación `022`).
- Administración de la pantalla de mostrador, despliegue de tarjetas de pedidos listos o cotejo físico de platos por el personal de sala (cubiertos en la especificación `020-counter-dispatch-ready-orders`).
- Mecanismo de reducción manual de demora, botón de resta de minutos o reseteo a cero de la demora acumulada en la comanda de cocina.
- Canales de notificación externos ajenos a la pantalla de seguimiento en vivo (tales como notificaciones push para móviles, alertas por SMS, correos electrónicos adicionales o mensajes por WhatsApp).
- Impresión física de tickets o comprobantes en papel en cocina o mostrador.
- Procesamiento de cobros, transacciones de pago, devoluciones, propinas o conciliaciones financieras (resueltos en Specs 014, 015 y 016).
- Modificación, alta, baja o edición de platos, comentarios o especificaciones gastronómicas sobre el pedido una vez confirmado.
- Especificación de detalles técnicos de implementación (tales como nombres de tablas, esquemas de bases de datos, endpoints de red, componentes de código, clases CSS, hooks o librerías).

---

## Criterios de éxito medibles (Success criteria)

- **SC-001**: El 100% de los comensales con órdenes en estado "en cola" visualizan de forma explícita su número de ticket, la insignia "En cola en cocina" y la leyenda "Tenés $N$ pedidos por delante", manteniendo el temporizador y la barra de progreso ocultos.
- **SC-002**: El 100% de las órdenes en cola con cero pedidos precedentes ($N = 0$) exhiben de manera exacta y determinista la leyenda "Tenés 0 pedidos por delante".
- **SC-003**: En el 100% de las transiciones operativas de "en cola" a "en preparación" iniciadas por cocina, la pantalla del comensal conmuta silenciosamente en un tiempo máximo medible de 8 segundos, removiendo la leyenda de pedidos por delante, actualizando la insignia a "Cocinando / En preparación" y activando el temporizador y la barra porcentual.
- **SC-004**: Cada pulsación del control táctil "+5" en la comanda activa de cocina añade invariablemente 5 minutos exactos a la demora de la orden ($1 \text{ pulsación} = +5 \text{ min}$, $2 \text{ pulsaciones} = +10 \text{ min}$), reflejándose en un incremento directo de $M \to M + 5$ minutos en el temporizador del comensal en la sincronización siguiente (5 a 8 segundos).
- **SC-005**: El 100% de las órdenes marcadas como "LISTO" por la cocina purgan de inmediato el temporizador, la barra porcentual y las demoras acumuladas, presentando en color verde de éxito el mensaje "¡Tu pedido está listo para retirar!" junto con el código numérico de seguridad.
- **SC-006**: Registro de 0 decrementos del minutero por debajo de 1 minuto restante y 0 lecturas de avance superiores al 92% en la barra de progreso mientras la orden continúe en estado "en preparación".
- **SC-007**: El 100% de las actualizaciones de seguimiento se ejecutan de manera automática y silenciosa cada 5 a 8 segundos sin requerir recargas de página ni presentar saltos visuales de diseño.
- **SC-008**: Cero pérdidas de estado o pantallas en blanco ante cortes transitorios de red durante los ciclos de sincronización del comensal.

---

## Preguntas abiertas

No se identifican preguntas abiertas ni ambigüedades pendientes. Las decisiones de diseño funcional, la anatomía de los estados de seguimiento, la dinámica acumulativa del botón "+5", la regla de piso del minutero y las invariantes de visualización fueron validadas y alineadas de manera exhaustiva con las especificaciones previas del sistema.

---

## Lista de verificación de cobertura

| Historia de usuario | Título de la historia | Prioridad | Secciones y criterios cubiertos |
| :--- | :--- | :---: | :--- |
| `COM-8.1.1` | Indicador visual de estados y posición en la cola | P1 | Historias de usuario (HU-1: AC-1, AC-2, AC-3), Requerimientos funcionales (RF-001 a RF-011, RF-017 a RF-024), Invariantes (1, 2, 3, 6, 7, 8, 9, 10), Casos borde (1, 2, 3, 7, 8, 9, 10), Criterios de éxito (SC-001, SC-002, SC-003, SC-005, SC-006, SC-007, SC-008) |
| `COM-7.3.2 (BIS)` | Tiempo estimado en vivo y ajuste granular de demora (+5) | P2 | Historias de usuario (HU-2: AC-1, AC-2, AC-3), Requerimientos funcionales (RF-012 a RF-016, RF-018), Invariantes (4, 5, 7), Casos borde (4, 5, 6, 11), Criterios de éxito (SC-004, SC-005) |
