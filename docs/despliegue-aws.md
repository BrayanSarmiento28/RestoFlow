# Despliegue en AWS EC2 (RES-57)

RestoFlow se publica en **un servidor EC2 de AWS** con el mismo `docker-compose.yml` que se usa en el PC: SQL Server,
backend Spring Boot y frontend Angular con Nginx. Lo que cambia es el archivo `.env` del servidor.

```
 Navegador ──HTTP 80──► EC2 (Ubuntu + Docker)
                         ├─ frontend  Nginx: sirve Angular y reenvía /api ──┐
                         ├─ backend   Spring Boot :8080 (solo red interna) ◄┘
                         └─ db        SQL Server Express :1433 (solo red interna)
```

## 1. Decisiones (para la sustentación)

| Decisión | Por qué |
|---|---|
| **EC2 + Docker Compose** | Reutiliza la contenerización del proyecto (RES-54): lo que funciona en el PC funciona igual en el servidor (factor X de los 12 factores, paridad entre entornos). Las plataformas gratuitas como Render, Railway o Vercel no ejecutan SQL Server. |
| **Plan gratuito de AWS** | Cuenta nueva con 100 USD de crédito (hasta 200 USD completando actividades) por 6 meses; en el plan gratuito AWS no cobra. |
| **Instancia `m7i-flex.large`** (2 vCPU, 8 GB) | SQL Server necesita mínimo 2 GB de RAM. `t3.micro` (1 GB) y `t3.small` (2 GB) no alcanzan para la base de datos, el backend y Nginx juntos. `m7i-flex.large` está permitida en el plan gratuito y la pagan los créditos. |
| **SQL Server Express** (`MSSQL_PID=Express`) | La edición Developer que usa el PC solo tiene licencia para desarrollo y pruebas. Express es gratuita **y con licencia para producción** (límite de 10 GB por base de datos, de sobra para un restaurante). |
| **Solo el puerto 80 abierto al público** | El backend (8080) y la base de datos (1433) quedan solo dentro del servidor. El grupo de seguridad de AWS los bloquea, y además se publican en `127.0.0.1`. |
| **Secretos solo en el `.env` del servidor** | Se generan en el propio servidor con `openssl` y nunca se suben a GitHub (`.env` está en `.gitignore`). |
| **Región `us-east-1`** (Norte de Virginia) | La más económica y con todos los servicios. |

**Limitación conocida:** el sitio funciona con HTTP, sin candado. Para HTTPS se necesita un dominio propio y un
certificado (por ejemplo, Let's Encrypt). Se deja como mejora futura; para un restaurante real sería obligatorio,
porque el token de sesión viaja en cada petición.

## 2. Crear el servidor (consola de AWS)

1. Arriba a la derecha, elija la región **US East (N. Virginia)**.
2. **EC2 → Instances → Launch instances**:
   - **Name:** `restoflow`
   - **Imagen:** Ubuntu Server 24.04 LTS (64-bit x86)
   - **Instance type:** `m7i-flex.large`. Si no aparece como disponible en el plan gratuito, use `c7i-flex.large` (4 GB).
   - **Key pair:** *Create new key pair* → nombre `restoflow`, tipo RSA, formato `.pem`. Se descarga el archivo
     `restoflow.pem`: guárdelo en `C:\Users\BRAYAN\.ssh\` y **nunca lo suba a GitHub**.
   - **Network settings → Edit:**
     - *Allow SSH traffic from* → **My IP** (solo su conexión puede entrar por SSH).
     - Marque **Allow HTTP traffic from the internet** (puerto 80).
     - No abra 8080 ni 1433.
   - **Storage:** 30 GiB, gp3.
3. **Launch instance**. Al minuto aparece en *Running*.
4. **IP fija:** *EC2 → Elastic IPs → Allocate Elastic IP address → Allocate*. Luego, *Actions → Associate Elastic IP
   address* → elija la instancia `restoflow` → *Associate*. Esa IP no cambia aunque apague el servidor.
   En esta guía se llama `IP-DEL-SERVIDOR`.

## 3. Entrar al servidor desde PowerShell

La primera vez hay que dejar la llave solo con permisos para su usuario (si no, SSH la rechaza):

```powershell
icacls C:\Users\BRAYAN\.ssh\restoflow.pem /inheritance:r /grant:r "$($env:USERNAME):(R)"
ssh -i C:\Users\BRAYAN\.ssh\restoflow.pem ubuntu@IP-DEL-SERVIDOR
```

Cuando pregunte `Are you sure you want to continue connecting`, escriba `yes`. Los comandos de las secciones 4 a 6
se escriben **dentro del servidor** (la línea empieza con `ubuntu@ip-...:~$`).

## 4. Instalar Docker y descargar RestoFlow

```bash
sudo apt-get update && sudo apt-get upgrade -y
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit
```

Vuelva a entrar con el mismo `ssh` (para que el usuario quede en el grupo `docker`) y descargue el proyecto:

```bash
docker --version && docker compose version
git clone https://github.com/BrayanSarmiento28/RestoFlow.git
cd RestoFlow
git checkout preprod
```

La rama `preprod` debe tener la última versión de `develop`. Antes de este paso, haga un pull request de `develop`
hacia `preprod` en GitHub y fusiónelo (RES-58).

Si el repositorio es privado, Git pide usuario y contraseña. La contraseña es un **token** de GitHub: *Settings →
Developer settings → Fine-grained tokens*, con acceso solo a RestoFlow y permiso *Contents: Read-only*. No lo
comparta con nadie.

## 5. Configurar el `.env` del servidor

Se parte de la plantilla y se generan contraseñas aleatorias en el propio servidor:

```bash
cp .env.example .env
JWT=$(openssl rand -base64 48 | tr -d '\n/+=')
SA="Sa-$(openssl rand -hex 10)-X9"
APP="App-$(openssl rand -hex 10)-X9"
ADMIN="Admin$(openssl rand -hex 6)9"
IP=$(curl -s https://checkip.amazonaws.com)
sed -i \
  -e "s|^JWT_SECRET=.*|JWT_SECRET=$JWT|" \
  -e "s|^MSSQL_SA_PASSWORD=.*|MSSQL_SA_PASSWORD=$SA|" \
  -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$APP|" \
  -e "s|^ADMIN_PASSWORD=.*|ADMIN_PASSWORD=$ADMIN|" \
  -e "s|^MSSQL_PID=.*|MSSQL_PID=Express|" \
  -e "s|^SPRING_PROFILES_ACTIVE=.*|SPRING_PROFILES_ACTIVE=preprod|" \
  -e "s|^FRONTEND_PUERTO=.*|FRONTEND_PUERTO=80|" \
  -e "s|^BACKEND_PUERTO=.*|BACKEND_PUERTO=127.0.0.1:8080|" \
  -e "s|^DB_PUERTO=.*|DB_PUERTO=127.0.0.1:14330|" \
  -e "s|^CORS_ORIGINS=.*|CORS_ORIGINS=http://$IP|" \
  .env
chmod 600 .env
grep -E "^(ADMIN_EMAIL|ADMIN_PASSWORD|SPRING_PROFILES_ACTIVE|MSSQL_PID|FRONTEND_PUERTO|CORS_ORIGINS)=" .env
```

El último comando muestra el correo y la **contraseña del administrador**, que necesita para entrar a RestoFlow y
para las pruebas de carga. Las demás contraseñas solo las usan los contenedores.

| Variable | Valor en el servidor | Por qué |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `preprod` (luego `prod`, RES-58) | Pre-producción trae el menú de ejemplo para las pruebas |
| `MSSQL_PID` | `Express` | Licencia de producción gratuita |
| `FRONTEND_PUERTO` | `80` | La aplicación queda en `http://IP-DEL-SERVIDOR` |
| `BACKEND_PUERTO`, `DB_PUERTO` | `127.0.0.1:...` | Solo se puede llegar desde dentro del servidor |
| `CORS_ORIGINS` | `http://IP-DEL-SERVIDOR` | Origen permitido para el navegador |
| `GEMINI_API_KEY` | Opcional | Sin clave, RestoFlow AI funciona con sus reglas |
| `CORREO_MODO`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Opcional | Sin Gmail, la recuperación de contraseña no envía el correo; el resto funciona |

Para cambiar algo a mano: `nano .env` (guardar con `Ctrl+O`, `Enter`; salir con `Ctrl+X`).

## 6. Encender RestoFlow

```bash
docker compose up -d --build
```

La primera vez tarda unos 10 minutos: compila el backend con Maven y el frontend con Angular dentro de Docker, y
crea la base de datos con los scripts de `backend/database`. Para comprobarlo:

```bash
docker compose ps
curl -s http://localhost/api/salud
```

`db`, `backend` y `frontend` deben aparecer `Up` (`healthy`), y `db-init` como `Exited (0)` (terminó de crear la
base de datos). Luego abra **`http://IP-DEL-SERVIDOR`** en el navegador e inicie sesión con el administrador.

| Para | Comando (en el servidor, dentro de `RestoFlow`) |
|---|---|
| Ver los registros del backend | `docker compose logs -f backend` (salir con `Ctrl+C`) |
| Ver la auditoría | `docker compose exec backend tail -n 50 /app/logs/auditoria.log` |
| Actualizar a la última versión de la rama | `git pull && docker compose up -d --build` |
| Apagar RestoFlow (conserva los datos) | `docker compose down` |
| Uso de memoria y CPU | `docker stats --no-stream` |

## 7. Pruebas de carga contra el servidor (RES-56)

Desde **su PC**, en PowerShell y en la carpeta del proyecto, con Apache JMeter (ver
[pruebas de carga y estrés](pruebas/carga-y-estres.md)). El guion pide la contraseña del administrador del servidor
(la que mostró el paso 5) sin que quede escrita en ningún archivo:

```powershell
.\docs\pruebas\jmeter\ejecutar-jmeter.ps1 -Prueba humo -Servidor IP-DEL-SERVIDOR -Puerto 80
.\docs\pruebas\jmeter\ejecutar-jmeter.ps1 -Prueba carga -Servidor IP-DEL-SERVIDOR -Puerto 80
.\docs\pruebas\jmeter\ejecutar-jmeter.ps1 -Prueba estres -Servidor IP-DEL-SERVIDOR -Puerto 80
```

Así la medición incluye la red real entre el usuario y el servidor. Mientras corre la de estrés, en otra ventana
conectada por SSH, `docker stats` muestra cuánta CPU y memoria usa cada contenedor.

## 8. Pasar a producción (RES-58)

Cuando `preprod` esté validado y unido a `main`, y `main` tenga la etiqueta de la versión (por ejemplo `v1.1.1`):

```bash
cd ~/RestoFlow
git fetch
git checkout main
git pull
```

Luego se cambian cuatro valores del `.env` del servidor:

```bash
sed -i \
  -e "s|^SPRING_PROFILES_ACTIVE=.*|SPRING_PROFILES_ACTIVE=prod|" \
  -e "s|^RESTOFLOW_VERSION=.*|RESTOFLOW_VERSION=1.1.1|" \
  .env
grep -q "^SWAGGER_HABILITADO=" .env || echo "SWAGGER_HABILITADO=true" >> .env
```

| Variable | Valor | Por qué |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Sin menú de ejemplo, tablas solo por scripts SQL, registros de librerías solo desde `WARN` y correo real |
| `RESTOFLOW_VERSION` | `1.1.0` | Las imágenes quedan con la versión de la entrega: `restoflow-backend:1.1.0` |
| `SWAGGER_HABILITADO` | `true` | Para mostrar el API en la sustentación. En un restaurante real se deja en `false` |
| `MAIL_USERNAME` y `MAIL_PASSWORD` | la cuenta de Gmail y su contraseña de aplicación | En `prod` la recuperación de contraseña envía el código por correo real |

Las credenciales de Gmail **no se escriben en esta guía**: se copian a mano del `.env` del PC con `nano .env`,
igual que `GEMINI_API_KEY`. Después:

```bash
docker compose up -d --build
docker compose ps
curl -s http://localhost/api/salud; echo
```

Debe responder `"entorno":"produccion"`.

En `prod`:
- No se crea el menú de ejemplo. Si la base ya tiene datos (por ejemplo, el menú y el inventario que cargó
  `preprod`), se conservan: cambiar de perfil no borra nada.
- Las tablas solo se crean con los scripts SQL (`ddl-auto: none`).
- Swagger queda en `http://IP-DEL-SERVIDOR/swagger-ui.html` solo si `SWAGGER_HABILITADO=true`. Aunque se vea la
  documentación, cada operación sigue pidiendo iniciar sesión.

Para empezar producción con la base de datos vacía, **antes** del `up` se borra solo el volumen de SQL Server con
`docker compose down` y luego `docker volume rm restoflow_sqlserver_datos`. **Esto borra la base de datos para
siempre**, así que úselo solo si no hay nada que conservar.

## 9. Costos y cuidado de los créditos

- Cuenta en el **plan gratuito**: AWS no cobra. El servidor, la IP fija y el disco se descuentan de los créditos.
- La instancia gasta créditos **solo mientras está encendida**. Cuando no la use: *EC2 → Instances → restoflow →
  Instance state → Stop*. Al volver a encenderla (*Start*), Docker levanta RestoFlow solo
  (`restart: unless-stopped`) y la IP elástica sigue siendo la misma.
- La alerta de presupuesto (*Billing → Budgets*, plantilla *Zero spend budget*) avisa por correo si algo empieza a
  costar.
- **Al terminar el semestre:** *Terminate* la instancia y *Release* la IP elástica, para no seguir consumiendo
  créditos.
