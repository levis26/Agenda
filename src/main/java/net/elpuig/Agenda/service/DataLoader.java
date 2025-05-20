package net.elpuig.Agenda.service;

import net.elpuig.Agenda.model.Reserva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
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
    private Map<String, String> traducciones = new HashMap<>();
    private List<String> incidencias = new ArrayList<>();

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public void loadConfig(MultipartFile configFile) throws IOException {
        incidencias.clear();
        if (configFile.isEmpty()) {
            throw new IllegalArgumentException("El archivo de configuración está vacío.");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(configFile.getInputStream()))) {
            String line1 = reader.readLine();
            String line2 = reader.readLine();

            if (line1 == null || line2 == null) {
                throw new IllegalArgumentException("El archivo config.txt debe contener al menos dos líneas.");
            }

            try {
                String[] ym = line1.trim().split(" ");
                if (ym.length != 2) {
                    throw new IllegalArgumentException("Formato de año y mes incorrecto en config.txt: " + line1);
                }
                int year = Integer.parseInt(ym[0]);
                int month = Integer.parseInt(ym[1]);
                this.mesProcesar = YearMonth.of(year, month);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Año o mes inválido en config.txt: " + line1, e);
            }

            String[] langs = line2.trim().split(" ");
            if (langs.length != 2) {
                throw new IllegalArgumentException("Formato de idiomas incorrecto en config.txt: " + line2);
            }
            this.idiomaEntrada = langs[0];
            String idiomaSalida = langs[1];

            loadTranslations(idiomaSalida);
            logger.info("Configuración cargada: Mes a procesar {}, Idioma de entrada {}, Idioma de salida {}", mesProcesar, idiomaEntrada, idiomaSalida);
        } catch (IOException e) {
            logger.error("Error de E/S al cargar config.txt", e);
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
                    reservasCargadas.add(crearReservaDesdeLinea(line));
                } catch (Exception e) {
                    String incidencia = String.format("Error en línea %d del archivo de peticiones: '%s'. Causa: %s", lineNumber, line, e.getMessage());
                    parsingIncidencias.add(incidencia);
                    logger.warn(incidencia);
                }
            }
        } catch (IOException e) {
            logger.error("Error de E/S al cargar peticiones.txt", e);
            throw e;
        }
        return reservasCargadas;
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
        String diasSemana = partes[4];
        String horarios = partes[5];

        validarFechas(fechaInicio, fechaFin);
        validarDiasSemana(diasSemana);
        validarHorarios(horarios);

        return new Reserva(nombreActividad, sala, fechaInicio, fechaFin, diasSemana, horarios);
    }

    private LocalDate parseFecha(String fechaStr) throws DateTimeParseException {
        return LocalDate.parse(fechaStr, DATE_FORMATTER);
    }

    private void validarFechas(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaInicio.isAfter(fechaFin)) {
            throw new IllegalArgumentException("La fecha de inicio no puede ser posterior a la fecha de fin.");
        }
    }

    private void validarDiasSemana(String diasSemanaCode) {
        if (diasSemanaCode == null || !diasSemanaCode.matches("^[LMCJVSG]+$")) {
            throw new IllegalArgumentException("Formato de días de la semana inválido. Solo se permiten 'L', 'M', 'C', 'J', 'V', 'S', 'G'.");
        }
    }

    private void validarHorarios(String horariosStr) {
        String[] horarios = horariosStr.split("_");
        for (String horario : horarios) {
            String[] partes = horario.split("-");
            if (partes.length != 2) {
                throw new IllegalArgumentException("Formato de horario inválido: " + horario + ". Se espera HH-HH.");
            }
            try {
                int inicio = Integer.parseInt(partes[0]);
                int fin = Integer.parseInt(partes[1]);
                if (inicio < 0 || fin > 24 || inicio >= fin) {
                    throw new IllegalArgumentException("Rango horario inválido: " + horario + ". Las horas deben estar entre 00 y 24, y la hora de inicio debe ser menor que la de fin.");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Formato de hora no numérico en horario: " + horario, e);
            }
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

    public Map<String, String> getTraducciones() {
        return traducciones;
    }

    public List<String> getIncidencias() {
        return incidencias;
    }
}