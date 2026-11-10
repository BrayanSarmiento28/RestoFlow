package com.udec.restoflow.infrastructure.adapter.in.web;

import com.udec.restoflow.application.port.in.GestionarCuentasUseCase;
import com.udec.restoflow.application.port.in.GestionarCuentasUseCase.ComandoCrearCuenta;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.AuthDto.CambioEstadoRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.AuthDto.CrearUsuarioRequest;
import com.udec.restoflow.infrastructure.adapter.in.web.dto.AuthDto.UsuarioResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.udec.restoflow.infrastructure.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HU-03 · Gestión de cuentas del personal. Protegido: solo ADMIN (ver SecurityConfig). */
@Tag(name = "Cuentas del personal", description = "HU-03 Crear, desactivar y reactivar cuentas · solo rol ADMIN")
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final GestionarCuentasUseCase cuentas;

    public UsuarioController(GestionarCuentasUseCase cuentas) {
        this.cuentas = cuentas;
    }

    @Operation(summary = "Crear cuenta", description = "Crea una cuenta con rol ADMIN, MESERO, COCINA o INVENTARIO. La contraseña se guarda cifrada con BCrypt.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crear(@Valid @RequestBody CrearUsuarioRequest request) {
        return UsuarioResponse.desde(cuentas.crearCuenta(
                new ComandoCrearCuenta(request.nombre(), request.email(), request.password(), request.rol())));
    }

    @Operation(summary = "Listar cuentas")
    @GetMapping
    public List<UsuarioResponse> listar() {
        return cuentas.listarCuentas().stream().map(UsuarioResponse::desde).toList();
    }

    @Operation(summary = "Obtener una cuenta por id")
    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return UsuarioResponse.desde(cuentas.obtenerCuenta(id));
    }

    @Operation(summary = "Desactivar o reactivar una cuenta",
            description = "Con {\"activa\": false} la cuenta ya no puede iniciar sesión y su sesión abierta deja de "
                    + "funcionar; con true vuelve a tener acceso. La cuenta no se borra: su historial se conserva. "
                    + "409 si el administrador intenta desactivar su propia cuenta.")
    @PatchMapping("/{id}/estado")
    public UsuarioResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest request,
                                         @AuthenticationPrincipal UsuarioAutenticado actual) {
        return UsuarioResponse.desde(cuentas.cambiarEstado(id, request.activa(), actual.id()));
    }
}
