package com.udec.restoflow.infrastructure.config;

import com.udec.restoflow.application.port.in.BuscarProductoExternoUseCase;
import com.udec.restoflow.application.port.in.ConsultarHistorialUseCase;
import com.udec.restoflow.application.port.in.ConsultarPanelUseCase;
import com.udec.restoflow.application.port.in.DescontarInventarioUseCase;
import com.udec.restoflow.application.port.in.GestionarAlertasUseCase;
import com.udec.restoflow.application.port.in.GestionarCatalogoUseCase;
import com.udec.restoflow.application.port.in.GestionarCocinaUseCase;
import com.udec.restoflow.application.port.in.GestionarCuentasUseCase;
import com.udec.restoflow.application.port.in.GestionarInventarioUseCase;
import com.udec.restoflow.application.port.in.GestionarPedidosUseCase;
import com.udec.restoflow.application.port.in.GestionarRecetasUseCase;
import com.udec.restoflow.application.port.in.IniciarSesionUseCase;
import com.udec.restoflow.application.port.in.RecuperarContrasenaUseCase;
import com.udec.restoflow.application.port.out.AlertaRepositoryPort;
import com.udec.restoflow.application.port.out.CategoriaRepositoryPort;
import com.udec.restoflow.application.port.out.CifradoContrasenaPort;
import com.udec.restoflow.application.port.out.CatalogoExternoPort;
import com.udec.restoflow.application.port.out.CodigoRecuperacionRepositoryPort;
import com.udec.restoflow.application.port.out.EnviarCorreoPort;
import com.udec.restoflow.application.port.out.GeneradorTokenPort;
import com.udec.restoflow.application.port.out.IngredienteRepositoryPort;
import com.udec.restoflow.application.port.out.MovimientoRepositoryPort;
import com.udec.restoflow.application.port.out.PedidoRepositoryPort;
import com.udec.restoflow.application.port.out.ProductoRepositoryPort;
import com.udec.restoflow.application.port.out.RecetaRepositoryPort;
import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.application.service.BuscarProductoExternoService;
import com.udec.restoflow.application.service.ConsultarHistorialService;
import com.udec.restoflow.application.service.ConsultarPanelService;
import com.udec.restoflow.application.service.ControlAlertas;
import com.udec.restoflow.application.service.DescontarInventarioService;
import com.udec.restoflow.application.service.GestionarAlertasService;
import com.udec.restoflow.application.service.GestionarCatalogoService;
import com.udec.restoflow.application.service.GestionarCocinaService;
import com.udec.restoflow.application.service.GestionarCuentasService;
import com.udec.restoflow.application.service.GestionarInventarioService;
import com.udec.restoflow.application.service.GestionarPedidosService;
import com.udec.restoflow.application.service.GestionarRecetasService;
import com.udec.restoflow.application.service.IniciarSesionService;
import com.udec.restoflow.application.service.RecuperarContrasenaService;
import com.udec.restoflow.application.service.RestoFlowAIService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Aquí se "conecta" el hexágono: Spring crea cada caso de uso y le entrega los adaptadores
 * que implementan sus puertos de salida (inyección de dependencias).
 * Por eso las clases de la capa de aplicación no necesitan anotaciones como @Service.
 */
@Configuration
public class BeanConfig {

    /** Reloj del sistema. En las pruebas se reemplaza por uno fijo para simular el paso del tiempo. */
    @Bean
    public Clock reloj() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public IniciarSesionUseCase iniciarSesionUseCase(UsuarioRepositoryPort usuarios, CifradoContrasenaPort cifrado,
                                                     GeneradorTokenPort generadorToken) {
        return new IniciarSesionService(usuarios, cifrado, generadorToken);
    }

    @Bean
    public RecuperarContrasenaUseCase recuperarContrasenaUseCase(UsuarioRepositoryPort usuarios,
                                                                 CodigoRecuperacionRepositoryPort codigos,
                                                                 CifradoContrasenaPort cifrado,
                                                                 EnviarCorreoPort correo, Clock reloj) {
        return new RecuperarContrasenaService(usuarios, codigos, cifrado, correo, reloj);
    }

    @Bean
    public GestionarCuentasUseCase gestionarCuentasUseCase(UsuarioRepositoryPort usuarios,
                                                           CifradoContrasenaPort cifrado, Clock reloj) {
        return new GestionarCuentasService(usuarios, cifrado, reloj);
    }

    @Bean
    public GestionarCatalogoUseCase gestionarCatalogoUseCase(CategoriaRepositoryPort categorias,
                                                             ProductoRepositoryPort productos) {
        return new GestionarCatalogoService(categorias, productos);
    }

    @Bean
    public GestionarPedidosUseCase gestionarPedidosUseCase(PedidoRepositoryPort pedidos, ProductoRepositoryPort productos,
                                                           UsuarioRepositoryPort usuarios,
                                                           DescontarInventarioUseCase inventario, Clock reloj) {
        return new GestionarPedidosService(pedidos, productos, usuarios, inventario, reloj);
    }

    @Bean
    public GestionarCocinaUseCase gestionarCocinaUseCase(PedidoRepositoryPort pedidos, Clock reloj) {
        return new GestionarCocinaService(pedidos, reloj);
    }

    // ---------- Inventario, panel e historial (Sprint 4: RF-05 a RF-08 y RF-12) ----------

    @Bean
    public ControlAlertas controlAlertas(AlertaRepositoryPort alertas, Clock reloj) {
        return new ControlAlertas(alertas, reloj);
    }

    @Bean
    public GestionarInventarioUseCase gestionarInventarioUseCase(IngredienteRepositoryPort ingredientes,
                                                                 MovimientoRepositoryPort movimientos,
                                                                 ControlAlertas control, Clock reloj) {
        return new GestionarInventarioService(ingredientes, movimientos, control, reloj);
    }

    @Bean
    public DescontarInventarioUseCase descontarInventarioUseCase(RecetaRepositoryPort recetas,
                                                                 IngredienteRepositoryPort ingredientes,
                                                                 MovimientoRepositoryPort movimientos,
                                                                 ControlAlertas control, Clock reloj) {
        return new DescontarInventarioService(recetas, ingredientes, movimientos, control, reloj);
    }

    @Bean
    public GestionarRecetasUseCase gestionarRecetasUseCase(RecetaRepositoryPort recetas, ProductoRepositoryPort productos,
                                                           IngredienteRepositoryPort ingredientes) {
        return new GestionarRecetasService(recetas, productos, ingredientes);
    }

    @Bean
    public GestionarAlertasUseCase gestionarAlertasUseCase(AlertaRepositoryPort alertas, Clock reloj) {
        return new GestionarAlertasService(alertas, reloj);
    }

    @Bean
    public ConsultarPanelUseCase consultarPanelUseCase(PedidoRepositoryPort pedidos,
                                                       IngredienteRepositoryPort ingredientes,
                                                       AlertaRepositoryPort alertas) {
        return new ConsultarPanelService(pedidos, ingredientes, alertas);
    }

    @Bean
    public ConsultarHistorialUseCase consultarHistorialUseCase(PedidoRepositoryPort pedidos) {
        return new ConsultarHistorialService(pedidos);
    }

    /** Catálogo externo (Open Food Facts): el adaptador de la API externa implementa CatalogoExternoPort. */
    @Bean
    public BuscarProductoExternoUseCase buscarProductoExternoUseCase(CatalogoExternoPort catalogo) {
        return new BuscarProductoExternoService(catalogo);
    }

    // ---------- RestoFlow AI (Sprint 5: RF-09 a RF-11) ----------

    /**
     * Un solo servicio que implementa dos casos de uso: consultar el análisis y generar las alertas automáticas.
     * Se declara con su clase para que Spring lo entregue a quien pida cualquiera de los dos puertos.
     */
    @Bean
    public RestoFlowAIService restoFlowAIService(PedidoRepositoryPort pedidos, IngredienteRepositoryPort ingredientes,
                                                 MovimientoRepositoryPort movimientos, AlertaRepositoryPort alertas,
                                                 Clock reloj) {
        return new RestoFlowAIService(pedidos, ingredientes, movimientos, alertas, reloj);
    }
}
