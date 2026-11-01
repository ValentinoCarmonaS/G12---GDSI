# Spec: C-07 — Rechazo con feedback y formulario editable (administrador)

## Context
Tras implementar la auditoría de platos (C-05), el Administrador requiere la capacidad de rechazar un plato enviando un feedback obligatorio y editar el formulario de revisión de manera ágil.

## Requerimientos
- **FR-041**: El modal de rechazo debe incluir un campo de texto obligatorio (feedback) para el Chef.
- **FR-042**: El formulario de revisión de platos debe permitir la edición rápida de parámetros (precios/categoría) antes de realizar una decisión final.
- **FR-043**: Toda acción de rechazo debe persistir el feedback en el historial (`DishReviewHistory`).

## Plan
1. Crear documentación y tareas para C-07.
2. Implementar el campo de feedback obligatorio en el componente de acciones.
3. Habilitar campos editables en la ficha del plato.
4. Validar la persistencia de datos en backend.
