package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.ConsultarRegistroUseCase;
import com.udec.restoflow.application.port.in.ConsultarRegistroUseCase.ResultadoRegistro;
import com.udec.restoflow.application.port.out.RegistroPeticionRepositoryPort;
import com.udec.restoflow.domain.exception.DomainException;
import com.udec.restoflow.domain.model.FiltroRegistro;
import com.udec.restoflow.domain.model.RegistroPeticion;
import com.udec.restoflow.domain.model.ResumenRegistro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Registro de actividad con un doble en memoria de la tabla (sin base de datos). */
class RegistroActividadServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 4, 10, 0);

    /** Doble de la tabla registro_peticiones: aplica los mismos filtros que la consulta real. */
    static class RegistrosEnMemoria implements RegistroPeticionRepositoryPort {
        final List<RegistroPeticion> filas = new ArrayList<>();

        @Override
        public void guardar(RegistroPeticion r) {
            filas.add(r);
        }

        private List<RegistroPeticion> filtrar(FiltroRegistro f) {
            return filas.stream()
                    .filter(r -> f.desde() == null || !r.fecha().isBefore(f.desde()))
                    .filter(r -> f.hasta() == null || r.fecha().isBefore(f.hasta()))
                    .filter(r -> f.usuario() == null || (r.usuario() != null
                            && r.usuario().toLowerCase(Locale.ROOT).contains(f.usuario().toLowerCase(Locale.ROOT))))
                    .filter(r -> f.metodo() == null || r.metodo().equals(f.metodo()))
                    .filter(r -> f.ruta() == null || r.ruta().contains(f.ruta()))
                    .filter(r -> r.estado() >= f.estadoDesde() && r.estado() <= f.estadoHasta())
                    .filter(r -> r.duracionMs() >= f.duracionMinima())
                    .sorted(Comparator.comparing(RegistroPeticion::fecha).reversed())
                    .toList();
        }

        @Override
        public ResumenRegistro.Pagina buscar(FiltroRegistro f, int pagina, int tamano) {
            List<RegistroPeticion> todas = filtrar(f);
            List<RegistroPeticion> pag = todas.stream().skip((long) pagina * tamano).limit(tamano).toList();
            return new ResumenRegistro.Pagina(pag, todas.size(), pagina, tamano);
        }

        @Override
        public ResumenRegistro resumir(FiltroRegistro f) {
            List<RegistroPeticion> t = filtrar(f);
            return new ResumenRegistro(t.size(), t.stream().filter(RegistroPeticion::esRechazo).count(),
                    t.stream().filter(RegistroPeticion::esError).count(),
                    t.stream().filter(RegistroPeticion::esLenta).count(),
                    Math.round(t.stream().mapToLong(RegistroPeticion::duracionMs).average().orElse(0)));
        }

        @Override
        public int borrarAnterioresA(LocalDateTime limite) {
            int antes = filas.size();
            filas.removeIf(r -> r.fecha().isBefore(limite));
            return antes - filas.size();
        }
    }

    private RegistrosEnMemoria tabla;
    private RegistroActividadService servicio;

    private void registrar(LocalDateTime fecha, String usuario, String metodo, String ruta, int estado, long ms) {
        servicio.registrar(RegistroPeticion.nuevo(fecha, "id", usuario, "ADMIN", metodo, ruta, estado, ms));
    }

    @BeforeEach
    void preparar() {
        tabla = new RegistrosEnMemoria();
        servicio = new RegistroActividadService(tabla, Clock.fixed(AHORA.atZone(BOGOTA).toInstant(), BOGOTA), 30);
        registrar(AHORA.minusMinutes(5), "admin@restoflow.com", "GET", "/api/panel/resumen", 200, 40);
        registrar(AHORA.minusMinutes(4), "laura@restoflow.com", "POST", "/api/pedidos", 201, 80);
        registrar(AHORA.minusMinutes(3), "laura@restoflow.com", "POST", "/api/pedidos", 409, 30);
        registrar(AHORA.minusMinutes(2), null, "POST", "/api/auth/login", 401, 20);
        registrar(AHORA.minusMinutes(1), "admin@restoflow.com", "GET", "/api/historial/ventas", 500, 1500);
        registrar(AHORA.minusDays(40), "admin@restoflow.com", "GET", "/api/panel/resumen", 200, 10);
    }

    @Test
    @DisplayName("El resumen cuenta total, rechazadas (4xx), errores (5xx), lentas y la duración promedio")
    void resumen() {
        ResultadoRegistro r = servicio.consultar(new FiltroRegistro(AHORA.minusDays(1), null, null, null, null, null), 0, 50);

        assertEquals(new ResumenRegistro(5, 2, 1, 1, 334), r.resumen());               // (40+80+30+20+1500) ÷ 5
        assertEquals(5, r.pagina().total());
        assertEquals("/api/historial/ventas", r.pagina().registros().get(0).ruta());   // el más reciente primero
    }

    @Test
    @DisplayName("Filtra por usuario (parte del correo, sin importar mayúsculas), método y tipo")
    void filtros() {
        assertEquals(2, servicio.consultar(new FiltroRegistro(null, null, "LAURA", null, null, null), 0, 50)
                .pagina().total());
        assertEquals(1, servicio.consultar(new FiltroRegistro(null, null, "laura", "post", null,
                FiltroRegistro.Tipo.RECHAZADAS), 0, 50).pagina().total());
        assertEquals(1, servicio.consultar(new FiltroRegistro(null, null, null, null, null,
                FiltroRegistro.Tipo.ERRORES), 0, 50).pagina().total());
    }

    @Test
    @DisplayName("Pagina los resultados: tamaño por defecto 50 y máximo 200")
    void paginacion() {
        FiltroRegistro todo = new FiltroRegistro(null, null, null, null, null, null);
        ResultadoRegistro segunda = servicio.consultar(todo, 1, 2);
        assertEquals(2, segunda.pagina().registros().size());
        assertEquals(6, segunda.pagina().total());
        assertEquals(3, segunda.pagina().totalPaginas());

        assertEquals(ConsultarRegistroUseCase.TAMANO_POR_DEFECTO, servicio.consultar(todo, 0, 0).pagina().tamano());
        assertEquals(ConsultarRegistroUseCase.TAMANO_MAXIMO, servicio.consultar(todo, 0, 5000).pagina().tamano());
        assertThrows(DomainException.class, () -> servicio.consultar(todo, -1, 10));
    }

    @Test
    @DisplayName("La limpieza borra solo lo que tiene más de 30 días")
    void limpieza() {
        assertEquals(1, servicio.limpiar());
        assertEquals(5, tabla.filas.size());
        assertEquals(0, servicio.limpiar());
    }

    @Test
    @DisplayName("Conservar menos de 1 día no tiene sentido")
    void diasInvalidos() {
        Clock reloj = Clock.system(BOGOTA);
        assertThrows(DomainException.class, () -> new RegistroActividadService(tabla, reloj, 0));
    }
}
