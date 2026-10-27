package com.residencial.correo;

import com.residencial.model.Residente;
import org.springframework.web.util.HtmlUtils;

/** Textos de los correos a los residentes (P5). */
public final class Plantillas {

    private Plantillas() {}

    public static String asuntoRecibido() {
        return "Recibimos tu registro en MiConjunto";
    }

    public static String recibido(Residente r) {
        return pagina(r, "Recibimos tu registro como <b>" + rol(r) + "</b> de " + apto(r) + ".",
                r.esArrendatario()
                        ? "Queda pendiente de aprobación por parte de la administración y del propietario. Te avisaremos por este medio."
                        : "Queda pendiente de aprobación por parte de la administración. Te avisaremos por este medio.");
    }

    public static String asuntoAprobado() {
        return "Tu registro en MiConjunto fue aprobado";
    }

    public static String aprobado(Residente r) {
        return pagina(r, "La administración aprobó tu registro como <b>" + rol(r) + "</b> de " + apto(r) + ".",
                r.puedeEntrar()
                        ? "Ya puedes entrar a la app con tu torre, apartamento y contraseña."
                        : "Falta la aprobación del propietario; te avisaremos cuando puedas entrar.");
    }

    public static String asuntoRechazado() {
        return "Tu registro en MiConjunto no fue aprobado";
    }

    public static String rechazado(Residente r, String motivo) {
        return pagina(r, "La administración no aprobó tu registro como " + rol(r) + " de " + apto(r) + ".",
                motivo == null ? "Si crees que es un error, comunícate con la administración."
                        : "Motivo: " + HtmlUtils.htmlEscape(motivo));
    }

    private static String pagina(Residente r, String linea1, String linea2) {
        return "<div style=\"font-family:sans-serif;font-size:15px;color:#222\">"
                + "<p>Hola, " + HtmlUtils.htmlEscape(r.getNombreCompleto()) + ":</p>"
                + "<p>" + linea1 + "</p><p>" + linea2 + "</p>"
                + "<p style=\"color:#777\">MiConjunto</p></div>";
    }

    private static String rol(Residente r) {
        return r.esArrendatario() ? "arrendatario" : "propietario";
    }

    private static String apto(Residente r) {
        return "Torre " + HtmlUtils.htmlEscape(r.getApartamento().getTorre())
                + " Apto " + HtmlUtils.htmlEscape(r.getApartamento().getApto());
    }
}
