package net.elpuig.Agenda.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.*;
// import java.time.format.DateTimeFormatter; // REMOVE THIS LINE
// import java.util.stream.Collectors; // REMOVE THIS LINE

public class AgendaViewModel {
    private Map<String, Map<LocalDate, Map<String, String>>> agendaPorSala;
    private List<String> incidencias;
    private YearMonth mesProcesar;
    private Map<String, String> traducciones;

    public AgendaViewModel(YearMonth mesProcesar, Map<String, String> traducciones) {
        this.mesProcesar = mesProcesar;
        this.traducciones = traducciones;
        this.agendaPorSala = new TreeMap<>(); // Using TreeMap for sorted sala names
        this.incidencias = new ArrayList<>();
    }

    // Métodos para agregar datos
    public void addReserva(String sala, LocalDate fecha, String hora, String actividad) {
        agendaPorSala.computeIfAbsent(sala, k -> new TreeMap<>()) // TreeMap for dates
                .computeIfAbsent(fecha, k -> new TreeMap<>())     // TreeMap for hours
                .put(hora, actividad);
    }

    public void addIncidencia(String incidencia) {
        incidencias.add(incidencia);
    }

    // Getters para Thymeleaf
    public String getMesNombre() {
        if (mesProcesar == null) {
            return "Mes no especificado";
        }
        // Use getOrDefault to prevent NullPointerException if key is missing
        return traducciones.getOrDefault("month." + mesProcesar.getMonthValue(), "Unknown Month");
    }

    public int getAnyo() {
        return mesProcesar != null ? mesProcesar.getYear() : 0;
    }

    public List<List<LocalDate>> getSemanas() {
        List<List<LocalDate>> semanas = new ArrayList<>();
        LocalDate fechaInicioMes = mesProcesar.atDay(1);
        LocalDate fechaFinMes = mesProcesar.atEndOfMonth();

        LocalDate currentWeekStart = fechaInicioMes.with(DayOfWeek.MONDAY); // Start week on Monday

        // Adjust if month starts after currentWeekStart (e.g., month starts on Wednesday, but currentWeekStart is Monday of previous week)
        if (currentWeekStart.isAfter(fechaInicioMes)) {
            currentWeekStart = currentWeekStart.minusWeeks(1);
        }

        while (!currentWeekStart.isAfter(fechaFinMes)) { // Iterate until past the end of the month
            List<LocalDate> semanaActual = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate dia = currentWeekStart.plusDays(i);
                semanaActual.add(dia);
            }
            semanas.add(semanaActual);
            currentWeekStart = currentWeekStart.plusWeeks(1);
        }
        return semanas;
    }

    public List<String> getDiasSemana() {
        return Arrays.asList("L", "M", "C", "J", "V", "S", "G"); // 'G' for Sunday
    }

    public String traducirDia(String diaAbr) {
        // Use getOrDefault to prevent NullPointerException if key is missing
        return traducciones.getOrDefault("day." + diaAbr, diaAbr);
    }

    public String getEstado(String sala, LocalDate fecha, String hora) {
        Map<LocalDate, Map<String, String>> fechas = agendaPorSala.get(sala);
        if (fechas == null) return "libre";

        Map<String, String> horarios = fechas.get(fecha);
        if (horarios != null && horarios.containsKey(hora)) {
            String actividad = horarios.get(hora);
            // Check if it's a "Cerrado" activity
            if (actividad != null && (actividad.equalsIgnoreCase(traducciones.getOrDefault("closed.activity", "Tancat")) || actividad.equalsIgnoreCase(traducciones.getOrDefault("closed.activity", "Cerrado")))) {
                return "cerrado";
            }
            return "ocupado";
        }
        return "libre";
    }

    public String getActividad(String sala, LocalDate fecha, String hora) {
        Map<LocalDate, Map<String, String>> fechas = agendaPorSala.get(sala);
        if (fechas == null) return "";

        Map<String, String> horarios = fechas.get(fecha);
        return (horarios != null && horarios.containsKey(hora)) ? horarios.get(hora) : "";
    }


    // Getters estándar
    public Map<String, Map<LocalDate, Map<String, String>>> getAgendaPorSala() {
        return agendaPorSala;
    }

    public List<String> getIncidencias() {
        return incidencias;
    }

    public int getNumeroSemana(LocalDate fecha) {
        return fecha.get(WeekFields.ISO.weekOfWeekBasedYear());
    }
}