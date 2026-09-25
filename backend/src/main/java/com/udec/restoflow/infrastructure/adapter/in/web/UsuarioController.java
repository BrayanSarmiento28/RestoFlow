package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.GestionarCuentasUseCase;
import com.udec.restoflow.application.port.in.GestionarCuentasUseCase.ComandoCrearCuenta;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.AuthDto.CrearUsuarioRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.AuthDto.UsuarioResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HU-03 · Gestión de cuentas del personal. Protegido: solo ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final GestionarCuentasUseCase cuentas;

    public UsuarioController(GestionarCuentasUseCase cuentas) {
        this.cuentas = cuentas;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crear(@Valid @RequestBody CrearUsuarioRequest request) {
        return UsuarioResponse.desde(cuentas.crearCuenta(
                new ComandoCrearCuenta(request.nombre(), request.email(), request.password(), request.rol())));
    }

    @GetMapping
    public List<UsuarioResponse> listar() {
        return cuentas.listarCuentas().stream().map(UsuarioResponse::desde).toList();
    }

    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return UsuarioResponse.desde(cuentas.obtenerCuenta(id));
    }
}
