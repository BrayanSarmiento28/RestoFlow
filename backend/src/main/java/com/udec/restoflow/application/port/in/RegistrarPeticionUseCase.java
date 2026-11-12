package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.RegistroPeticion;

/** Guarda en la base de datos una petición que ya terminó (lo llama el filtro de peticiones, en segundo plano). */
public interface RegistrarPeticionUseCase {

    void registrar(RegistroPeticion registro);
}
