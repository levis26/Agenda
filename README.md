# Agenda
# Estructura y Funcionamiento del Proyecto Agenda

## Estructura de Directorios

```plaintext

Agenda/

├── HELP.md

├── mvnw

├── mvnw.cmd

├── pom.xml

├── processToWork.txt

├── README.md

└── src/

├── main/

│   ├── java/

│   │   └── net/

│   │       └── elpuig/

│   │           └── Agenda/

│   │               ├── AgendaApplication.java

│   │               ├── controller/

│   │               │   └── AgendaController.java

│   │               ├── model/

│   │               │   └── Reserva.java

│   │               └── service/

│   │                   ├── AgendaProcessor.java

│   │                   └── DataLoader.java

│   └── resources/

│       ├── application.properties

│       ├── internacional.CAT.properties

│       ├── static/

│       │   └── css/

│       │       └── styles.css

│       └── templates/

│           ├── agenda.html

│           └── upload.html

└── test/

└── java/

└── net/

└── elpuig/

└── Agenda/

└── AgendaApplicationTests.java

```

---

## Explicación de Archivos Clave

### 1. `AgendaController.java`

- **Ubicación**: `src/main/java/net/elpuig/Agenda/controller/`

- **Función**:

Controlador Spring MVC que gestiona las rutas principales:

- `/upload`: Muestra el formulario de subida de archivos (`upload.html`).

- `/procesar`: Procesa los archivos `config.txt` y `peticiones.txt`.

- `/agenda`: Renderiza la agenda generada (`agenda.html`).

---

### 2. `Reserva.java`

- **Ubicación**: `src/main/java/net/elpuig/Agenda/model/`

- **Función**:

Modelo de datos que representa una reserva. Atributos:

- `nombreActividad`, `sala`, `fechaInicio`, `fechaFin`, `diasSemana`, `horarios`.

---

### 3. `AgendaProcessor.java`

- **Ubicación**: `src/main/java/net/elpuig/Agenda/service/`

- **Función**:

- Agrupa reservas válidas por sala.

- Detecta conflictos entre reservas.

- Genera la estructura de datos para la vista `agenda.html`.

---

### 4. `DataLoader.java`

- **Ubicación**: `src/main/java/net/elpuig/Agenda/service/`

- **Función**:

- Valida y carga los archivos `config.txt` y `peticiones.txt`.

- Convierte las líneas de `peticiones.txt` en objetos `Reserva`.

---

### 5. `application.properties`

- **Ubicación**: `src/main/resources/`

- **Función**:

Configuración de Spring Boot:

```properties

server.port=8080

spring.thymeleaf.cache=false

```

---

### 6. `internacional.CAT.properties`

- **Ubicación**: `src/main/resources/`

- **Función**:

Internacionalización en catalán para días y meses:

```properties

002=Dilluns,Dimarts,Dimecres,Dijous,Divendres,Dissabte,Diumenge

004=Gener,Febrer,Març,Abril,Maig,Juny,Juliol,Agost,Setembre,Octubre,Novembre,Desembre

```

---

### 7. `agenda.html`

- **Ubicación**: `src/main/resources/templates/`

- **Función**:

- Muestra la agenda en tablas con colores para indicar disponibilidad.

- Lista incidencias detectadas.

---

### 8. `upload.html`

- **Ubicación**: `src/main/resources/templates/`

- **Función**:

- Formulario para subir archivos de configuración y peticiones.

- Muestra errores de validación.

---

## Flujo de la Aplicación

1. **Subida de archivos**: El usuario carga `config.txt` y `peticiones.txt` desde `upload.html`.

2. **Validación**: `DataLoader` verifica los archivos y crea objetos `Reserva`.

3. **Procesamiento**: `AgendaProcessor` organiza las reservas y detecta conflictos.

4. **Visualización**: `agenda.html` muestra la agenda con colores y lista de incidencias.

**Patrón MVC**:

- **Modelo**: `Reserva.java`.

- **Vista**: `agenda.html` y `upload.html`.

- **Controlador**: `AgendaController.java`.

```