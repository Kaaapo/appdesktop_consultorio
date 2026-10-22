# Consultorio SV

<p align="center">
  <img alt="Trabajo cerrado" src="https://img.shields.io/badge/Trabajo%20cerrado-listo%20para%20usar-6B3A46?style=for-the-badge&labelColor=2C2426">
</p>

Programa de escritorio para el consultorio de **Stephany Valencia**, odontóloga y armonizadora orofacial. Lleva pacientes, citas, controles y los avisos por WhatsApp en un solo lugar, en este computador.

**Estado: trabajo cerrado.** La versión 1.0 está terminada y lista para usarse en el consultorio.

La interfaz está en español (Colombia) y las horas se muestran en formato de 12 horas, con *a. m.* y *p. m.*

## Qué hace

- **Pacientes.** Nombre, documento, fecha de nacimiento, edad, teléfono, procedimiento, número de citas, número de controles y fecha de registro. Un paciente se desactiva; no se borra. El historial se conserva y se puede reactivar.
- **Citas y controles.** Una cita suma al conteo. Un control no. Entre una hora y la siguiente hay hora y media de diferencia.
- **Horario.** De lunes a sábado, de 7:00 a. m. a 7:00 p. m., cada media hora y sin descanso al mediodía. El domingo el consultorio no abre: ese día no se puede agendar.
- **WhatsApp.** Abre el chat de la aplicación de Windows, pega el mensaje una vez y después la foto de la ubicación. No abre el navegador.
- **Mensajes.** Cada procedimiento tiene su plantilla. `{nombre}`, `{tipo}`, `{procedimiento}` y `{fecha}` se reemplazan al avisar. `{fecha}` incluye el día y la hora de la cita.
- **Reportes.** Listados en Excel y PDF, con cada cita y cada control. También se pueden importar pacientes desde una plantilla de Excel.
- **Apariencia.** Tema claro u oscuro, y tamaño de letra.

Hay un solo usuario: el administrador.

## Dónde quedan los datos

Todo se guarda en este equipo, en:

`%APPDATA%\ConsultorioSV\consultorio.db`

Ahí mismo, en `backups\`, el programa deja copias diarias. Cerrar la ventana no borra lo que ya se guardó. Solo se pierde lo que estaba escrito en un formulario y no se envió.

## Cómo usarlo

Si ya está empaquetado, abre:

`dist\Consultorio SV\Consultorio SV.exe`

## Cómo correrlo en desarrollo

Hace falta **JDK 22** (compila para Java 21) y Maven.

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-22"
mvn -q -DskipTests compile
mvn -f app/pom.xml javafx:run
```

Las pruebas del backend:

```powershell
mvn -f backend/pom.xml test
```

## Cómo generar el programa portable

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-22"
.\packaging\build-installer.ps1
```

Queda en `dist\Consultorio SV\`. Con `-Type exe` o `-Type msi` se arma un instalador; para eso hace falta WiX Toolset 3.x en el PATH.

## Cómo está armado

| Parte | Qué es |
| --- | --- |
| `backend` | Reglas, SQLite, reportes e importación. Aquí viven las pruebas. |
| `app` | Ventana JavaFX. |
| `packaging` | Icono y empaquetado con `jpackage`. |

Java 21, JavaFX 21.0.5, SQLite, Apache POI y OpenPDF.
