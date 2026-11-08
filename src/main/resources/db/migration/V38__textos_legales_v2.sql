-- P12 bloque 4: versión 2 de la política de datos y de los términos, con todo lo que la app hace
-- hoy (fotos de visitantes y de quien recoge la correspondencia, citofonía y chat, visitantes
-- frecuentes, parqueaderos, pagos en línea, contador, personal, guardias con PIN, proveedores).
-- Al ser una versión nueva, cada usuario la vuelve a aceptar al entrar. El texto es el mismo de
-- la app (res/raw/privacidad.txt y terminos.txt). Idempotente.

INSERT INTO textos_legales (tipo, version, titulo, contenido, vigente_desde)
SELECT 'POLITICA_PRIVACIDAD', 2, 'Política de tratamiento de datos personales',
       'Versión 2 · Última actualización: 7 de noviembre de 2026.

Esta política explica cómo se tratan los datos personales en MiConjunto, conforme a la Ley 1581 de 2012, el Decreto 1377 de 2013 (compilado en el Decreto 1074 de 2015) y la Ley 675 de 2001 de propiedad horizontal.

# 1. Quién es responsable
- Responsable del tratamiento: la administración de cada conjunto residencial que usa MiConjunto. Decide qué datos se registran y atiende las consultas y reclamos de sus titulares.
- Encargado del tratamiento: el equipo de MiConjunto, que desarrolla y opera la plataforma y trata los datos solo por cuenta de la administración y según sus instrucciones.

# 2. Qué datos se tratan
- Residentes: nombre, número de identificación, teléfono, si es propietario o arrendatario, su apartamento y el estado de su cuenta.
- Acceso: usuario y contraseña (guardada cifrada, nunca en texto legible), código del conjunto y un identificador del celular para mantener la sesión.
- Visitas en portería: nombre y documento del visitante, placa si llega en vehículo, motivo, a qué apartamento va, quién autorizó la entrada, la hora de entrada y de salida, y una foto del rostro si la portería la toma.
- Visitantes frecuentes: nombre, documento y teléfono de las personas que el residente autoriza para entrar sin preguntar.
- Correspondencia: remitente, tipo, foto del paquete, y nombre, documento y foto de quien la recoge.
- Chats con la portería: los mensajes que se escriben cuando se rechaza una visita o una entrega.
- Vehículos y parqueaderos: placa, tipo, marca, color y parqueadero asignado.
- Pagos y cartera: cuotas, pagos, comprobantes y la referencia del pago en línea. MiConjunto no recibe ni guarda los datos de tarjetas o cuentas bancarias: los maneja la pasarela de pagos.
- Reservas de zonas comunes, obras en los apartamentos y su personal externo (nombre, cédula y estado de la ARL).
- Personal del conjunto (porteros, aseo, mantenimiento): nombre, documento, cargo, turnos y nómina.
- Guardias de portería: nombre y un PIN personal (guardado cifrado) para abrir su turno, y lo que anotan en la minuta.
- Datos técnicos: el identificador del celular para enviar avisos, solo si los avisos están activados.

# 3. Para qué se usan
- Identificar a los residentes y dar acceso a la app de su apartamento.
- Controlar quién entra y sale del conjunto: anunciar visitas, que el residente las autorice, contar el tiempo que llevan dentro y registrar su salida.
- Recibir la correspondencia y entregarla solo a quien el residente autorice.
- Gestionar parqueaderos, reservas, obras, cartera, pagos y el personal del conjunto.
- Enviar avisos sobre el apartamento y el conjunto.
Los datos no se venden ni se usan para publicidad.

# 4. Fotos de visitantes y de quien recoge la correspondencia
La portería puede tomar una foto del rostro del visitante o de quien recoge un paquete, solo para que el residente sepa a quién autoriza. La ven únicamente la portería, la administración y el apartamento al que va la visita o el paquete. Se guardan en un almacenamiento privado y se muestran con enlaces que vencen a los 15 minutos. Si no quieres que te tomen foto, puedes pedirlo en la portería y el residente te autorizará por tu nombre.

# 5. Cámara, notificaciones y alerta en pantalla completa
- Cámara: la app abre la cámara del celular solo cuando la portería o el residente deciden tomar una foto.
- Notificaciones: se usan para avisarte de visitas, correspondencia, pagos y comunicados. Puedes desactivarlas en Cuenta → Ajustes.
- Alerta en pantalla completa: cuando alguien llega a la portería o quiere recoger tu correspondencia, el celular suena como una alarma hasta que respondas o pasen 5 minutos.

# 6. Huella o rostro
MiConjunto nunca recibe ni guarda tu huella o tu rostro: los verifica el propio celular. Si activas esta opción, se guarda en tu celular una credencial cifrada que se desbloquea con tu huella o rostro. Puedes desactivarla en cualquier momento en Cuenta → Ajustes.

# 7. Con quién se comparten
Solo con proveedores que MiConjunto necesita para funcionar, que tratan los datos por su cuenta:
- Render: el servidor de la aplicación.
- Supabase: la base de datos y el almacenamiento privado de las fotos.
- Firebase Cloud Messaging (Google): el envío de notificaciones al celular.
- Wompi: los pagos en línea, si el conjunto los usa.
Estos servicios pueden guardar datos en servidores fuera de Colombia, con medidas de seguridad adecuadas. La información de un conjunto no la ven los demás conjuntos.

# 8. Cuánto tiempo se guardan
Mientras el apartamento o la persona tenga relación con el conjunto y lo que exijan las normas contables y de propiedad horizontal. El chat de una visita o entrega rechazada solo se puede usar durante 30 minutos, aunque queda en el registro. Cuando un arrendatario se va, la administración puede desactivar o eliminar su cuenta.

# 9. Datos de terceros
Quien registra a otras personas (visitantes frecuentes, residentes de su hogar, personal de obra o del conjunto) debe contar con su autorización para compartir sus datos con la administración.

# 10. Tus derechos
Como titular de los datos puedes:
- Conocer, actualizar y rectificar tus datos.
- Pedir prueba de la autorización que diste.
- Ser informado sobre el uso que se les ha dado.
- Revocar la autorización o pedir que se eliminen, cuando no exista un deber legal o contractual de conservarlos.
- Presentar quejas ante la Superintendencia de Industria y Comercio.
Para ejercerlos usa Cuenta → Legal → Mis datos personales, o comunícate con la administración del conjunto. Las consultas se responden en máximo 10 días hábiles y los reclamos en máximo 15 días hábiles.

# 11. Seguridad
Las comunicaciones con el servidor van cifradas (HTTPS), las contraseñas y los PIN se guardan cifrados, las fotos están en un almacenamiento privado y cada usuario solo puede ver la información que le corresponde.

# 12. Menores de edad
La app no está dirigida a menores de edad. Si un menor aparece como visitante o residente, sus datos se tratan respetando su interés superior y con autorización de su representante.

# 13. Cambios a esta política
Cuando cambie, la app te mostrará la nueva versión para que la aceptes, y estará en Cuenta → Legal con su fecha de actualización.',
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM textos_legales WHERE tipo = 'POLITICA_PRIVACIDAD' AND version = 2);

INSERT INTO textos_legales (tipo, version, titulo, contenido, vigente_desde)
SELECT 'TERMINOS', 2, 'Términos y condiciones de uso',
       'Versión 2 · Última actualización: 7 de noviembre de 2026.

Estos términos regulan el uso de MiConjunto, las aplicaciones para administrar el conjunto residencial: la de residentes, la de administración, la de portería y la de plataforma. Al iniciar sesión aceptas estas condiciones.

# 1. Quién presta el servicio
La administración de cada conjunto pone la app a disposición de sus residentes y de su personal, y es la responsable de las cuentas, de la información registrada y de responder las solicitudes. El equipo de MiConjunto desarrolla y opera la plataforma.

# 2. Acceso y cuentas
- Para entrar por primera vez se pide el código del conjunto que entrega la administración.
- Cada residente entra con la torre, el apartamento y su contraseña personal. La administración, el contador y la portería entran con su usuario; los guardias abren su turno con su PIN.
- Eres responsable de no compartir tu contraseña o tu PIN y de cambiarlos si crees que otra persona los conoce.
- Si activas la entrada con huella o rostro, cualquier persona cuya huella o rostro esté registrado en tu celular podrá entrar a tu cuenta.

# 3. Uso permitido
Puedes usar la app para los asuntos de tu apartamento y del conjunto. No está permitido:
- Registrar información falsa o de otras personas sin su autorización.
- Intentar entrar a cuentas de otros apartamentos, de otros conjuntos o a funciones que no te corresponden.
- Usar los chats o los avisos para molestar a otros residentes o al personal del conjunto.
La administración puede suspender o desactivar las cuentas que hagan un uso indebido.

# 4. Visitas y portería
- Cuando la portería anuncia una visita, tienes 5 minutos para autorizarla o rechazarla. Si no respondes, la portería puede llamarte y registrar tu respuesta.
- Los visitantes frecuentes que registres entran sin preguntarte; te llega el aviso de su entrada.
- La app muestra cuánto tiempo lleva dentro cada visitante. Si ya se fue y la portería no registró su salida, puedes darla desde Herramientas → Visitantes activos, y la portería recibe el aviso.

# 5. Correspondencia
La portería solo entrega tu correspondencia a quien tú autorices desde la app o por teléfono. Al autorizar la entrega aceptas que se entregue a esa persona. Si la rechazas, puedes escribir a la portería por el chat durante 30 minutos.

# 6. Reservas, obras, vehículos y parqueaderos
Las reservas, obras, vehículos y arrendatarios quedan pendientes hasta que la administración los aprueba. Registrar algo en la app no reemplaza los permisos que exija el reglamento de propiedad horizontal del conjunto (Ley 675 de 2001).

# 7. Cartera y pagos
Los saldos que muestra la app son informativos; en caso de diferencia prima el estado de cuenta oficial de la administración. Los cambios del contador a la cartera los aprueba la administración. Los pagos en línea los procesa la pasarela de pagos con sus propias condiciones; un pago se refleja cuando la pasarela o la administración lo confirman.

# 8. Disponibilidad
Se procura que la app funcione siempre, pero puede haber interrupciones por mantenimiento o fallas de conexión. Ante una urgencia, comunícate directamente con la portería o la administración.

# 9. Datos personales
El tratamiento de tus datos se rige por la Política de tratamiento de datos personales, en Cuenta → Legal.

# 10. Cambios a estos términos
Estos términos pueden cambiar. Cuando ocurra, la app te mostrará la nueva versión para que la aceptes, y estará en Cuenta → Legal con su fecha de actualización.',
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM textos_legales WHERE tipo = 'TERMINOS' AND version = 2);
