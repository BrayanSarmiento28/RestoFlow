package com.udec.restoflow.infrastructure.mail;

import com.udec.restoflow.infrastructure.adapter.out.mail.CorreoConsolaAdapter;
import com.udec.restoflow.infrastructure.adapter.out.mail.CorreoNoEnviadoException;
import com.udec.restoflow.infrastructure.adapter.out.mail.GmailCorreoAdapter;
import com.udec.restoflow.infrastructure.config.RestoFlowProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU-02 · El código de recuperación sale por Gmail en producción y por la consola en desarrollo y pruebas. */
class CorreoAdaptersTest {

    private static final RestoFlowProperties PROPIEDADES = new RestoFlowProperties("pruebas", false, null, null,
            new RestoFlowProperties.Correo("gmail", "RestoFlow"), null, null, null);

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final GmailCorreoAdapter gmail = new GmailCorreoAdapter(mailSender, "restoflow@pruebas.com", PROPIEDADES);

    @Test
    @DisplayName("Gmail: envía un correo HTML con el código, al destinatario y con el remitente configurado")
    void enviaPorGmail() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        gmail.enviarCodigoRecuperacion("laura@restoflow.com", "Laura <b>", "482913", 15);

        ArgumentCaptor<MimeMessage> enviado = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(enviado.capture());
        MimeMessage correo = enviado.getValue();
        assertThat(correo.getAllRecipients()[0]).hasToString("laura@restoflow.com");
        assertThat(correo.getFrom()[0].toString()).contains("restoflow@pruebas.com").contains("RestoFlow");
        assertThat(correo.getSubject()).isEqualTo("RestoFlow · Código para restablecer tu contraseña");
        String cuerpo = correo.getContent().toString();
        assertThat(cuerpo).contains("482913").contains("15").contains("Laura &lt;b&gt;");   // el nombre no puede inyectar HTML
    }

    @Test
    @DisplayName("Gmail: si el correo no sale, se avisa con un mensaje claro para el usuario")
    void fallaGmail() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("SMTP caído")).when(mailSender).send(any(MimeMessage.class));

        CorreoNoEnviadoException e = assertThrows(CorreoNoEnviadoException.class,
                () -> gmail.enviarCodigoRecuperacion("laura@restoflow.com", "Laura", "482913", 15));
        assertThat(e).hasMessageContaining("No fue posible enviar el correo");
    }

    @Test
    @DisplayName("Consola: en desarrollo el código solo se escribe en el log")
    void consola() {
        CorreoConsolaAdapter consola = new CorreoConsolaAdapter();

        assertThatCode(() -> consola.enviarCodigoRecuperacion("laura@restoflow.com", "Laura", "482913", 15))
                .doesNotThrowAnyException();
    }
}
