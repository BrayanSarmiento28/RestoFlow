package com.udec.restoflow.infrastructure.security;

import com.udec.restoflow.domain.model.Rol;

/** Datos del usuario que viajan dentro del token y están disponibles en cada petición autenticada. */
public record UsuarioAutenticado(Long id, String email, String nombre, Rol rol) {
}
