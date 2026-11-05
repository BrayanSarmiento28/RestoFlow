package com.udec.restoflow.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validación de la lógica de la aplicación (recomendación del comité): las reglas del negocio probadas de punta a
 * punta, por el API real (seguridad, controladores, casos de uso, dominio y JPA con H2), con cada rol.
 * <p>
 * Cada prueba usa sus propias mesas (40 en adelante) para no depender de las otras.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReglasDeNegocioIntegracionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String admin;
    private String mesero;
    private String cocina;
    /** JUnit crea un objeto por prueba: lo que se prepara una sola vez va en campos estáticos. */
    private static long producto;
    private static boolean preparado;

    private String login(String email, String password) throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("token").asText();
    }

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, String token, String cuerpo) throws Exception {
        peticion.header("Authorization", "Bearer " + token);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(peticion);
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    /** Crea las cuentas y el producto una sola vez (el contexto se comparte entre las pruebas de esta clase). */
    @BeforeEach
    void preparar() throws Exception {
        admin = login("admin@pruebas.com", "Admin2026*");
        if (!preparado) {
            enviar(post("/api/usuarios"), admin, "{\"nombre\":\"Mesero Reglas\",\"email\":\"mesero.reglas@pruebas.com\","
                    + "\"password\":\"Mesero2026\",\"rol\":\"MESERO\"}");
            enviar(post("/api/usuarios"), admin, "{\"nombre\":\"Cocina Reglas\",\"email\":\"cocina.reglas@pruebas.com\","
                    + "\"password\":\"Cocina2026\",\"rol\":\"COCINA\"}");
            long categoria = leer(enviar(post("/api/categorias"), admin, "{\"nombre\":\"Reglas\"}")).get("id").asLong();
            producto = leer(enviar(post("/api/productos"), admin,
                    "{\"nombre\":\"Arepa reglas\",\"precio\":8000,\"categoriaId\":" + categoria + "}")).get("id").asLong();
            preparado = true;
        }
        mesero = login("mesero.reglas@pruebas.com", "Mesero2026");
        cocina = login("cocina.reglas@pruebas.com", "Cocina2026");
    }

    private String pedidoEnMesa(int mesa) {
        return "{\"numeroMesa\":" + mesa + ",\"lineas\":[{\"productoId\":" + producto + ",\"cantidad\":1}]}";
    }

    private long registrar(int mesa) throws Exception {
        return leer(enviar(post("/api/pedidos"), mesero, pedidoEnMesa(mesa)).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private void cocinaHasta(long pedido, String... estados) throws Exception {
        for (String estado : estados) {
            enviar(patch("/api/cocina/pedidos/" + pedido + "/estado"), cocina, "{\"estado\":\"" + estado + "\"}")
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("RN · Mesa ocupada: no recibe otro pedido (409 con el pedido que la ocupa) hasta que se cobre")
    void mesaOcupadaHastaCobrar() throws Exception {
        long pedido = registrar(41);

        enviar(post("/api/pedidos"), mesero, pedidoEnMesa(41))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("La mesa 41 está ocupada (la atiende Mesero Reglas)")))
                .andExpect(jsonPath("$.mensaje", org.hamcrest.Matchers.not(containsString("#"))));   // sin número
        enviar(get("/api/pedidos/mesas-ocupadas"), mesero, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numeroMesa == 41)].pedidoId", hasItem((int) pedido)));

        // Sigue ocupada aunque ya se haya entregado: se libera al cobrar
        cocinaHasta(pedido, "EN_PREPARACION", "LISTO", "ENTREGADO");
        enviar(post("/api/pedidos"), mesero, pedidoEnMesa(41)).andExpect(status().isConflict());
        enviar(patch("/api/pedidos/" + pedido + "/pagar"), mesero, "{\"metodoPago\":\"EFECTIVO\"}")
                .andExpect(status().isForbidden());                         // el mesero pasa la cuenta, no cobra
        enviar(patch("/api/pedidos/" + pedido + "/pagar"), admin, "{\"metodoPago\":\"BITCOIN\"}")
                .andExpect(status().isBadRequest());                        // solo efectivo o transferencia
        enviar(patch("/api/pedidos/" + pedido + "/pagar"), admin, "{\"metodoPago\":\"TRANSFERENCIA\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPedido").value("PAGADO"))
                .andExpect(jsonPath("$.metodoPago").value("TRANSFERENCIA"));

        registrar(41);                                                     // libre otra vez
    }

    @Test
    @DisplayName("RN · Al cancelar el pedido, la mesa queda libre")
    void mesaLibreAlCancelar() throws Exception {
        long pedido = registrar(42);
        enviar(patch("/api/pedidos/" + pedido + "/cancelar"), mesero, null).andExpect(status().isOk());
        registrar(42);
    }

    @Test
    @DisplayName("RN · Cambiar de mesa: solo a una mesa libre; la anterior se libera; la cocina no puede hacerlo")
    void cambiarDeMesa() throws Exception {
        long pedido = registrar(43);
        registrar(44);
        cocinaHasta(pedido, "EN_PREPARACION");                              // se puede en cualquier momento

        enviar(patch("/api/pedidos/" + pedido + "/mesa"), mesero, "{\"numeroMesa\":44}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("La mesa 44 está ocupada")));
        enviar(patch("/api/pedidos/" + pedido + "/mesa"), cocina, "{\"numeroMesa\":45}")
                .andExpect(status().isForbidden());
        enviar(patch("/api/pedidos/" + pedido + "/mesa"), mesero, "{\"numeroMesa\":0}")
                .andExpect(status().isBadRequest());

        enviar(patch("/api/pedidos/" + pedido + "/mesa"), mesero, "{\"numeroMesa\":45}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroMesa").value(45))
                .andExpect(jsonPath("$.estadoCocina").value("EN_PREPARACION"));
        registrar(43);                                                     // la 43 quedó libre
    }

    @Test
    @DisplayName("RN · Estados en orden: no se cancela ni modifica lo que ya se prepara y no se cobra lo no entregado")
    void reglasDeEstados() throws Exception {
        long pedido = registrar(46);

        enviar(patch("/api/pedidos/" + pedido + "/pagar"), admin, null).andExpect(status().isConflict());
        cocinaHasta(pedido, "EN_PREPARACION");
        enviar(patch("/api/pedidos/" + pedido + "/cancelar"), mesero, null).andExpect(status().isConflict());
        enviar(put("/api/pedidos/" + pedido), mesero, pedidoEnMesa(46))
                .andExpect(status().isConflict())                           // en cocina solo se cambia la mesa
                .andExpect(jsonPath("$.mensaje", containsString("Cambiar mesa")));
        enviar(patch("/api/cocina/pedidos/" + pedido + "/estado"), cocina, "{\"estado\":\"ENTREGADO\"}")
                .andExpect(status().isConflict());                          // no salta de En preparación a Entregado
        cocinaHasta(pedido, "LISTO", "ENTREGADO");
        enviar(patch("/api/pedidos/" + pedido + "/pagar"), admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.estadoPedido").value("PAGADO"));
        enviar(patch("/api/pedidos/" + pedido + "/pagar"), admin, null).andExpect(status().isConflict());
        enviar(patch("/api/pedidos/" + pedido + "/mesa"), mesero, "{\"numeroMesa\":47}")
                .andExpect(status().isConflict());                          // un pedido cobrado ya no se mueve
    }

    @Test
    @DisplayName("RN · Datos inválidos: mesa fuera de rango, cantidad 0 o producto inexistente")
    void datosInvalidos() throws Exception {
        enviar(post("/api/pedidos"), mesero, pedidoEnMesa(0)).andExpect(status().isBadRequest());
        enviar(post("/api/pedidos"), mesero, pedidoEnMesa(1000)).andExpect(status().isBadRequest());
        enviar(post("/api/pedidos"), mesero,
                "{\"numeroMesa\":48,\"lineas\":[{\"productoId\":" + producto + ",\"cantidad\":0}]}")
                .andExpect(status().isBadRequest());
        enviar(post("/api/pedidos"), mesero, "{\"numeroMesa\":48,\"lineas\":[{\"productoId\":999999,\"cantidad\":1}]}")
                .andExpect(status().isNotFound());
        registrar(48);                                                     // los intentos fallidos no la ocuparon
    }

    @Test
    @DisplayName("RN · Los pedidos se numeran por día: cada pedido nuevo recibe el número siguiente del día")
    void numeroDelDia() throws Exception {
        JsonNode primero = leer(enviar(post("/api/pedidos"), mesero, pedidoEnMesa(49)).andExpect(status().isCreated()));
        JsonNode segundo = leer(enviar(post("/api/pedidos"), mesero, pedidoEnMesa(50)).andExpect(status().isCreated()));

        int n = primero.get("numeroDelDia").asInt();
        org.junit.jupiter.api.Assertions.assertTrue(n >= 1);
        org.junit.jupiter.api.Assertions.assertEquals(n + 1, segundo.get("numeroDelDia").asInt());
    }
}
