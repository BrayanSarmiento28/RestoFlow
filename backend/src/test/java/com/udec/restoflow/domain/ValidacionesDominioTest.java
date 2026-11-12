package com.udec.restoflow.domain;

import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.exception.OperacionNoPermitidaException;
import com.udec.restoflow.domain.model.Alerta;
import com.udec.restoflow.domain.model.Categoria;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.MetodoPago;
import com.udec.restoflow.domain.model.MovimientoInventario;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.PrioridadAlerta;
import com.udec.restoflow.domain.model.Producto;
import com.udec.restoflow.domain.model.RecetaIngrediente;
import com.udec.restoflow.domain.model.RegistroPeticion;
import com.udec.restoflow.domain.model.ResumenRegistro;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.TipoMovimiento;
import com.udec.restoflow.domain.model.UnidadMedida;
import com.udec.restoflow.domain.model.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Las reglas de validación del dominio: cada dato obligatorio o fuera de rango se rechaza con un mensaje claro,
 * antes de llegar a la base de datos.
 */
class ValidacionesDominioTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 12, 12, 0);
    private static final BigDecimal UNO = BigDecimal.ONE;
    private static final String HASH = "$2a$10$hash";

    // ---------- Usuarios ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"corta1", "solo-letras", "12345678"})
    @DisplayName("La contraseña necesita 8 caracteres, una letra y un número")
    void contrasenaInsegura(String contrasena) {
        assertThrows(DomainException.class, () -> Usuario.validarContrasenaSegura(contrasena));
    }

    @Test
    @DisplayName("Una contraseña con letras (mayúsculas o minúsculas) y números es válida")
    void contrasenaSegura() {
        assertDoesNotThrow(() -> Usuario.validarContrasenaSegura("Fogon2027"));
        assertDoesNotThrow(() -> Usuario.validarContrasenaSegura("fogon2027"));
        assertDoesNotThrow(() -> Usuario.validarContrasenaSegura("FOGON2027"));
    }

    @Test
    @DisplayName("Un usuario necesita correo, nombre de 3 letras, contraseña y rol")
    void usuarioIncompleto() {
        assertThrows(DomainException.class, () -> Usuario.normalizarEmail(" "));
        assertThrows(DomainException.class, () -> Usuario.normalizarEmail(null));
        assertThrows(DomainException.class, () -> Usuario.nuevo("Al", "al@restoflow.com", HASH, Rol.MESERO, AHORA));
        assertThrows(DomainException.class, () -> Usuario.nuevo(null, "al@restoflow.com", HASH, Rol.MESERO, AHORA));
        assertThrows(DomainException.class, () -> Usuario.nuevo("Alba", "al@restoflow.com", " ", Rol.MESERO, AHORA));
        assertThrows(DomainException.class, () -> Usuario.nuevo("Alba", "al@restoflow.com", null, Rol.MESERO, AHORA));
        assertThrows(DomainException.class, () -> Usuario.nuevo("Alba", "al@restoflow.com", HASH, null, AHORA));
        assertThat(Rol.COCINA.getDescripcion()).isEqualTo("Personal de cocina");
    }

    // ---------- Inventario ----------

    @Test
    @DisplayName("Un insumo necesita nombre, unidad, cantidades no negativas y movimientos mayores que cero")
    void insumoInvalido() {
        assertThrows(DomainException.class, () -> Ingrediente.nuevo("A", UnidadMedida.GRAMO, UNO, UNO));
        assertThrows(DomainException.class, () -> Ingrediente.nuevo(null, UnidadMedida.GRAMO, UNO, UNO));
        assertThrows(DomainException.class, () -> Ingrediente.nuevo("Arroz", null, UNO, UNO));
        assertThrows(DomainException.class, () -> Ingrediente.nuevo("Arroz", UnidadMedida.GRAMO, UNO, null));
        BigDecimal negativo = new BigDecimal("-1");
        assertThrows(DomainException.class, () -> Ingrediente.nuevo("Arroz", UnidadMedida.GRAMO, UNO, negativo));

        Ingrediente arroz = Ingrediente.nuevo("Arroz", UnidadMedida.GRAMO, UNO, UNO);
        assertThrows(DomainException.class, () -> arroz.ingresar(BigDecimal.ZERO));
        assertThrows(DomainException.class, () -> arroz.ingresar(null));
        arroz.desactivar();
        arroz.activar();
        assertThat(arroz.isActivo()).isTrue();
    }

    @Test
    @DisplayName("Una receta y un movimiento necesitan sus datos y cantidades válidas")
    void recetaYMovimientoInvalidos() {
        assertThrows(DomainException.class, () -> new RecetaIngrediente(null, null, 1L, UNO));
        assertThrows(DomainException.class, () -> new RecetaIngrediente(null, 1L, null, UNO));
        assertThrows(DomainException.class, () -> new RecetaIngrediente(null, 1L, 1L, null));
        assertThrows(DomainException.class, () -> new RecetaIngrediente(null, 1L, 1L, BigDecimal.ZERO));

        BigDecimal negativo = new BigDecimal("-1");
        assertThrows(DomainException.class,
                () -> new MovimientoInventario(null, null, TipoMovimiento.ENTRADA, UNO, null, null, null, AHORA));
        assertThrows(DomainException.class,
                () -> new MovimientoInventario(null, 1L, null, UNO, null, null, null, AHORA));
        assertThrows(DomainException.class,
                () -> new MovimientoInventario(null, 1L, TipoMovimiento.ENTRADA, UNO, null, null, null, null));
        assertThrows(DomainException.class,
                () -> new MovimientoInventario(null, 1L, TipoMovimiento.ENTRADA, null, null, null, null, AHORA));
        assertThrows(DomainException.class,
                () -> new MovimientoInventario(null, 1L, TipoMovimiento.ENTRADA, negativo, null, null, null, AHORA));
        assertThrows(DomainException.class,
                () -> new Alerta(null, 1L, " ", PrioridadAlerta.ALTA, false, AHORA, null));
        assertThrows(DomainException.class,
                () -> new Alerta(null, 1L, null, PrioridadAlerta.ALTA, false, AHORA, null));
    }

    // ---------- Catálogo ----------

    @Test
    @DisplayName("Un producto necesita un nombre de al menos 3 letras; una categoría se puede reactivar")
    void catalogoInvalido() {
        BigDecimal precio = new BigDecimal("9000");
        assertThrows(DomainException.class, () -> new Producto(null, "Té", null, precio, 1L, null, true));
        assertThrows(DomainException.class, () -> new Producto(null, null, null, precio, 1L, null, true));

        Categoria bebidas = Categoria.nueva("Bebidas");
        bebidas.desactivar();
        bebidas.activar();
        assertThat(bebidas.isActiva()).isTrue();
    }

    // ---------- Pedidos ----------

    private static DetallePedido linea() {
        return new DetallePedido(null, 1L, "Ajiaco", 1, new BigDecimal("28000"), null);
    }

    private static Pedido pedido(EstadoCocina estado) {
        return new Pedido(1L, 4, 7L, "Laura", List.of(linea()), null, estado, EstadoPedido.ABIERTO,
                AHORA, null, null, null, null);
    }

    @Test
    @DisplayName("Cada línea necesita producto, precio mayor que cero y una nota corta")
    void lineaInvalida() {
        BigDecimal precio = new BigDecimal("28000");
        String notaLarga = "x".repeat(500);
        assertThrows(DomainException.class, () -> new DetallePedido(null, null, "Ajiaco", 1, precio, null));
        assertThrows(DomainException.class, () -> new DetallePedido(null, 1L, "Ajiaco", 1, null, null));
        assertThrows(DomainException.class, () -> new DetallePedido(null, 1L, "Ajiaco", 1, BigDecimal.ZERO, null));
        assertThrows(DomainException.class, () -> new DetallePedido(null, 1L, "Ajiaco", 1, precio, notaLarga));
        assertThat(new DetallePedido(null, 1L, "Ajiaco", 1, precio, "  ").getNota()).isNull();
    }

    @Test
    @DisplayName("Un pedido necesita quién lo registra, productos y observaciones cortas")
    void pedidoInvalido() {
        List<DetallePedido> lineas = List.of(linea());
        String largas = "x".repeat(1000);
        assertThrows(DomainException.class, () -> new Pedido(1L, 4, null, "Laura", lineas, null,
                EstadoCocina.PENDIENTE, EstadoPedido.ABIERTO, AHORA, null, null, null, null));
        assertThrows(DomainException.class, () -> new Pedido(1L, 4, 7L, "Laura", null, null,
                EstadoCocina.PENDIENTE, EstadoPedido.ABIERTO, AHORA, null, null, null, null));
        assertThrows(DomainException.class, () -> new Pedido(1L, 4, 7L, "Laura", lineas, largas,
                EstadoCocina.PENDIENTE, EstadoPedido.ABIERTO, AHORA, null, null, null, null));
        assertThat(pedido(EstadoCocina.PENDIENTE).getObservaciones()).isNull();
    }

    @Test
    @DisplayName("Los estados de cocina avanzan en orden; un pedido entregado ya no tiene más estados")
    void estadosDeCocina() {
        Pedido enCocina = pedido(EstadoCocina.PENDIENTE);
        assertThrows(DomainException.class, () -> enCocina.cambiarEstadoCocina(null, AHORA));

        Pedido entregado = pedido(EstadoCocina.ENTREGADO);
        OperacionNoPermitidaException e = assertThrows(OperacionNoPermitidaException.class,
                () -> entregado.cambiarEstadoCocina(EstadoCocina.LISTO, AHORA));
        assertThat(e).hasMessage("El pedido ya fue entregado; no tiene más estados de cocina");
        assertThat(EstadoCocina.ENTREGADO.siguiente()).isNull();
    }

    @Test
    @DisplayName("Una adición a un pedido listo lo devuelve a preparación; sin productos no se acepta")
    void adicion() {
        Pedido listo = pedido(EstadoCocina.LISTO);
        assertThrows(DomainException.class, () -> listo.agregarAdicion(null, AHORA));

        Producto gaseosa = new Producto(3L, "Gaseosa", null, new BigDecimal("5000"), 1L, "Bebidas", true);
        listo.agregarAdicion(List.of(DetallePedido.adicionDe(gaseosa, 1, null)), AHORA);

        assertThat(listo.getEstadoCocina()).isEqualTo(EstadoCocina.EN_PREPARACION);
        assertThat(listo.getFechaListo()).isNull();
        assertThat(MetodoPago.TRANSFERENCIA.getDescripcion()).isEqualTo("Transferencia");
    }

    // ---------- Registro de actividad ----------

    @Test
    @DisplayName("Un registro de actividad necesita fecha, método, ruta y un código HTTP real")
    void registroInvalido() {
        assertThrows(DomainException.class, () -> RegistroPeticion.nuevo(null, "r", "u", "ADMIN", "GET", "/api", 200, 5));
        assertThrows(DomainException.class, () -> RegistroPeticion.nuevo(AHORA, "r", "u", "ADMIN", null, "/api", 200, 5));
        assertThrows(DomainException.class, () -> RegistroPeticion.nuevo(AHORA, "r", "u", "ADMIN", " ", "/api", 200, 5));
        assertThrows(DomainException.class, () -> RegistroPeticion.nuevo(AHORA, "r", "u", "ADMIN", "GET", null, 200, 5));
        assertThrows(DomainException.class, () -> RegistroPeticion.nuevo(AHORA, "r", "u", "ADMIN", "GET", " ", 200, 5));
        assertThat(new ResumenRegistro.Pagina(List.of(), 0, 0, 0).totalPaginas()).isZero();
        assertThat(new ResumenRegistro.Pagina(List.of(), 41, 0, 20).totalPaginas()).isEqualTo(3);
    }
}
