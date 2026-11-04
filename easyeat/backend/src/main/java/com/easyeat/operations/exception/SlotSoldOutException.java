package com.easyeat.operations.exception;

/** INV-2 / EC-016: the slot reached its capacity, also when it was lost to a concurrent approval. */
public class SlotSoldOutException extends RuntimeException {

    public SlotSoldOutException(int maxCapacity) {
        super(String.format("La franja seleccionada ha alcanzado su capacidad máxima permitida (%d pedidos).", maxCapacity));
    }
}
