package com.udec.restoflow.infrastructure.logging;

import com.udec.restoflow.domain.model.RegistroPeticion;

/** A dónde manda el filtro cada petición terminada para guardarla (la base de datos, en segundo plano). */
@FunctionalInterface
public interface DestinoRegistro {

    void enviar(RegistroPeticion registro);
}
