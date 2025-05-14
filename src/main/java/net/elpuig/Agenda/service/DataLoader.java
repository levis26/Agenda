package net.elpuig.Agenda.service;

import net.elpuig.Agenda.model.Reserva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.*;

@Service
public class DataLoader {
    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    private YearMonth mesProcesar;
    private String idiomaEntrada;
    private Map<String, String> traducciones = new HashMap<>();
    private List<Reserva> reservas = new ArrayList<>();

    // Método para crear Reserva desde una línea válida
    private Reserva crearReservaDesdeLinea(String linea) throws Exception {
        String[] partes = linea.split(" ");
        return new Reserva(
                partes[0],
                partes[1],
                parseFecha(partes[2]),
                parseFecha(partes[3]),
                partes[4],
                partes[5]
        );
    }

    // Validación de fechas con lanzamiento de excepciones
    private LocalDate parseFecha(String fechaStr) throws Exception {
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
            // Línea 1: Año y Mes
            String lineaFecha = scanner.nextLine().trim();
            validarLineaConfig(lineaFecha, true);
            
            // Línea 2: Idiomas
            String lineaIdiomas = scanner.nextLine().trim();
            validarLineaConfig(lineaIdiomas, false);
            
            cargarTraducciones(idiomaEntrada);
        }
    }

    private void validarLineaConfig(String linea, boolean esFecha) throws Exception {
        String[] partes = linea.split(" ");
        if (esFecha) {
            if (partes.length != 2) throw new Exception("Formato de fecha inválido");
            int año = Integer.parseInt(partes[0]);
            int mes = Integer.parseInt(partes[1]);
            if (mes < 1 || mes > 12) throw new Exception("Mes inválido: " + mes);
            this.mesProcesar = YearMonth.of(año, mes);
        } else {
            if (partes.length != 2) throw new Exception("Formato de idiomas inválido");
            this.idiomaEntrada = partes[0];
        }
    }

    private void cargarTraducciones(String idioma) throws Exception {
        String archivo = "internacional." + idioma + ".properties";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(archivo)) {
            if (input == null) {
                throw new Exception("Archivo de traducción no encontrado: " + archivo);
            }
            try (Scanner scanner = new Scanner(input)) {
                while (scanner.hasNextLine()) {
                    String[] linea = scanner.nextLine().split(";");
                    if (linea.length == 2) {
                        traducciones.put(linea[0].trim(), linea[1].trim());
                    }
                }
            }
        }
    }

    public void validarPeticiones(InputStream peticionesStream) throws Exception {
        try (Scanner scanner = new Scanner(peticionesStream)) {
            while (scanner.hasNextLine()) {
                String linea = scanner.nextLine().trim();
                try {
                    validarLineaPeticion(linea);
                    reservas.add(crearReservaDesdeLinea(linea));
                } catch (Exception e) {
                    logger.error("Error en línea: {} - {}", linea, e.getMessage());
                    throw e; // Opcional: Continuar procesando otras líneas removiendo 'throw'
                }
            }
        }
    }

    // Getters
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
        if (partes.length < 6) throw new Exception("Formato de petición inválido");

        validarFecha(partes[2]); // FechaInicio
        validarFecha(partes[3]); // FechaFin

        LocalDate fechaInicio = parseFecha(partes[2]);
        LocalDate fechaFin = parseFecha(partes[3]);
        if (fechaInicio.isAfter(fechaFin)) {
            throw new Exception("Fecha inicio > fecha fin");
        }

        validarHorarios(partes[5]);
    }

    private void validarHorarios(String horariosStr) throws Exception {
        String[] horarios = horariosStr.split("_");
        for (String horario : horarios) {
            String[] horas = horario.split("-");
            if (horas.length != 2) throw new Exception("Formato horario inválido");
            int inicio = Integer.parseInt(horas[0]);
            int fin = Integer.parseInt(horas[1]);
            if (inicio >= fin || inicio < 0 || fin > 24) {
                throw new Exception("Rango horario inválido: " + horario);
            }
        }
    }

    private void validarFecha(String fecha) throws Exception {
        if (!fecha.matches("\\d{2}/\\d{2}/\\d{4}")) {
            throw new Exception("Formato fecha inválido");
        }
        String[] partes = fecha.split("/");
        int dia = Integer.parseInt(partes[0]);
        int mes = Integer.parseInt(partes[1]);
        int año = Integer.parseInt(partes[2]);

        if (mes < 1 || mes > 12) throw new Exception("Mes inválido");
        YearMonth yearMonth = YearMonth.of(año, mes);
        if (dia < 1 || dia > yearMonth.lengthOfMonth()) {
            throw new Exception("Día inválido");
        }
        if (año < 1900 || año > Year.now().getValue() + 1) {
            throw new Exception("Año inválido");
        }
    }
}