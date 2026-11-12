package com.udec.restoflow.infrastructure;

import com.udec.restoflow.application.port.out.EnviarCorreoPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-02 · Recuperar la contraseña con un código de 6 dígitos, por el API completo (seguridad, caso de uso y JPA). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:restoflow_recuperar;MODE=MSSQLServer;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecuperacionContrasenaIntegracionTest {

    private static final String ADMIN = "admin@pruebas.com";

    @Autowired
    private MockMvc mvc;

    /** El correo no sale: se lee el código que se habría enviado. */
    @MockitoBean
    private EnviarCorreoPort correo;

    private ResultActions json(String ruta, String cuerpo) throws Exception {
        return mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test
    @DisplayName("Pide el código, lo usa una vez para cambiar la contraseña e inicia sesión con la nueva")
    void recuperar() throws Exception {
        // Un correo que no existe recibe la misma respuesta (no se revela quién tiene cuenta)
        json("/api/auth/recuperar", "{\"email\":\"nadie@pruebas.com\"}").andExpect(status().isOk());
        json("/api/auth/recuperar", "{\"email\":\"" + ADMIN + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").exists());

        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(correo).enviarCodigoRecuperacion(eq(ADMIN), anyString(), codigo.capture(), anyInt());

        json("/api/auth/restablecer", "{\"email\":\"" + ADMIN + "\",\"codigo\":\"000000\",\"nuevaPassword\":\"Nueva2027*\"}")
                .andExpect(status().isBadRequest());
        json("/api/auth/restablecer", "{\"email\":\"" + ADMIN + "\",\"codigo\":\"" + codigo.getValue()
                + "\",\"nuevaPassword\":\"Nueva2027*\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Tu contraseña se actualizó correctamente. Ya puedes iniciar sesión."));

        // El código ya se usó y la contraseña nueva funciona
        json("/api/auth/restablecer", "{\"email\":\"" + ADMIN + "\",\"codigo\":\"" + codigo.getValue()
                + "\",\"nuevaPassword\":\"Otra2028*\"}")
                .andExpect(status().isBadRequest());
        json("/api/auth/login", "{\"email\":\"" + ADMIN + "\",\"password\":\"Nueva2027*\"}").andExpect(status().isOk());
    }
}
