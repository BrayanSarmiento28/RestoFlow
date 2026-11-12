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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las operaciones de consulta y edición del API que no recorren los flujos principales: editar productos e insumos,
 * ajustar un conteo, atender una alerta, consultar y modificar un pedido, y la lista del personal.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:restoflow_api;MODE=MSSQLServer;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiCompletaIntegracionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String admin;

    @BeforeEach
    void iniciarSesion() throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@pruebas.com\",\"password\":\"Admin2026*\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        admin = json.readTree(respuesta).get("token").asText();
    }

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, String cuerpo) throws Exception {
        peticion.header("Authorization", "Bearer " + admin);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(peticion);
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Catálogo: crear, consultar, editar y retirar un producto")
    void catalogo() throws Exception {
        long categoria = leer(enviar(post("/api/categorias"), "{\"nombre\":\"Sopas API\"}")
                .andExpect(status().isCreated())).get("id").asLong();
        enviar(get("/api/categorias"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Sopas API')]").exists());

        long producto = leer(enviar(post("/api/productos"), "{\"nombre\":\"Sancocho API\",\"descripcion\":\"De gallina\","
                + "\"precio\":25000,\"categoriaId\":" + categoria + "}").andExpect(status().isCreated())).get("id").asLong();
        enviar(get("/api/productos/" + producto), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Sancocho API"));

        enviar(put("/api/productos/" + producto), "{\"nombre\":\"Sancocho trifásico\",\"descripcion\":\"Res, cerdo y pollo\","
                + "\"precio\":29000,\"categoriaId\":" + categoria + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Sancocho trifásico"))
                .andExpect(jsonPath("$.precio").value(29000));
        enviar(patch("/api/productos/" + producto + "/disponibilidad"), "{\"activo\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    @DisplayName("Inventario: editar, desactivar, ajustar el conteo y atender la alerta que crea quedar en cero")
    void inventario() throws Exception {
        long insumo = leer(enviar(post("/api/inventario/ingredientes"),
                "{\"nombre\":\"Yuca API\",\"unidad\":\"KILOGRAMO\",\"stockMinimo\":2,\"stockInicial\":10}")
                .andExpect(status().isCreated())).get("id").asLong();

        enviar(put("/api/inventario/ingredientes/" + insumo),
                "{\"nombre\":\"Yuca criolla API\",\"unidad\":\"KILOGRAMO\",\"stockMinimo\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Yuca criolla API"))
                .andExpect(jsonPath("$.stockMinimo").value(3));
        enviar(get("/api/inventario/ingredientes"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Yuca criolla API')]").exists());

        enviar(patch("/api/inventario/ingredientes/" + insumo + "/estado"), "{\"activo\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        enviar(patch("/api/inventario/ingredientes/" + insumo + "/estado"), "{\"activo\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));

        // El conteo físico encontró 0: el stock queda en cero y se crea una alerta
        enviar(post("/api/inventario/ingredientes/" + insumo + "/ajustes"), "{\"stockContado\":0,\"motivo\":\"Se dañó\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockActual").value(0))
                .andExpect(jsonPath("$.agotado").value(true));

        JsonNode alertas = leer(enviar(get("/api/alertas"), null).andExpect(status().isOk()));
        long alerta = -1;
        for (JsonNode a : alertas) {
            if (a.get("mensaje").asText().contains("Yuca criolla API")) {
                alerta = a.get("id").asLong();
            }
        }
        enviar(patch("/api/alertas/" + alerta + "/atender"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alerta));
    }

    @Test
    @DisplayName("Pedidos: consultar uno por su id y modificarlo mientras sigue pendiente")
    void pedido() throws Exception {
        long categoria = leer(enviar(post("/api/categorias"), "{\"nombre\":\"Bebidas API\"}")).get("id").asLong();
        long jugo = leer(enviar(post("/api/productos"), "{\"nombre\":\"Jugo de lulo API\",\"precio\":7000,"
                + "\"categoriaId\":" + categoria + "}")).get("id").asLong();

        long pedido = leer(enviar(post("/api/pedidos"), "{\"numeroMesa\":41,\"lineas\":[{\"productoId\":" + jugo
                + ",\"cantidad\":1}]}").andExpect(status().isCreated())).get("id").asLong();
        enviar(get("/api/pedidos/" + pedido), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroMesa").value(41));

        enviar(put("/api/pedidos/" + pedido), "{\"numeroMesa\":41,\"lineas\":[{\"productoId\":" + jugo
                + ",\"cantidad\":3,\"nota\":\"Sin azúcar\"}],\"observaciones\":\"Para llevar\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(21000))
                .andExpect(jsonPath("$.observaciones").value("Para llevar"));
        enviar(get("/api/pedidos/999999"), null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Personal y cocina: la lista de cuentas y el stock de una comanda sin productos")
    void personalYCocina() throws Exception {
        enviar(get("/api/usuarios"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'admin@pruebas.com')]").exists());
        enviar(get("/api/cocina/stock"), null).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        enviar(get("/api/no-existe"), null).andExpect(status().isNotFound());
    }
}
