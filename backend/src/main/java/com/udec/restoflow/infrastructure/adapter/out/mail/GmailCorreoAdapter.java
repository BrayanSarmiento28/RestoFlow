package com.udec.restoflow.infrastructure.adapter.out.mail;

import com.udec.restoflow.application.port.out.EnviarCorreoPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;

/**
 * Adaptador de salida: implementa EnviarCorreoPort enviando un correo real por Gmail (SMTP).
 * Se activa con restoflow.correo.modo=gmail.
 */
@Component
@ConditionalOnProperty(name = "restoflow.correo.modo", havingValue = "gmail")
public class GmailCorreoAdapter implements EnviarCorreoPort {

    private static final Logger log = LoggerFactory.getLogger(GmailCorreoAdapter.class);

    private final JavaMailSender mailSender;
    private final String remitente;
    private final String nombreRemitente;

    public GmailCorreoAdapter(JavaMailSender mailSender,
                              @Value("${spring.mail.username}") String remitente,
                              @Value("${restoflow.correo.remitente-nombre:RestoFlow}") String nombreRemitente) {
        this.mailSender = mailSender;
        this.remitente = remitente;
        this.nombreRemitente = nombreRemitente;
    }

    @Override
    public void enviarCodigoRecuperacion(String destinatario, String nombre, String codigo, int minutosVigencia) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, "UTF-8");
            helper.setFrom(remitente, nombreRemitente);
            helper.setTo(destinatario);
            helper.setSubject("RestoFlow · Código para restablecer tu contraseña");
            helper.setText(plantilla(nombre, codigo, minutosVigencia), true);
            mailSender.send(mensaje);
            log.info("Código de recuperación enviado a {}", destinatario);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            log.error("No se pudo enviar el correo a " + destinatario, e);
            throw new CorreoNoEnviadoException(e);
        }
    }

    private String plantilla(String nombre, String codigo, int minutosVigencia) {
        return """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:480px;margin:auto;color:#1c1917">
                  <div style="background:#ea580c;color:#fff;padding:18px 24px;border-radius:12px 12px 0 0">
                    <h2 style="margin:0">RestoFlow</h2>
                  </div>
                  <div style="border:1px solid #e7e5e4;border-top:0;padding:24px;border-radius:0 0 12px 12px">
                    <p>Hola <strong>%s</strong>,</p>
                    <p>Recibimos una solicitud para restablecer la contraseña de tu cuenta. Usa este código:</p>
                    <p style="font-size:32px;font-weight:bold;letter-spacing:8px;text-align:center;
                              background:#fff1e8;color:#c2410c;padding:14px;border-radius:8px">%s</p>
                    <p>El código vence en <strong>%d minutos</strong> y solo se puede usar una vez.</p>
                    <p style="color:#78716c;font-size:13px">Si no solicitaste este cambio, ignora este mensaje:
                       tu contraseña actual sigue siendo válida.</p>
                  </div>
                </div>
                """.formatted(HtmlUtils.htmlEscape(nombre), codigo, minutosVigencia);
    }
}
