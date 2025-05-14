package net.elpuig.Agenda.service;


import net.elpuig.Agenda.model.Reserva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// DataLoader.java
import org.springframework.stereotype.Service;
import java.time.LocalDate;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.*;

@Service
public class DataLoader {
    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    @SuppressWarnings("unused")
    private YearMonth mesProcesar;  // Usado en módulos posteriores (feature/outputs)
    private String idiomaEntrada;
    @SuppressWarnings("unused")
    private String idiomaSalida;    // Usado en módulos posteriores (feature/outputs)
    private Map<String, String> traducciones = new HashMap<>();
    private List<Reserva> reservas = new ArrayList<>();

    // Variables para almacenar datos temporalmente
    private List<String> configData = new ArrayList<>();
    private List<String> peticionesData = new ArrayList<>();

    // Método para crear Reserva desde una línea válida
    private Reserva crearReservaDesdeLinea(String linea) throws Exception {
        String[] partes = linea.split(" ");
        return new Reserva(
                partes[0],
                partes[1],
                parseFechaSilencioso(partes[2]), // Ahora lanza excepción
                parseFechaSilencioso(partes[3]),
                partes[4],
                partes[5]
        );
    }

    // Versión segura de parseFecha (sin lanzar excepciones)
    private LocalDate parseFechaSilencioso(String fechaStr) throws Exception {
        try {
            String[] partes = fechaStr.split("/");
            return LocalDate.of(
                    Integer.parseInt(partes[2]),
                    Integer.parseInt(partes[1]),
                    Integer.parseInt(partes[0])
            );
        } catch (Exception e) {
            throw new Exception("Fecha inválida: " + fechaStr);
        }
    }

    public void validarConfig(InputStream configStream) throws Exception {
        try (Scanner scanner = new Scanner(configStream)) {
            // Línea 1: Fecha
            String lineaFecha = scanner.nextLine();
            String[] fecha = lineaFecha.split(" ");
            if (fecha.length != 2) throw new Exception("Formato de fecha inválido");
            this.mesProcesar = YearMonth.of(Integer.parseInt(fecha[0]), Integer.parseInt(fecha[1]));
            configData.add(lineaFecha); // ✅

            // Línea 2: Idiomas
            String lineaIdiomas = scanner.nextLine();
            String[] idiomas = lineaIdiomas.split(" ");
            if (idiomas.length != 2) throw new Exception("Formato de idiomas inválido");
            this.idiomaEntrada = idiomas[0];
            this.idiomaSalida = idiomas[1];
            configData.add(lineaIdiomas); // ✅
            logger.info("Contenido de configData: {}", configData);
            System.out.println(configData);

            cargarTraducciones(idiomaEntrada);
        }
    }

    private void cargarTraducciones(String idioma) throws Exception {
        String archivo = "internacional." + idioma + ".properties";
        try (InputStream input = ClassLoader.getSystemResourceAsStream(archivo)) {
            if (input == null) {
                throw new Exception("Archivo no encontrado: " + archivo 
                    + "\nRuta esperada: 'classpath:/" + archivo + "'");
            }
            try (Scanner scanner = new Scanner(input)) {  // Try-with-resources para cerrar Scanner
                while (scanner.hasNextLine()) {
                    String[] linea = scanner.nextLine().split(";");
                    if (linea.length == 2) traducciones.put(linea[0].trim(), linea[1].trim());
                }
            }
        }
    }

    public void validarPeticiones(InputStream peticionesStream) throws Exception {
        try (Scanner scanner = new Scanner(peticionesStream)) {
            while (scanner.hasNextLine()) {
                String linea = scanner.nextLine().trim();
                validarLineaPeticion(linea);
                reservas.add(crearReservaDesdeLinea(linea)); // Añadir reserva válida
            }
        }
    }

    // Getters necesarios
    public YearMonth getMesProcesar() {
        return mesProcesar;
    }

    public Map<String, String> getTraducciones() {
        return traducciones;
    }

    public List<Reserva> getReservas() {
        return reservas;
    }

    private void validarLineaPeticion(String linea) throws Exception {
        String[] partes = linea.split(" ");
        if (partes.length < 6) throw new Exception("Formato de petición inválido: " + linea);

        // Validar formato de fechas
        validarFecha(partes[2]); // FechaInicio
        validarFecha(partes[3]); // FechaFin

        // Validar que FechaInicio <= FechaFin
        LocalDate fechaInicio = parseFecha(partes[2]);
        LocalDate fechaFin = parseFecha(partes[3]);
        if (fechaInicio.isAfter(fechaFin)) {
            throw new Exception("Fecha de inicio posterior a fecha fin: " + linea);
        }

        // Validar horarios
        String[] horarios = partes[5].split("_");
        for (String horario : horarios) {
            String[] horas = horario.split("-");
            if (horas.length != 2) throw new Exception("Horario inválido: " + horario);
            int inicio = Integer.parseInt(horas[0]);
            int fin = Integer.parseInt(horas[1]);
            if (inicio >= fin || inicio < 0 || fin > 24) {
                throw new Exception("Rango horario inválido: " + horario);
            }
        }
    }

    private LocalDate parseFecha(String fechaStr) throws Exception {
        try {
            String[] partes = fechaStr.split("/");
            int dia = Integer.parseInt(partes[0]);
            int mes = Integer.parseInt(partes[1]);
            int año = Integer.parseInt(partes[2]);
            return LocalDate.of(año, mes, dia);
        } catch (Exception e) {
            throw new Exception("Error al parsear fecha: " + fechaStr);
        }
    }

    private void validarFecha(String fecha) throws Exception {
        if (!fecha.matches("\\d{2}/\\d{2}/\\d{4}")) {
            throw new Exception("Formato de fecha inválido: " + fecha);
        }
        // Validación adicional de día/mes
        String[] partes = fecha.split("/");
        int dia = Integer.parseInt(partes[0]);
        int mes = Integer.parseInt(partes[1]);
        if (mes < 1 || mes > 12 || dia < 1 || dia > 31) {
            throw new Exception("Fecha fuera de rango: " + fecha);
        }

        YearMonth yearMonth = YearMonth.of(Integer.parseInt(partes[2]), mes);
        if (dia > yearMonth.lengthOfMonth()) {
            throw new Exception("Día inválido para el mes: " + fecha);
        }

        int año = Integer.parseInt(partes[2]);
        if (año < 1900 || año > Year.now().getValue() + 1) { // Ej: año entre 1900 y 2025
            throw new Exception("Año inválido: " + fecha);
        }
    }

    // Métodos para acceder a los datos (usar en futuras vistas)
    public List<String> getConfigData() {
        return configData;
    }

    public List<String> getPeticionesData() {
        return peticionesData;
    }

}