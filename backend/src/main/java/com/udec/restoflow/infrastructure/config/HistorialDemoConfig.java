package com.udec.restoflow.infrastructure.config;

import com.udec.restoflow.application.port.in.GestionarPedidosUseCase.ComandoPedido;
import com.udec.restoflow.application.port.in.GestionarPedidosUseCase.LineaPedido;
import com.udec.restoflow.application.port.out.AlertaRepositoryPort;
import com.udec.restoflow.application.port.out.IngredienteRepositoryPort;
import com.udec.restoflow.application.port.out.MovimientoRepositoryPort;
import com.udec.restoflow.application.port.out.PedidoRepositoryPort;
import com.udec.restoflow.application.port.out.ProductoRepositoryPort;
import com.udec.restoflow.application.port.out.RecetaRepositoryPort;
import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.application.service.ControlAlertas;
import com.udec.restoflow.application.service.DescontarInventarioService;
import com.udec.restoflow.application.service.GestionarCocinaService;
import com.udec.restoflow.application.service.GestionarInventarioService;
import com.udec.restoflow.application.service.GestionarPedidosService;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.Ingrediente;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.Producto;
import com.udec.restoflow.domain.model.RecetaIngrediente;
import com.udec.restoflow.domain.model.Rol;
import com.udec.restoflow.domain.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Solo para demostraciones de RestoFlow AI ({@code IA_HISTORIAL_DEMO=true}): genera 3 semanas de ventas de ejemplo
 * para que el análisis tenga datos desde el primer día.
 * <p>
 * No escribe directo en la base de datos: usa los <b>mismos casos de uso</b> que el mesero y la cocina (registrar el
 * pedido, prepararlo, entregarlo y cobrarlo), con un reloj que "viaja" a cada día del pasado. Así cada venta
 * descuenta su inventario y deja sus movimientos como una venta real (RF-06, RF-12). Las compras de insumos se
 * registran igual, como entradas.
 * <ul>
 *   <li>Fines de semana con más pedidos y la última semana con más pedidos que las anteriores, para que se vean
 *       el día más fuerte y las tendencias.</li>
 *   <li>Los últimos 2 días no se reabastece, así algunos insumos quedan bajos y aparecen recomendaciones de compra.</li>
 *   <li>No hace nada si ya hay ventas recientes (más de 30 en las últimas 3 semanas), si no hay productos con
 *       receta o si no hay un usuario para registrar los pedidos.</li>
 * </ul>
 * Arranca después del menú y el inventario de ejemplo (@Order 3).
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "restoflow.ia.historial-demo", havingValue = "true")
public class HistorialDemoConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(HistorialDemoConfig.class);
    private static final int DIAS = 21;
    private static final int VENTAS_SUFICIENTES = 30;
    private static final int DIAS_SIN_COMPRAS = 2;
    private static final String MOTIVO_COMPRA = "Compra (datos de ejemplo de RestoFlow AI)";

    private final PedidoRepositoryPort pedidos;
    private final ProductoRepositoryPort productos;
    private final UsuarioRepositoryPort usuarios;
    private final RecetaRepositoryPort recetas;
    private final IngredienteRepositoryPort ingredientes;
    private final MovimientoRepositoryPort movimientos;
    private final AlertaRepositoryPort alertas;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    @SuppressWarnings("java:S107")   // recibe los puertos que necesita para armar los casos de uso con su propio reloj
    public HistorialDemoConfig(PedidoRepositoryPort pedidos, ProductoRepositoryPort productos,
                               UsuarioRepositoryPort usuarios, RecetaRepositoryPort recetas,
                               IngredienteRepositoryPort ingredientes, MovimientoRepositoryPort movimientos,
                               AlertaRepositoryPort alertas, Clock reloj, TransactionTemplate transaccion) {
        this.pedidos = pedidos;
        this.productos = productos;
        this.usuarios = usuarios;
        this.recetas = recetas;
        this.ingredientes = ingredientes;
        this.movimientos = movimientos;
        this.alertas = alertas;
        this.reloj = reloj;
        this.transaccion = transaccion;
    }

    /**
     * Corre en un hilo aparte: generar 3 semanas de ventas toma unos segundos y así el backend queda listo
     * (healthcheck de Docker en verde) sin esperar a que termine.
     */
    @Override
    public void run(String... args) {
        Thread hilo = new Thread(() -> {
            try {
                generar();
            } catch (RuntimeException e) {
                log.error("RestoFlow AI: no se pudieron generar los datos de ejemplo", e);
            }
        }, "restoflow-ai-historial-demo");
        hilo.setDaemon(true);
        hilo.start();
    }

    void generar() {
        LocalDate hoy = LocalDate.now(reloj);
        Integer recientes = transaccion.execute(estado ->
                pedidos.pagadosEntre(hoy.minusDays(DIAS).atStartOfDay(), hoy.atStartOfDay()).size());
        if (recientes != null && recientes > VENTAS_SUFICIENTES) {
            log.info("RestoFlow AI: ya hay {} ventas en las últimas 3 semanas; no se generan datos de ejemplo.", recientes);
            return;
        }
        Optional<Usuario> mesero = transaccion.execute(estado -> quienRegistra());
        List<Producto> menu = transaccion.execute(estado -> productosConReceta());
        if (mesero == null || mesero.isEmpty() || menu == null || menu.isEmpty()) {
            log.warn("RestoFlow AI: no se generaron datos de ejemplo (faltan productos con receta o un usuario activo).");
            return;
        }

        RelojAjustable relojDemo = new RelojAjustable(reloj.getZone());
        ControlAlertas control = new ControlAlertas(alertas, relojDemo);
        GestionarInventarioService inventario = new GestionarInventarioService(ingredientes, movimientos, control, relojDemo);
        GestionarPedidosService ventas = new GestionarPedidosService(pedidos, productos, usuarios,
                new DescontarInventarioService(recetas, ingredientes, movimientos, control, relojDemo), relojDemo);
        GestionarCocinaService cocina = new GestionarCocinaService(pedidos, relojDemo);
        // Semilla fija: siempre genera los mismos datos. No es un uso de seguridad (no crea claves ni tokens).
        Random azar = new Random(2026);   // NOSONAR java:S2245

        int total = 0;
        for (int hace = DIAS; hace >= 1; hace--) {
            LocalDate dia = hoy.minusDays(hace);
            if (hace > DIAS_SIN_COMPRAS) {
                relojDemo.fijar(dia.atTime(8, 30));
                transaccion.executeWithoutResult(estado -> reabastecer(inventario));
            }
            int cantidad = pedidosDelDia(dia, hace, azar);
            for (int i = 0; i < cantidad; i++) {
                int hora = i % 2 == 0 ? 12 + azar.nextInt(3) : 18 + azar.nextInt(3);
                relojDemo.fijar(dia.atTime(hora, azar.nextInt(60)));
                Long meseroId = mesero.get().getId();
                List<LineaPedido> lineas = lineas(menu, azar);
                int mesa = 1 + azar.nextInt(12);
                // Cada venta en su propia transacción, como cuando la hace el mesero desde la pantalla
                transaccion.executeWithoutResult(estado -> vender(ventas, cocina, relojDemo, meseroId, lineas, mesa));
                total++;
            }
        }
        log.info("RestoFlow AI: se generaron {} ventas de ejemplo de los últimos {} días.", total, DIAS);
    }

    private Optional<Usuario> quienRegistra() {
        List<Usuario> activos = usuarios.listarTodos().stream().filter(Usuario::isActivo).toList();
        return activos.stream().filter(u -> u.getRol() == Rol.MESERO).findFirst()
                .or(() -> activos.stream().filter(u -> u.getRol() == Rol.ADMIN).findFirst());
    }

    private List<Producto> productosConReceta() {
        List<Producto> activos = productos.listar(true, null);
        Set<Long> conReceta = recetas.deProductos(activos.stream().map(Producto::getId).toList()).stream()
                .map(RecetaIngrediente::getProductoId)
                .collect(Collectors.toSet());
        return activos.stream().filter(p -> conReceta.contains(p.getId())).toList();
    }

    /** Compra de la mañana: lo que esté por debajo de 4 veces su mínimo se lleva a 6 veces el mínimo. */
    private void reabastecer(GestionarInventarioService inventario) {
        for (Ingrediente i : ingredientes.listar(true)) {
            BigDecimal objetivo = i.getStockMinimo().multiply(BigDecimal.valueOf(6));
            if (i.getStockActual().compareTo(i.getStockMinimo().multiply(BigDecimal.valueOf(4))) < 0
                    && objetivo.compareTo(i.getStockActual()) > 0) {
                inventario.registrarEntrada(i.getId(), objetivo.subtract(i.getStockActual()), MOTIVO_COMPRA, null);
            }
        }
    }

    /** Entre semana 8 a 10 pedidos; viernes, sábado y domingo 6 más; la última semana 2 más (tendencia al alza). */
    private static int pedidosDelDia(LocalDate dia, int hace, Random azar) {
        DayOfWeek d = dia.getDayOfWeek();
        boolean finDeSemana = d == DayOfWeek.FRIDAY || d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
        return 8 + (finDeSemana ? 6 : 0) + (hace <= 7 ? 2 : 0) + azar.nextInt(3);
    }

    /** 1 a 3 productos distintos; los primeros del menú salen más (dos sorteos y se queda el menor). */
    private static List<LineaPedido> lineas(List<Producto> menu, Random azar) {
        Map<Long, LineaPedido> elegidos = new LinkedHashMap<>();
        int cuantos = 1 + azar.nextInt(Math.min(3, menu.size()));
        while (elegidos.size() < cuantos) {
            Producto p = menu.get(Math.min(azar.nextInt(menu.size()), azar.nextInt(menu.size())));
            elegidos.putIfAbsent(p.getId(), new LineaPedido(p.getId(), 1 + azar.nextInt(2), null));
        }
        return new ArrayList<>(elegidos.values());
    }

    /** El recorrido completo de un pedido: mesero → cocina → entrega → cobro (que descuenta el inventario). */
    private static void vender(GestionarPedidosService ventas, GestionarCocinaService cocina, RelojAjustable relojDemo,
                               Long meseroId, List<LineaPedido> lineas, int mesa) {
        Pedido pedido = ventas.registrar(new ComandoPedido(mesa, lineas, null), meseroId);
        relojDemo.avanzarMinutos(3);
        cocina.cambiarEstado(pedido.getId(), EstadoCocina.EN_PREPARACION);
        relojDemo.avanzarMinutos(15);
        cocina.cambiarEstado(pedido.getId(), EstadoCocina.LISTO);
        relojDemo.avanzarMinutos(5);
        cocina.cambiarEstado(pedido.getId(), EstadoCocina.ENTREGADO);
        relojDemo.avanzarMinutos(30);
        ventas.registrarVenta(pedido.getId());
    }

    /** Reloj que se puede mover a cualquier fecha: con él los casos de uso "creen" que es ese día. */
    static final class RelojAjustable extends Clock {

        private final ZoneId zona;
        private Instant instante;

        RelojAjustable(ZoneId zona) {
            this.zona = zona;
            this.instante = Instant.now();
        }

        void fijar(LocalDateTime momento) {
            instante = momento.atZone(zona).toInstant();
        }

        void avanzarMinutos(long minutos) {
            instante = instante.plusSeconds(minutos * 60);
        }

        @Override
        public ZoneId getZone() {
            return zona;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }
}
