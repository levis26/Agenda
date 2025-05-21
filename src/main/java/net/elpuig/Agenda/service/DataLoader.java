package net.elpuig.Agenda.service;

import net.elpuig.Agenda.model.Reserva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DataLoader {
    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    private YearMonth mesProcesar;
    private String idiomaEntrada;
    private String idiomaSalida;
    private Map<String, String> traducciones = new HashMap<>();

    private List<String> incidencias = new ArrayList<>();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public void loadConfig(MultipartFile configFile) throws IOException {
        incidencias.clear();
        if (configFile.isEmpty()) {
            throw new IllegalArgumentException("El archivo de configuración está vacío.");

   
        }
        logger.info("Traducciones cargadas para el idioma de salida: {}", idiomaSalida);


        // 2. Cargar peticiones.txt
        cargarPeticiones(peticionesStream);
        logger.info("Peticiones cargadas. Total de reservas procesadas: {}", reservas.size() + incidenciasCarga.size());
        logger.info("Reservas válidas cargadas: {}", reservas.size());
        if (!incidenciasCarga.isEmpty()) {
            logger.warn("Incidencias durante la carga de peticiones: {}", incidenciasCarga.size());
            incidenciasCarga.forEach(incidencia -> logger.warn("  - {}", incidencia));
        }
    }

    private void limpiarEstadoAnterior() {
        mesProcesar = null;
        idiomaEntrada = null;
        idiomaSalida = null;
        traducciones.clear();
        reservas.clear();
        incidenciasCarga.clear();
    }


            try {
                String[] ym = line1.trim().split(" ");
                if (ym.length != 2) {
                    throw new IllegalArgumentException("Formato de año y mes incorrecto en config.txt: " + line1);

                }
                int year = Integer.parseInt(partesFecha[0]);
                int month = Integer.parseInt(partesFecha[1]);
                this.mesProcesar = YearMonth.of(year, month);
            } else {
                throw new Exception("config.txt está vacío o la primera línea falta.");
            }


            String[] langs = line2.trim().split(" ");
            if (langs.length != 2) {
                throw new IllegalArgumentException("Formato de idiomas incorrecto en config.txt: " + line2);

            }
        } catch (NumberFormatException e) {
            throw new Exception("Error al parsear año/mes en config.txt: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error al cargar config.txt: {}", e.getMessage(), e);
            throw e;
        }
    }


    public List<Reserva> loadPeticiones(MultipartFile peticionesFile, List<String> parsingIncidencias) throws IOException {
        List<Reserva> reservasCargadas = new ArrayList<>();
        if (peticionesFile.isEmpty()) {
            parsingIncidencias.add("El archivo de peticiones está vacío.");
            return reservasCargadas;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(peticionesFile.getInputStream()))) {

            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {

                    continue;

                }

                try {
                    reservas.add(crearReservaDesdeLinea(line));
                } catch (Exception e) {

                    String incidencia = String.format("Error en línea %d del archivo de peticiones: '%s'. Causa: %s", lineNumber, line, e.getMessage());
                    parsingIncidencias.add(incidencia);
                    logger.warn(incidencia);

                }
            }
        } catch (Exception e) {
            String incidencia = "Error al leer peticiones.txt: " + e.getMessage();
            incidenciasCarga.add(incidencia);
            logger.error(incidencia, e); // Log del error completo
        }
    }

    private Reserva crearReservaDesdeLinea(String linea) throws Exception {
        String[] partes = linea.split(" ");
        if (partes.length != 6) {
            throw new IllegalArgumentException("Número de campos incorrecto. Se esperaban 6 campos.");

        }

        String nombreActividad = partes[0];
        String sala = partes[1];
        LocalDate fechaInicio = parseFecha(partes[2]);
        LocalDate fechaFin = parseFecha(partes[3]);
        String diasSemanaEntrada = partes[4]; // Días como vienen en el archivo
        String horarios = partes[5];


        validarFechas(fechaInicio, fechaFin);
        validarDiasSemana(diasSemana);
        validarHorarios(horarios);


        // Convertir los días de la semana al formato interno (LMCJVSGD)
        String diasSemanaInternos = convertirDiasSemana(diasSemanaEntrada, idiomaEntrada);

        return new Reserva(
                nombreActividad,
                sala,
                fechaInicio,
                fechaFin,
                diasSemanaInternos, // Usamos los días convertidos
                horarios
        );
    }


    private void validarFechas(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaInicio.isAfter(fechaFin)) {
            throw new IllegalArgumentException("La fecha de inicio no puede ser posterior a la fecha de fin.");
        }
    }


        if ("ENG".equalsIgnoreCase(idioma)) {
            StringBuilder diasConvertidos = new StringBuilder();
            // Divide la cadena de días en grupos de 3 caracteres (Mon, Tue, etc.)
            // Ojo: si hay entradas como "MTWHF", necesitarás una lógica de parsing más robusta
            // Aquí asumimos que son bloques de 3 caracteres (ej. "MONWEDFRI")
            // Si el formato es "Mon,Wed,Fri", necesitarías split(",") y luego trim()
            // Para el ejemplo dado (MonWedFri), este regex es útil:
            // "(?<=\\G...)" asegura dividir cada 3 caracteres de la cadena
            String[] diasIndividuales = diasEntradaUpper.split("(?<=\\G...)");

            if (diasIndividuales.length == 0 && !diasEntradaUpper.isEmpty()) {
                // Caso donde el split por 3 caracteres no funciona, podría ser "Mon,Tue" o "Monday"
                // Intentamos un split por coma o un mapeo directo para un solo día completo
                if (diasEntradaUpper.contains(",")) {
                    diasIndividuales = diasEntradaUpper.split(",");
                } else {
                    diasIndividuales = new String[]{diasEntradaUpper}; // Considera toda la cadena como un solo día
                }
            }



    private void loadTranslations(String idiomaSalida) throws IOException {
        String resourceFileName = "i18n/internacional." + idiomaSalida.toUpperCase() + ".properties";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourceFileName)) {
            if (input == null) {
                logger.error("No se encontró el archivo de traducciones: {}", resourceFileName);
                throw new IOException("No se pudo cargar el archivo de traducciones para el idioma: " + idiomaSalida);
            }
            Properties prop = new Properties();
            prop.load(input);
            for (String key : prop.stringPropertyNames()) {
                traducciones.put(key, prop.getProperty(key));
            }
            ensureBasicTranslations(); // Asegurar traducciones básicas
        }
    }

    private void ensureBasicTranslations() {
        if (!traducciones.containsKey("month.1")) traducciones.put("month.1", "Enero");
        if (!traducciones.containsKey("month.2")) traducciones.put("month.2", "Febrero");
        if (!traducciones.containsKey("month.3")) traducciones.put("month.3", "Marzo");
        if (!traducciones.containsKey("month.4")) traducciones.put("month.4", "Abril");
        if (!traducciones.containsKey("month.5")) traducciones.put("month.5", "Mayo");
        if (!traducciones.containsKey("month.6")) traducciones.put("month.6", "Junio");
        if (!traducciones.containsKey("month.7")) traducciones.put("month.7", "Julio");
        if (!traducciones.containsKey("month.8")) traducciones.put("month.8", "Agosto");
        if (!traducciones.containsKey("month.9")) traducciones.put("month.9", "Septiembre");
        if (!traducciones.containsKey("month.10")) traducciones.put("month.10", "Octubre");
        if (!traducciones.containsKey("month.11")) traducciones.put("month.11", "Noviembre");
        if (!traducciones.containsKey("month.12")) traducciones.put("month.12", "Diciembre");

        if (!traducciones.containsKey("day.L")) traducciones.put("day.L", "Lunes");
        if (!traducciones.containsKey("day.M")) traducciones.put("day.M", "Martes");
        if (!traducciones.containsKey("day.C")) traducciones.put("day.C", "Miércoles");
        if (!traducciones.containsKey("day.J")) traducciones.put("day.J", "Jueves");
        if (!traducciones.containsKey("day.V")) traducciones.put("day.V", "Viernes");
        if (!traducciones.containsKey("day.S")) traducciones.put("day.S", "Sábado");
        if (!traducciones.containsKey("day.G")) traducciones.put("day.G", "Domingo");

        // NUEVA CLAVE DE TRADUCCIÓN: Para la actividad "Cerrado" / "Tancat"
        if (!traducciones.containsKey("activity.closed")) traducciones.put("activity.closed", "Tancat");
    }

    public YearMonth getMesProcesar() {
        return mesProcesar;

    }

    // Validaciones combinadas de fechas y horarios
    private void validarFechasYHorarios(LocalDate fechaInicio, LocalDate fechaFin, String horariosStr) throws Exception {
        if (fechaInicio.isAfter(fechaFin)) {
            throw new Exception("Fecha inicio (" + fechaInicio + ") no puede ser posterior a fecha fin (" + fechaFin + ")");
        }

        // Validar que el año y el mes de inicio y fin estén dentro de un rango razonable
        // (ej. 1900-2100 para evitar errores de parseo con años muy antiguos o futuros)
        if (fechaInicio.getYear() < 1900 || fechaInicio.getYear() > 2100 ||
            fechaFin.getYear() < 1900 || fechaFin.getYear() > 2100) {
            throw new Exception("Año fuera de rango permitido (1900-2100).");
        }

    public List<String> getIncidencias() {
        return incidencias;

    }

    // Método para cargar los archivos de propiedades de internacionalización
    // Este método es crucial y debe ser llamado por tu aplicación antes de que DataLoader
    // intente usar las traducciones. Por ejemplo, en el constructor de tu clase principal
    // o en un método de inicialización de un servicio que gestione i18n.
    // Lo ideal es que el DataLoader reciba el Map<String, String> de traducciones para el idioma de salida
    // en lugar de cargarlo él mismo, si tienes un servicio de i18n centralizado.
    // Para esta solución, asumimos que se lo pasamos al cargarArchivos.

}