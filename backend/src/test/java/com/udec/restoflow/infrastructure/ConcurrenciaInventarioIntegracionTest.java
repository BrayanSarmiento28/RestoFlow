package com.udec.restoflow.infrastructure;

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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RES-45 · Error encontrado con la prueba de carga (RES-56): cobros SIMULTÁNEOS de pedidos que usan el mismo insumo.
 * <p>
 * Antes, cada cobro leía el stock, restaba y guardaba sin reservar el insumo: si dos cobros leían a la vez, uno de los
 * descuentos se perdía (el inventario quedaba con más de lo real) o SQL Server cancelaba uno por deadlock (error 500).
 * Aquí se cobran 10 pedidos al mismo tiempo, en 10 hilos, y el stock debe bajar exactamente 10 unidades.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConcurrenciaInventarioIntegracionTest {

    private static final int COBROS = 10;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String login() throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@pruebas.com\",\"password\":\"Admin2026*\"}"))
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

    private long id(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    @DisplayName("10 cobros simultáneos con el mismo insumo: todos responden 200 y el stock baja exactamente 10")
    void cobrosSimultaneosNoPierdenDescuentos() throws Exception {
        String admin = login();

        // Un insumo con 100 unidades y un producto cuya receta usa 1 unidad
        long insumo = id(enviar(post("/api/inventario/ingredientes"), admin,
                "{\"nombre\":\"Pan concurrencia\",\"unidad\":\"UNIDAD\",\"stockMinimo\":1,\"stockInicial\":100}")
                .andExpect(status().isCreated()));
        long categoria = id(enviar(post("/api/categorias"), admin, "{\"nombre\":\"Concurrencia\"}")
                .andExpect(status().isCreated()));
        long producto = id(enviar(post("/api/productos"), admin,
                "{\"nombre\":\"Sanduche concurrencia\",\"precio\":9000,\"categoriaId\":" + categoria + "}")
                .andExpect(status().isCreated()));
        enviar(put("/api/productos/" + producto + "/receta"), admin,
                "{\"lineas\":[{\"ingredienteId\":" + insumo + ",\"cantidad\":1}]}").andExpect(status().isOk());

        // 10 pedidos entregados, cada uno en su mesa, listos para cobrar
        List<Long> pedidos = new ArrayList<>();
        for (int i = 0; i < COBROS; i++) {
            long pedido = id(enviar(post("/api/pedidos"), admin, "{\"numeroMesa\":" + (300 + i)
                    + ",\"lineas\":[{\"productoId\":" + producto + ",\"cantidad\":1}]}").andExpect(status().isCreated()));
            for (String estado : new String[]{"EN_PREPARACION", "LISTO", "ENTREGADO"}) {
                enviar(patch("/api/cocina/pedidos/" + pedido + "/estado"), admin, "{\"estado\":\"" + estado + "\"}")
                        .andExpect(status().isOk());
            }
            pedidos.add(pedido);
        }

        // Todos los hilos esperan la misma señal y cobran a la vez
        ExecutorService hilos = Executors.newFixedThreadPool(COBROS);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> respuestas = new ArrayList<>();
        for (long pedido : pedidos) {
            respuestas.add(hilos.submit(() -> {
                salida.await();
                return enviar(patch("/api/pedidos/" + pedido + "/pagar"), admin, "{\"metodoPago\":\"EFECTIVO\"}")
                        .andReturn().getResponse().getStatus();
            }));
        }
        salida.countDown();
        List<Integer> estados = new ArrayList<>();
        for (Future<Integer> r : respuestas) {
            estados.add(r.get(60, TimeUnit.SECONDS));
        }
        hilos.shutdown();

        assertThat(estados).as("ningún cobro falla").containsOnly(200);
        enviar(get("/api/inventario/ingredientes/" + insumo), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockActual").value(100 - COBROS));
    }
}
