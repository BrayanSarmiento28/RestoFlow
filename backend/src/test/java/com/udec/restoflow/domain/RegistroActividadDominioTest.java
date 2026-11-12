package com.udec.restoflow.domain;

import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.model.FiltroRegistro;
import com.udec.restoflow.domain.model.RegistroPeticion;
import com.udec.restoflow.domain.model.ResumenRegistro;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registro de actividad en la base de datos · reglas del registro y de los filtros. */
class RegistroActividadDominioTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 4, 10, 0);

    private static RegistroPeticion registro(int estado, long ms) {
        return RegistroPeticion.nuevo(AHORA, "a1b2c3d4", "laura@restoflow.com", "MESERO", "GET", "/api/pedidos",
                estado, ms);
    }

    @Test
    @DisplayName("Clasifica cada petición: rechazo 4xx, error 5xx y lenta (más de 1 segundo)")
    void clasificacion() {
        assertTrue(registro(404, 20).esRechazo());
        assertFalse(registro(404, 20).esError());
        assertTrue(registro(500, 20).esError());
        assertFalse(registro(200, 20).esRechazo());
        assertTrue(registro(200, 1500).esLenta());
        assertFalse(registro(200, 1000).esLenta());
    }

    @Test
    @DisplayName("Recorta rutas muy largas y nunca guarda duraciones negativas")
    void normaliza() {
        RegistroPeticion r = RegistroPeticion.nuevo(AHORA, null, null, null, "GET", "/api/" + "x".repeat(800), 200, -5);
        assertEquals(RegistroPeticion.RUTA_MAXIMA, r.ruta().length());
        assertEquals(0, r.duracionMs());
        assertNull(r.usuario());                                       // petición anónima (por ejemplo, un login fallido)
    }

    @Test
    @DisplayName("Rechaza registros incompletos o con un código HTTP que no existe")
    void invalidos() {
        assertThrows(DomainException.class, () -> RegistroPeticion.nuevo(null, null, null, null, "GET", "/api", 200, 1));
        assertThrows(DomainException.class, () -> RegistroPeticion.nuevo(AHORA, null, null, null, " ", "/api", 200, 1));
        assertThrows(DomainException.class, () -> RegistroPeticion.nuevo(AHORA, null, null, null, "GET", "/api", 99, 1));
        assertThrows(DomainException.class, () -> RegistroPeticion.nuevo(AHORA, null, null, null, "GET", "/api", 600, 1));
    }

    @Test
    @DisplayName("Cada tipo de filtro se traduce en un rango de códigos HTTP y una duración mínima")
    void tiposDeFiltro() {
        FiltroRegistro errores = new FiltroRegistro(null, null, null, null, null, FiltroRegistro.Tipo.ERRORES);
        assertEquals(500, errores.estadoDesde());
        assertEquals(599, errores.estadoHasta());

        FiltroRegistro rechazadas = new FiltroRegistro(null, null, null, null, null, FiltroRegistro.Tipo.RECHAZADAS);
        assertEquals(400, rechazadas.estadoDesde());
        assertEquals(499, rechazadas.estadoHasta());

        FiltroRegistro exitosas = new FiltroRegistro(null, null, null, null, null, FiltroRegistro.Tipo.EXITOSAS);
        assertEquals(100, exitosas.estadoDesde());
        assertEquals(399, exitosas.estadoHasta());

        FiltroRegistro lentas = new FiltroRegistro(null, null, null, null, null, FiltroRegistro.Tipo.LENTAS);
        assertEquals(1001, lentas.duracionMinima());
        assertEquals(0, exitosas.duracionMinima());
    }

    @Test
    @DisplayName("El filtro limpia los textos vacíos, pone el método en mayúsculas y por defecto busca TODAS")
    void normalizaFiltro() {
        FiltroRegistro f = new FiltroRegistro(null, null, "  ", " get ", "", null);
        assertNull(f.usuario());
        assertNull(f.ruta());
        assertEquals("GET", f.metodo());
        assertEquals(FiltroRegistro.Tipo.TODAS, f.tipo());
    }

    @Test
    @DisplayName("La fecha inicial debe ser anterior a la final")
    void rangoInvalido() {
        LocalDateTime ayer = AHORA.minusDays(1);
        assertThrows(DomainException.class, () -> new FiltroRegistro(AHORA, ayer, null, null, null, null));
    }

    @Test
    @DisplayName("Una página sabe cuántas páginas hay en total")
    void paginas() {
        assertEquals(3, new ResumenRegistro.Pagina(List.of(), 101, 0, 50).totalPaginas());
        assertEquals(0, new ResumenRegistro.Pagina(List.of(), 0, 0, 50).totalPaginas());
    }
}
