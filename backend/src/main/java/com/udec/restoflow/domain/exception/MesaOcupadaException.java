package com.udec.restoflow.domain.exception;

/**
 * RN: no se registra un pedido en una mesa que ya tiene uno abierto. El API responde 409 con un mensaje que dice
 * qué mesero la atiende y cómo se libera. El número del pedido no va en el mensaje (solo lo ve el administrador);
 * como una mesa tiene un solo pedido abierto, la mesa basta para identificarlo.
 */
// Hereda de OperacionNoPermitidaException para que el API responda 409 sin un manejador propio. SonarQube cuenta
// también Throwable, Exception y RuntimeException, por eso marca más de 5 niveles (java:S110).
public class MesaOcupadaException extends OperacionNoPermitidaException {   // NOSONAR java:S110

    private final int numeroMesa;
    private final Long pedidoId;

    public MesaOcupadaException(int numeroMesa, Long pedidoId, String meseroNombre) {
        super("La mesa " + numeroMesa + " está ocupada" + (meseroNombre == null ? "" : " (la atiende " + meseroNombre + ")")
                + ". Se libera cuando su pedido se cobre o se cancele, o cuando se cambie a otra mesa.");
        this.numeroMesa = numeroMesa;
        this.pedidoId = pedidoId;
    }

    public int getNumeroMesa() {
        return numeroMesa;
    }

    public Long getPedidoId() {
        return pedidoId;
    }
}
