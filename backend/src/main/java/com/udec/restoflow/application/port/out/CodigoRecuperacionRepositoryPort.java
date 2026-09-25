package com.udec.restoflow.application.port.out;

import com.udec.restoflow.domain.model.CodigoRecuperacion;

import java.util.Optional;

/** Puerto de salida para guardar los códigos de "olvidé mi contraseña". */
public interface CodigoRecuperacionRepositoryPort {

    CodigoRecuperacion guardar(CodigoRecuperacion codigo);

    /** El código más reciente que no se ha usado para ese usuario. */
    Optional<CodigoRecuperacion> buscarUltimoSinUsar(Long usuarioId);

    /** Anula los códigos anteriores cuando se pide uno nuevo (solo sirve el último). */
    void invalidarCodigosDelUsuario(Long usuarioId);
}
