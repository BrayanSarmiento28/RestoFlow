package com.udec.restoflow.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.udec.restoflow.application.port.out.PedidoRepositoryPort;
import com.udec.restoflow.application.port.out.UsuarioRepositoryPort;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.EstadoCocina;
import com.udec.restoflow.domain.model.EstadoPedido;
import com.udec.restoflow.domain.model.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Avisos de RestoFlow AI de punta a punta (seguridad, caso de uso, dominio y JPA con H2): un pedido que lleva una
 * hora en cocina le aparece a la cocina y al mesero de esa mesa, pero no a otro mesero ni a inventario.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AvisosIntegracionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private PedidoRepositoryPort pedidos;

    @Autowired
    private UsuarioRepositoryPort usuarios;

    @Autowired
    private Clock reloj;

    /** Guardar el pedido directo por el puerto necesita una transacción (como lo hacen los servicios). */
    @Autowired
    private PlatformTransactionManager transacciones;

    private String login(String email, String password) throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }

    private JsonNode crear(String admin, String ruta, String cuerpo) throws Exception {
        String respuesta = mvc.perform(post(ruta).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta);
    }

    private void crearUsuario(String admin, String email, String rol) throws Exception {
        crear(admin, "/api/usuarios", "{\"nombre\":\"Usuario Avisos\",\"email\":\"" + email
                + "\",\"password\":\"Clave2026x\",\"rol\":\"" + rol + "\"}");
    }

    /** Las claves de los avisos que recibe ese usuario. */
    private List<String> avisos(String token) throws Exception {
        String respuesta = mvc.perform(get("/api/avisos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode cuerpo = json.readTree(respuesta);
        assertTrue(cuerpo.get("generadoEn").isTextual());
        List<String> claves = new ArrayList<>();
        cuerpo.get("avisos").forEach(a -> claves.add(a.get("clave").asText()));
        return claves;
    }

    @Test
    @DisplayName("Un pedido con una hora en cocina avisa a la cocina, a su mesero y al admin; no a otro mesero")
    void avisosPorRol() throws Exception {
        String admin = login("admin@pruebas.com", "Admin2026*");
        long categoria = crear(admin, "/api/categorias", "{\"nombre\":\"Avisos\"}").get("id").asLong();
        long producto = crear(admin, "/api/productos",
                "{\"nombre\":\"Sancocho de prueba\",\"precio\":28000,\"categoriaId\":" + categoria + "}").get("id").asLong();
        crearUsuario(admin, "cocina.avisos@pruebas.com", "COCINA");
        crearUsuario(admin, "mesero.avisos@pruebas.com", "MESERO");
        crearUsuario(admin, "otro.avisos@pruebas.com", "MESERO");
        crearUsuario(admin, "inventario.avisos@pruebas.com", "INVENTARIO");
        long meseroId = usuarios.buscarPorEmail("mesero.avisos@pruebas.com").orElseThrow().getId();

        // Un pedido registrado hace 60 minutos que sigue en preparación (lo normal sin historial son 15 min)
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Pedido demorado = new TransactionTemplate(transacciones).execute(tx -> pedidos.guardar(new Pedido(null, 9,
                meseroId, "Usuario Avisos",
                List.of(new DetallePedido(null, producto, "Sancocho de prueba", 1, new BigDecimal("28000"), null)),
                null, EstadoCocina.EN_PREPARACION, EstadoPedido.ABIERTO, ahora.minusMinutes(60),
                ahora.minusMinutes(55), null, null, null)));
        String clave = "demora-" + demorado.getId();

        assertTrue(avisos(login("cocina.avisos@pruebas.com", "Clave2026x")).contains(clave));
        assertTrue(avisos(login("mesero.avisos@pruebas.com", "Clave2026x")).contains(clave));
        assertFalse(avisos(login("otro.avisos@pruebas.com", "Clave2026x")).contains(clave));
        assertFalse(avisos(login("inventario.avisos@pruebas.com", "Clave2026x")).contains(clave));
        assertTrue(avisos(admin).contains(clave));
    }

    @Test
    @DisplayName("Sin sesión no hay avisos")
    void sinSesion() throws Exception {
        mvc.perform(get("/api/avisos")).andExpect(status().isUnauthorized());
    }
}
