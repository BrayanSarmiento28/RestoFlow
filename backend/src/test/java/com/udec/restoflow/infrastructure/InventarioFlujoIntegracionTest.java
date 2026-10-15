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

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración del Sprint 4 con el API completo (seguridad, casos de uso y JPA con H2):
 * insumos, receta, venta que descuenta inventario, alertas, panel e historial, y quién puede hacer qué.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventarioFlujoIntegracionTest {

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
    @DisplayName("Inventario de punta a punta: insumo, receta, venta con descuento automático, alertas, panel e historial")
    void flujoDeInventario() throws Exception {
        String admin = login("admin@pruebas.com", "Admin2026*");
        String hoy = LocalDate.now().toString();

        enviar(post("/api/usuarios"), admin, "{\"nombre\":\"Bodega Prueba\",\"email\":\"bodega@pruebas.com\","
                + "\"password\":\"Bodega2026\",\"rol\":\"INVENTARIO\"}").andExpect(status().isCreated());
        enviar(post("/api/usuarios"), admin, "{\"nombre\":\"Mesero Inv\",\"email\":\"mesero.inv@pruebas.com\","
                + "\"password\":\"Mesero2026\",\"rol\":\"MESERO\"}").andExpect(status().isCreated());
        String bodega = login("bodega@pruebas.com", "Bodega2026");
        String mesero = login("mesero.inv@pruebas.com", "Mesero2026");

        // RF-05 · El personal de inventario crea el insumo con 10 unidades (mínimo 4)
        long huevo = leer(enviar(post("/api/inventario/ingredientes"), bodega,
                "{\"nombre\":\"Huevo prueba\",\"unidad\":\"UNIDAD\",\"stockMinimo\":4,\"stockInicial\":10}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockActual").value(10))
                .andExpect(jsonPath("$.enMinimo").value(false))).get("id").asLong();

        // Validaciones y permisos: el mesero no entra al inventario ni al panel
        enviar(post("/api/inventario/ingredientes"), bodega, "{\"nombre\":\"\",\"unidad\":\"UNIDAD\",\"stockMinimo\":-1}")
                .andExpect(status().isBadRequest());
        enviar(get("/api/inventario/ingredientes"), mesero, null).andExpect(status().isForbidden());
        enviar(get("/api/panel/resumen"), bodega, null).andExpect(status().isForbidden());
        enviar(post("/api/inventario/ingredientes/" + huevo + "/salidas"), bodega, "{\"cantidad\":50,\"motivo\":\"Merma\"}")
                .andExpect(status().isConflict());

        // RF-06 · El administrador arma un producto cuya receta lleva 3 huevos
        long categoria = leer(enviar(post("/api/categorias"), admin, "{\"nombre\":\"Inventario prueba\"}")
                .andExpect(status().isCreated())).get("id").asLong();
        long tortilla = leer(enviar(post("/api/productos"), admin,
                "{\"nombre\":\"Tortilla prueba\",\"precio\":15000,\"categoriaId\":" + categoria + "}")
                .andExpect(status().isCreated())).get("id").asLong();
        enviar(put("/api/productos/" + tortilla + "/receta"), bodega,
                "{\"lineas\":[{\"ingredienteId\":" + huevo + ",\"cantidad\":3}]}").andExpect(status().isForbidden());
        enviar(put("/api/productos/" + tortilla + "/receta"), admin,
                "{\"lineas\":[{\"ingredienteId\":" + huevo + ",\"cantidad\":3}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ingredienteNombre").value("Huevo prueba"));
        enviar(get("/api/productos/" + tortilla + "/receta"), mesero, null).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Se venden 2 tortillas: el pedido pasa por cocina y se cobra
        long pedido = leer(enviar(post("/api/pedidos"), mesero,
                "{\"numeroMesa\":8,\"lineas\":[{\"productoId\":" + tortilla + ",\"cantidad\":2}]}")
                .andExpect(status().isCreated())).get("id").asLong();
        for (String estado : new String[]{"EN_PREPARACION", "LISTO", "ENTREGADO"}) {
            enviar(patch("/api/cocina/pedidos/" + pedido + "/estado"), admin, "{\"estado\":\"" + estado + "\"}")
                    .andExpect(status().isOk());
        }
        enviar(patch("/api/pedidos/" + pedido + "/pagar"), mesero, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPedido").value("PAGADO"));

        // 10 − 6 = 4 → queda en su mínimo y aparece la alerta
        enviar(get("/api/inventario/ingredientes/" + huevo), bodega, null)
                .andExpect(jsonPath("$.stockActual").value(4))
                .andExpect(jsonPath("$.enMinimo").value(true));
        JsonNode alertas = leer(enviar(get("/api/alertas"), bodega, null).andExpect(status().isOk()));
        long alerta = alertas.get(0).get("id").asLong();
        enviar(get("/api/alertas"), bodega, null)
                .andExpect(jsonPath("$[0].prioridad").value("MEDIA"))
                .andExpect(jsonPath("$[0].ingredienteNombre").value("Huevo prueba"));

        // RF-12 · El historial muestra la salida por la venta, hecha por el sistema y ligada al pedido
        enviar(get("/api/inventario/movimientos?ingredienteId=" + huevo + "&tipo=SALIDA&desde=" + hoy + "&hasta=" + hoy),
                admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].cantidad").value(6))
                .andExpect(jsonPath("$[0].pedidoId").value(pedido))
                .andExpect(jsonPath("$[0].usuarioNombre").value("Sistema"))
                .andExpect(jsonPath("$[0].motivo", startsWith("Venta del pedido #" + pedido)));

        // Llega mercancía: la alerta se cierra sola
        enviar(post("/api/inventario/ingredientes/" + huevo + "/entradas"), bodega, "{\"cantidad\":30,\"motivo\":\"Compra\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.stockActual").value(34));
        enviar(get("/api/alertas?soloPendientes=false"), admin, null)
                .andExpect(jsonPath("$[0].id").value(alerta))
                .andExpect(jsonPath("$[0].atendida").value(true));

        // RF-08 · Panel del día y RF-12 · historial de ventas
        enviar(get("/api/panel/resumen"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.ventas").value(30000))
                .andExpect(jsonPath("$.masVendidos[0].nombre").value("Tortilla prueba"));
        enviar(get("/api/historial/ventas?desde=" + hoy + "&hasta=" + hoy), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value(1))
                .andExpect(jsonPath("$.porDia[0].total").value(30000));
        enviar(get("/api/historial/pedidos?desde=" + hoy + "&hasta=" + hoy + "&estado=PAGADO"), admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(pedido));
        enviar(get("/api/historial/ventas?desde=" + hoy + "&hasta=2020-01-01"), admin, null)
                .andExpect(status().isBadRequest());
    }
}
