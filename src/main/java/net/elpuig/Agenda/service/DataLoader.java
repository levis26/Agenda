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
    private List<Reserva> reservas = new ArrayList<>();
    private List<String> incidenciasCarga = new ArrayList<>(); // Para incidencias durante la carga

    // Mapa para traducir abreviaciones de días de entrada a códigos internos
    private static final Map<String, String> ABBREVIATION_MAP = new HashMap<>();

    // Bloque estático para inicializar el mapa una sola vez
    static {
        ABBREVIATION_MAP.put("MON", "L");
        ABBREVIATION_MAP.put("TUE", "M");
        ABBREVIATION_MAP.put("WED", "C");
        ABBREVIATION_MAP.put("THU", "J");
        ABBREVIATION_MAP.put("FRI", "V");
        ABBREVIATION_MAP.put("SAT", "S");
        ABBREVIATION_MAP.put("SUN", "D");
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
        return reservas;
    }

    public List<String> getIncidenciasCarga() {
        return incidenciasCarga;
    }

    // Método principal para cargar los archivos
    public void cargarArchivos(InputStream configStream, InputStream peticionesStream,
                               Map<String, Map<String, String>> todosLosIdiomasTraducciones) throws Exception {
        logger.info("Iniciando carga de archivos...");
        limpiarEstadoAnterior(); // Limpiar datos de cargas previas

        // 1. Cargar config.txt
        cargarConfig(configStream);
        logger.info("Configuración cargada: Año {}, Mes {}, Entrada {}, Salida {}",
                mesProcesar.getYear(), mesProcesar.getMonthValue(), idiomaEntrada, idiomaSalida);

        // Asignar las traducciones específicas para el idioma de salida
        this.traducciones = todosLosIdiomasTraducciones.get(idiomaSalida);
        if (this.traducciones == null) {
            throw new Exception("No se encontraron traducciones para el idioma de salida: " + idiomaSalida);
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

    private void cargarConfig(InputStream configStream) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(configStream, "UTF-8"))) {
            String line;
            if ((line = reader.readLine()) != null) {
                String[] partesFecha = line.split(" ");
                if (partesFecha.length != 2) {
                    throw new Exception("Formato inválido en config.txt (línea 1): " + line);
                }
                int year = Integer.parseInt(partesFecha[0]);
                int month = Integer.parseInt(partesFecha[1]);
                this.mesProcesar = YearMonth.of(year, month);
            } else {
                throw new Exception("config.txt está vacío o la primera línea falta.");
            }

            if ((line = reader.readLine()) != null) {
                String[] partesIdioma = line.split(" ");
                if (partesIdioma.length != 2) {
                    throw new Exception("Formato inválido en config.txt (línea 2): " + line);
                }
                this.idiomaEntrada = partesIdioma[0].toUpperCase();
                this.idiomaSalida = partesIdioma[1].toUpperCase();
            } else {
                throw new Exception("config.txt está vacío o la segunda línea falta.");
            }
        } catch (NumberFormatException e) {
            throw new Exception("Error al parsear año/mes en config.txt: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error al cargar config.txt: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void cargarPeticiones(InputStream peticionesStream) {
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
                    logger.error(incidencia, e); // Log del error completo
                }
            }
        } catch (Exception e) {
            String incidencia = "Error al leer peticiones.txt: " + e.getMessage();
            incidenciasCarga.add(incidencia);
            logger.error(incidencia, e); // Log del error completo
        }
    }

    // Método para crear Reserva desde una línea válida, incluyendo la conversión de días
    private Reserva crearReservaDesdeLinea(String linea) throws Exception {
        String[] partes = linea.split(" ");
        if (partes.length != 6) {
            throw new Exception("Formato de línea de reserva inválido. Se esperaban 6 partes.");
        }

        String nombreActividad = partes[0];
        String sala = partes[1];
        LocalDate fechaInicio = parseFecha(partes[2]);
        LocalDate fechaFin = parseFecha(partes[3]);
        String diasSemanaEntrada = partes[4]; // Días como vienen en el archivo
        String horarios = partes[5];

        // Validaciones básicas de fechas y horarios antes de la conversión/creación
        validarFechasYHorarios(fechaInicio, fechaFin, horarios);

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

    // Nuevo método para convertir las abreviaciones de los días de la semana
    private String convertirDiasSemana(String diasEntrada, String idioma) throws Exception {
        // Normalizar a mayúsculas para la comparación en el mapa
        String diasEntradaUpper = diasEntrada.toUpperCase();

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


            for (String dia : diasIndividuales) {
                String codigoInterno = ABBREVIATION_MAP.get(dia.trim()); // trim por si hay espacios
                if (codigoInterno == null) {
                    throw new Exception("Día de la semana en inglés no reconocido o formato inválido: '" + dia + "'");
                }
                diasConvertidos.append(codigoInterno);
            }
            return diasConvertidos.toString();
        } else if ("ESP".equalsIgnoreCase(idioma) || "CAT".equalsIgnoreCase(idioma) || "ZHO".equalsIgnoreCase(idioma) || "JPN".equalsIgnoreCase(idioma)) {
            // Para estos idiomas, esperamos el formato interno LMCJVSGD
            if (!diasEntradaUpper.matches("^[LMCJVSGD]+$")) {
                throw new Exception("Días inválidos para el idioma " + idioma + ": " + diasEntrada);
            }
            return diasEntradaUpper; // Devolvemos en mayúsculas para consistencia
        } else {
            throw new Exception("Idioma de entrada no soportado para la conversión de días: " + idioma);
        }
    }


    // Validación de fechas con lanzamiento de excepciones
    private LocalDate parseFecha(String fechaStr) throws Exception {
        try {
            return LocalDate.parse(fechaStr, DATE_FORMATTER);
        } catch (Exception e) {
            throw new Exception("Fecha inválida: " + fechaStr + ". Formato esperado: dd/MM/yyyy");
        }
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


        // Validar horarios
        String[] horarios = horariosStr.split("_");
        for (String horario : horarios) {
            String[] horas = horario.split("-");
            if (horas.length != 2) {
                throw new Exception("Formato de horario inválido: " + horario + ". Esperado HH-HH");
            }
            int inicio = Integer.parseInt(horas[0]);
            int fin = Integer.parseInt(horas[1]);
            if (inicio < 0 || fin > 24 || inicio >= fin) {
                throw new Exception("Rango horario inválido o fuera de límites (00-24): " + horario);
            }
        }
    }

    // Método para cargar los archivos de propiedades de internacionalización
    // Este método es crucial y debe ser llamado por tu aplicación antes de que DataLoader
    // intente usar las traducciones. Por ejemplo, en el constructor de tu clase principal
    // o en un método de inicialización de un servicio que gestione i18n.
    // Lo ideal es que el DataLoader reciba el Map<String, String> de traducciones para el idioma de salida
    // en lugar de cargarlo él mismo, si tienes un servicio de i18n centralizado.
    // Para esta solución, asumimos que se lo pasamos al cargarArchivos.

}