# Correos a los residentes (Brevo)

Desde P5 el backend envía correos cuando un residente se registra desde la app, y cuando la
administración aprueba o rechaza su registro. Usa la API de Brevo (antes Sendinblue): el plan
gratuito permite 300 correos al día, suficiente para un conjunto.

Sin configurarlo, todo funciona igual: el correo solo queda en el log y el residente recibe el
aviso por la app.

## 1. Crear la cuenta (una vez)

1. Regístrate en <https://www.brevo.com> (plan Free).
2. **Senders, Domains & Dedicated IPs → Senders → Add a sender**: el correo desde el que
   saldrán los mensajes (por ejemplo, el de la administración). Brevo envía un enlace para
   confirmarlo.
3. **SMTP & API → API Keys → Generate a new API key**. Cópiala: solo se muestra una vez.

## 2. Variables en Render

| Variable | Valor |
|---|---|
| `BREVO_API_KEY` | la clave del paso 3 (**secreta**) |
| `CORREO_REMITENTE` | el correo confirmado del paso 2 |
| `CORREO_NOMBRE` | opcional; por defecto `MiConjunto` |

Al reiniciar, el log dice `Correos por Brevo desde ...`. Si dice "Correos solo en el log",
falta alguna variable.
