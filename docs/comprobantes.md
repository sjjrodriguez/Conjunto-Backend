# Comprobantes de pago en Supabase Storage

Desde el bloque 7 de P4, los residentes suben la foto o el PDF del pago de cada cuota. El
backend los guarda en un **bucket privado** de Supabase Storage y los muestra con enlaces
firmados que vencen a los 10 minutos. La app nunca habla directo con Supabase.

## 1. Crear el bucket (una vez)

En el panel de Supabase: **Storage → New bucket**

- Nombre: `comprobantes`
- **Public bucket: desactivado** (es privado)
- Tamaño máximo de archivo: 5 MB
- Tipos permitidos: `image/jpeg, image/png, image/webp, application/pdf`

O desde el SQL Editor:

```sql
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('comprobantes', 'comprobantes', false, 5242880,
        array['image/jpeg', 'image/png', 'image/webp', 'application/pdf']);
```

No hacen falta políticas RLS: el backend usa la *service role key*, que las salta.

## 2. Variables de entorno en Render

| Variable | Valor |
|---|---|
| `SUPABASE_URL` | `https://<proyecto>.supabase.co` (Project Settings → API) |
| `SUPABASE_SERVICE_KEY` | la clave `service_role` (Project Settings → API). **Secreta**: solo en Render, nunca en la app ni en git |
| `STORAGE_BUCKET` | opcional; por defecto `comprobantes` |

Sin `SUPABASE_URL` y `SUPABASE_SERVICE_KEY`, el backend guarda los comprobantes en una
carpeta local (`STORAGE_LOCAL_DIR`, o la carpeta temporal). Eso solo sirve para la demo y las
pruebas: en Render el disco se borra en cada despliegue.

## 3. Comprobar

Al arrancar, el log dice `Comprobantes en Supabase Storage (bucket comprobantes)`. Si dice
"carpeta local", faltan las variables.
