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
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class DataLoader {
    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    private YearMonth mesProcesar;
    private String idiomaEntrada;
    private String idiomaSalida;
    private Map<String, String> traducciones = new HashMap<>(); // Traducciones para el idioma de SALIDA
    private List<Reserva> reservas = new ArrayList<>(); // Reservas válidas cargadas
    private List<String> incidenciasCarga = new ArrayList<>(); // Para incidencias durante la carga de archivos

    // Mapa para traducir abreviaciones de días de entrada a códigos internos (LMCJVSGD)
    private static final Map<String, String> ABBREVIATION_MAP = new HashMap<>();

    // Bloque estático para inicializar el mapa una sola vez
    static {
        // Mapeo para inglés (puedes añadir otros idiomas si sus abreviaciones son diferentes)
        ABBREVIATION_MAP.put("MON", "L");
        ABBREVIATION_MAP.put("TUE", "M");
        ABBREVIATION_MAP.put("WED", "C");
        ABBREVIATION_MAP.put("THU", "J");
        ABBREVIATION_MAP.put("FRI", "V");
        ABBREVIATION_MAP.put("SAT", "S");
        ABBREVIATION_MAP.put("SUN", "D");
        // Asegúrate de que si "ESP" o "CAT" tienen abreviaciones diferentes en el archivo de entrada,
        // también las incluyas aquí. Por el momento, asumimos LMCJVSGD para ellos.
    }

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Métodos Getter para acceder a la información cargada
    public YearMonth getMesProcesar() {
        return mesProcesar;
    }

    public String getIdiomaEntrada() {
        return idiomaEntrada;
    }

    public String getIdiomaSalida() {
        return idiomaSalida;
    }

    public Map<String, String> getTraducciones() {
        return traducciones;
    }

    public List<Reserva> getReservas() {
        return Collections.unmodifiableList(reservas); // Devolver una lista inmutable
    }

    public List<String> getIncidenciasCarga() {
        return Collections.unmodifiableList(incidenciasCarga); // Devolver una lista inmutable
    }

    /**
     * Método principal para cargar los archivos de configuración y peticiones.
     * Recibe las traducciones precargadas para todos los idiomas.
     *
     * @param configStream InputStream del archivo config.txt
     * @param peticionesStream InputStream del archivo peticiones.txt
     * @param todosLosIdiomasTraducciones Mapa que contiene las traducciones para cada idioma (ej. "ENG" -> Map de traducciones)
     * @throws Exception Si ocurre un error durante la carga o validación.
     */
    public void cargarArchivos(InputStream configStream, InputStream peticionesStream,
                               Map<String, Map<String, String>> todosLosIdiomasTraducciones) throws Exception {
        logger.info("Iniciando carga de archivos...");
        limpiarEstadoAnterior(); // Limpiar datos de cargas previas

        // 1. Cargar config.txt para obtener mes, año e idiomas
        cargarConfig(configStream);
        logger.info("Configuración cargada: Año {}, Mes {}, Entrada {}, Salida {}",
                mesProcesar.getYear(), mesProcesar.getMonthValue(), idiomaEntrada, idiomaSalida);

        // Asignar las traducciones específicas para el idioma de salida
        // Asegúrate de que las claves en el mapa de traducciones sean mayúsculas (ej. "ARA", "ENG")
        this.traducciones = todosLosIdiomasTraducciones.get(idiomaSalida.toUpperCase());
        if (this.traducciones == null) {
            String errorMsg = "No se encontraron traducciones para el idioma de salida: " + idiomaSalida.toUpperCase();
            logger.error(errorMsg);
            throw new Exception(errorMsg);
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
        logger.debug("Estado de DataLoader limpiado.");
    }

    private void cargarConfig(InputStream configStream) throws Exception {
        if (configStream == null) {
            throw new IllegalArgumentException("InputStream para config.txt es nulo.");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(configStream, "UTF-8"))) {
            String line;
            // Línea 1: Año y Mes
            if ((line = reader.readLine()) != null) {
                String[] partesFecha = line.trim().split(" ");
                if (partesFecha.length != 2) {
                    throw new Exception("Formato inválido en config.txt (línea 1: año mes): " + line);
                }
                try {
                    int year = Integer.parseInt(partesFecha[0]);
                    int month = Integer.parseInt(partesFecha[1]);
                    this.mesProcesar = YearMonth.of(year, month);
                } catch (NumberFormatException e) {
                    throw new Exception("Valores numéricos inválidos para año/mes en config.txt: " + line);
                }
            } else {
                throw new Exception("config.txt está vacío o la primera línea (año mes) falta.");
            }

            // Línea 2: Idioma de entrada y salida
            if ((line = reader.readLine()) != null) {
                String[] partesIdioma = line.trim().split(" ");
                if (partesIdioma.length != 2) {
                    throw new Exception("Formato inválido en config.txt (línea 2: idiomaEntrada idiomaSalida): " + line);
                }
                this.idiomaEntrada = partesIdioma[0].toUpperCase();
                this.idiomaSalida = partesIdioma[1].toUpperCase();
            } else {
                throw new Exception("config.txt está vacío o la segunda línea (idiomas) falta.");
            }
            logger.debug("Configuración de config.txt parseada.");
        } catch (Exception e) {
            logger.error("Error al cargar config.txt: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void cargarPeticiones(InputStream peticionesStream) {
        if (peticionesStream == null) {
            incidenciasCarga.add("InputStream para peticiones.txt es nulo.");
            logger.error("InputStream para peticiones.txt es nulo.");
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(peticionesStream, "UTF-8"))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Ignorar líneas vacías o comentarios
                }

                try {
                    reservas.add(crearReservaDesdeLinea(line));
                } catch (Exception e) {
                    String incidencia = String.format("Error en línea %d de peticiones.txt ('%s'): %s", lineNumber, line, e.getMessage());
                    incidenciasCarga.add(incidencia);
                    logger.error(incidencia); // Log del error completo
                }
            }
            logger.debug("Peticiones de peticiones.txt procesadas.");
        } catch (Exception e) {
            String incidencia = "Error al leer peticiones.txt: " + e.getMessage();
            incidenciasCarga.add(incidencia);
            logger.error(incidencia); // Log del error completo
        }
    }

    /**
     * Crea un objeto Reserva a partir de una línea del archivo de peticiones.
     * Incluye validaciones y conversión de días de la semana.
     *
     * @param linea La línea de texto de la petición.
     * @return Un objeto Reserva.
     * @throws Exception Si la línea no tiene el formato esperado o hay datos inválidos.
     */
    private Reserva crearReservaDesdeLinea(String linea) throws Exception {
        String[] partes = linea.split(" ");
        if (partes.length != 6) {
            throw new Exception("Formato de línea de reserva inválido. Se esperaban 6 partes (NombreActividad Sala FechaInicio FechaFin DíasHoras Horarios).");
        }

        String nombreActividad = partes[0];
        String sala = partes[1];
        LocalDate fechaInicio = parseFecha(partes[2]);
        LocalDate fechaFin = parseFecha(partes[3]);
        String diasSemanaEntrada = partes[4]; // Días como vienen en el archivo
        String horarios = partes[5];

        // Validaciones básicas de fechas y horarios antes de la conversión/creación
        validarFechasYHorarios(fechaInicio, fechaFin, horarios);

        // Convertir los días de la semana al formato interno (LMCJVSGD) basado en el idioma de entrada
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

    /**
     * Convierte las abreviaciones de los días de la semana de un idioma de entrada
     * a un formato interno estandarizado (L, M, C, J, V, S, D).
     *
     * @param diasEntrada La cadena de días tal como aparece en el archivo de entrada.
     * @param idioma      El código del idioma de entrada (ej. "ENG", "ESP", "CAT").
     * @return Una cadena con los días convertidos al formato interno.
     * @throws Exception Si el idioma de entrada no está soportado o los días son inválidos.
     */
    private String convertirDiasSemana(String diasEntrada, String idioma) throws Exception {
        String diasEntradaUpper = diasEntrada.toUpperCase();

        if ("ENG".equalsIgnoreCase(idioma)) {
            StringBuilder diasConvertidos = new StringBuilder();
            // Esto asume que los días en inglés vienen concatenados sin separador (ej. "MONWEDFRI")
            // Si vinieran con comas (ej. "MON,WED,FRI"), la lógica de split debería cambiar.
            // Para "MONWEDFRI", un split por cada 3 caracteres es apropiado.
            String[] diasIndividuales = diasEntradaUpper.split("(?<=\\G...)"); // Divide cada 3 caracteres

            if (diasIndividuales.length == 0 && !diasEntradaUpper.isEmpty()) {
                // Fallback para entradas que no son múltiplos de 3, como "MONDAY" o "TUE" solo
                String codigoInterno = ABBREVIATION_MAP.get(diasEntradaUpper.trim());
                if (codigoInterno != null) {
                    return codigoInterno;
                }
            }

            for (String dia : diasIndividuales) {
                String codigoInterno = ABBREVIATION_MAP.get(dia.trim());
                if (codigoInterno == null) {
                    throw new Exception("Día de la semana en inglés no reconocido o formato inválido: '" + dia + "'");
                }
                diasConvertidos.append(codigoInterno);
            }
            return diasConvertidos.toString();
        } else if ("ESP".equalsIgnoreCase(idioma) || "CAT".equalsIgnoreCase(idioma) || "ARA".equalsIgnoreCase(idioma) || "ZHO".equalsIgnoreCase(idioma) || "JPN".equalsIgnoreCase(idioma)) {
            // Para estos idiomas, asumimos que los días ya vienen en el formato interno LMCJVSGD
            // Si el árabe tuviera un formato de días de entrada diferente, necesitarías añadir su lógica aquí.
            if (!diasEntradaUpper.matches("^[LMCJVSGD]+$")) {
                throw new Exception("Días inválidos para el idioma " + idioma + ": " + diasEntrada + ". Se esperan L, M, C, J, V, S, D.");
            }
            return diasEntradaUpper;
        } else {
            throw new Exception("Idioma de entrada no soportado para la conversión de días: " + idioma);
        }
    }


    // Validación de fechas con lanzamiento de excepciones
    private LocalDate parseFecha(String fechaStr) throws Exception {
        try {
            return LocalDate.parse(fechaStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new Exception("Formato de fecha inválido: " + fechaStr + ". Formato esperado: dd/MM/yyyy.");
        }
    }

    // Validaciones combinadas de fechas y horarios
    private void validarFechasYHorarios(LocalDate fechaInicio, LocalDate fechaFin, String horariosStr) throws Exception {
        if (fechaInicio.isAfter(fechaFin)) {
            throw new Exception("Fecha inicio (" + fechaInicio.format(DATE_FORMATTER) + ") no puede ser posterior a fecha fin (" + fechaFin.format(DATE_FORMATTER) + ")");
        }

        // Validar que el año y el mes de inicio y fin estén dentro del mes a procesar
        // Esta validación debe ser más precisa, ya que una reserva puede abarcar varios meses
        // y solo nos importa si cae dentro del mes a procesar o afecta a los días del mes a procesar.
        // Por ahora, solo una validación de rango de años para evitar errores obvios.
        if (fechaInicio.getYear() < 1900 || fechaInicio.getYear() > 2150 ||
            fechaFin.getYear() < 1900 || fechaFin.getYear() > 2150) {
            throw new Exception("Año fuera de rango permitido (1900-2150) para fecha " + fechaInicio.format(DATE_FORMATTER) + " o " + fechaFin.format(DATE_FORMATTER) + ".");
        }


        // Validar horarios
        String[] horarios = horariosStr.split("_");
        if (horarios.length == 0) {
            throw new Exception("No se especificaron horarios en la reserva.");
        }
        for (String horario : horarios) {
            String[] horas = horario.split("-");
            if (horas.length != 2) {
                throw new Exception("Formato de rango horario inválido: '" + horario + "'. Esperado HH-HH.");
            }
            try {
                int inicio = Integer.parseInt(horas[0]);
                int fin = Integer.parseInt(horas[1]);
                if (inicio < 0 || fin > 24 || inicio >= fin) {
                    throw new Exception("Rango horario inválido o fuera de límites (00-24) o inicio >= fin: '" + horario + "'.");
                }
            } catch (NumberFormatException e) {
                throw new Exception("Valores de hora no numéricos en rango: '" + horario + "'.");
            }
        }
    }

    // No se necesita loadTraducciones aquí, ya que el mapa de traducciones se pasa.
    // Si quisieras que DataLoader gestionara la carga de los archivos .properties directamente,
    // necesitarías un nuevo método o refactorizar 'cargarArchivos' para que no reciba el mapa
    // y lo construya internamente, usando ClassLoader.getResourceAsStream("i18n/...")
}