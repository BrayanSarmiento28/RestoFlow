package com.udec.restoflow.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración: el API completo (controladores, seguridad JWT, casos de uso y JPA con H2)
 * recorriendo el flujo pedido → cocina → venta y las reglas de acceso por rol.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PedidoFlujoIntegracionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

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

    @Test
    @DisplayName("Pedido completo: mesero registra, cocina prepara y entrega, mesero cobra; cada rol solo hace lo suyo")
    void flujoCompletoConRoles() throws Exception {
        String admin = login("admin@pruebas.com", "Admin2026*");

        // El administrador arma el menú y crea las cuentas de mesero y cocina
        long categoria = leer(enviar(post("/api/categorias"), admin, "{\"nombre\":\"Platos de prueba\"}")
                .andExpect(status().isCreated())).get("id").asLong();
        long producto = leer(enviar(post("/api/productos"), admin,
                "{\"nombre\":\"Plato de prueba\",\"precio\":25000,\"categoriaId\":" + categoria + "}")
                .andExpect(status().isCreated())).get("id").asLong();
        enviar(post("/api/usuarios"), admin,
                "{\"nombre\":\"Mesero Prueba\",\"email\":\"mesero@pruebas.com\",\"password\":\"Mesero2026\",\"rol\":\"MESERO\"}")
                .andExpect(status().isCreated());
        enviar(post("/api/usuarios"), admin,
                "{\"nombre\":\"Cocina Prueba\",\"email\":\"cocina@pruebas.com\",\"password\":\"Cocina2026\",\"rol\":\"COCINA\"}")
                .andExpect(status().isCreated());
        String mesero = login("mesero@pruebas.com", "Mesero2026");
        String cocina = login("cocina@pruebas.com", "Cocina2026");

        // El mesero no puede crear productos ni entrar a la cocina
        enviar(post("/api/productos"), mesero,
                "{\"nombre\":\"Otro\",\"precio\":1000,\"categoriaId\":" + categoria + "}").andExpect(status().isForbidden());
        enviar(get("/api/cocina/comandas"), mesero, null).andExpect(status().isForbidden());

        // Registrar pedido: 2 × 25.000 = 50.000
        String pedidoJson = "{\"numeroMesa\":3,\"lineas\":[{\"productoId\":" + producto + ",\"cantidad\":2}]}";
        JsonNode pedido = leer(enviar(post("/api/pedidos"), mesero, pedidoJson)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoCocina").value("PENDIENTE"))
                .andExpect(jsonPath("$.meseroNombre").value("Mesero Prueba"))
                .andExpect(jsonPath("$.total").value(50000)));
        long id = pedido.get("id").asLong();

        // La cocina no puede registrar pedidos, pero sí los ve en sus comandas
        enviar(post("/api/pedidos"), cocina, pedidoJson).andExpect(status().isForbidden());
        enviar(get("/api/cocina/comandas"), cocina, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].lineas[0].productoNombre").value("Plato de prueba"));

        // Saltar de Pendiente a Listo no está permitido (409)
        enviar(patch("/api/cocina/pedidos/" + id + "/estado"), cocina, "{\"estado\":\"LISTO\"}")
                .andExpect(status().isConflict());
        enviar(patch("/api/cocina/pedidos/" + id + "/estado"), cocina, "{\"estado\":\"EN_PREPARACION\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.estadoCocina").value("EN_PREPARACION"));

        // Ya en preparación, el mesero no puede modificarlo
        enviar(put("/api/pedidos/" + id), mesero, pedidoJson).andExpect(status().isConflict());

        enviar(patch("/api/cocina/pedidos/" + id + "/estado"), cocina, "{\"estado\":\"LISTO\"}").andExpect(status().isOk());
        enviar(patch("/api/cocina/pedidos/" + id + "/estado"), cocina, "{\"estado\":\"ENTREGADO\"}").andExpect(status().isOk());

        // Entregado: sale de la cocina y el mesero registra la venta
        enviar(get("/api/cocina/comandas"), cocina, null).andExpect(jsonPath("$.length()").value(0));
        enviar(patch("/api/pedidos/" + id + "/pagar"), mesero, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.estadoPedido").value("PAGADO"));
    }

    @Test
    @DisplayName("Un pedido sin productos se rechaza con 400 y el campo que falló")
    void pedidoSinProductos() throws Exception {
        String admin = login("admin@pruebas.com", "Admin2026*");
        enviar(post("/api/pedidos"), admin, "{\"numeroMesa\":3,\"lineas\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.lineas").exists());
    }

    @Test
    @DisplayName("El estado del servicio es público (lo usa el healthcheck de Docker)")
    void saludPublica() throws Exception {
        mvc.perform(get("/api/salud")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("UP"));
    }

    @Test
    @DisplayName("Sin token no se puede consultar pedidos (401)")
    void sinToken() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
    }
}
