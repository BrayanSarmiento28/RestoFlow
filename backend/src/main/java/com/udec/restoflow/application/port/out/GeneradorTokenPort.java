package com.udec.restoflow.application.port.out;

import com.udec.restoflow.domain.model.Usuario;

/** Puerto de salida para generar el token de sesión (la implementación usa JWT). */
public interface GeneradorTokenPort {

    String generarToken(Usuario usuario);

    long getVigenciaSegundos();
}
