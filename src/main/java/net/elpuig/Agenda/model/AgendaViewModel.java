package net.elpuig.Agenda.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.*;

public class AgendaViewModel {
    private String nombreSala; // Nuevo campo para el nombre de la sala
    private Map<LocalDate, Map<String, String>> agendaPorDiaHora; // Agenda solo para esta sala
    private List<String> incidencias; // Incidencias específicas de esta sala (opcional, o global)
    private YearMonth mesProcesar;
    private Map<String, String> traducciones;

    // Constructor actualizado para incluir el nombre de la sala
    public AgendaViewModel(String nombreSala, YearMonth mesProcesar, Map<String, String> traducciones) {
        this.nombreSala = nombreSala;
        this.mesProcesar = mesProcesar;
        this.traducciones = traducciones;
        this.agendaPorDiaHora = new TreeMap<>(); // TreeMap for sorted dates
        this.incidencias = new ArrayList<>(); // Inicializar para incidencias específicas de sala si se usan
    }

    // Métodos para agregar datos (solo para esta sala)
    public void addReserva(LocalDate fecha, String hora, String actividad) {
        agendaPorDiaHora.computeIfAbsent(fecha, k -> new TreeMap<>())
                .put(hora, actividad);
    }

    public void addIncidencia(String incidencia) {
        this.incidencias.add(incidencia);
    }

    // Getters para Thymeleaf

    public String getNombreSala() { // Nuevo getter
        return nombreSala;
    }

    public String getMesNombre() {
        if (mesProcesar == null) {
            return "Mes no especificado";
        }
        return traducciones.getOrDefault("month." + mesProcesar.getMonthValue(), "Unknown Month");
    }

    public int getAnyo() {
        return mesProcesar != null ? mesProcesar.getYear() : 0;
    }

    public YearMonth getMesProcesar() {
        return mesProcesar;
    }

    public List<List<LocalDate>> getSemanas() {
        List<List<LocalDate>> semanas = new ArrayList<>();
        LocalDate fechaInicioMes = mesProcesar.atDay(1);
        LocalDate fechaFinMes = mesProcesar.atEndOfMonth();

        LocalDate currentWeekStart = fechaInicioMes.with(DayOfWeek.MONDAY);

        if (currentWeekStart.isAfter(fechaInicioMes)) {
            currentWeekStart = currentWeekStart.minusWeeks(1);
        }

        while (!currentWeekStart.isAfter(fechaFinMes)) {
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
        return traducciones.getOrDefault("day." + diaAbr, diaAbr);
    }

    public String getEstado(LocalDate fecha, String hora) { // Ya no necesita 'sala'
        Map<String, String> horarios = agendaPorDiaHora.get(fecha);
        if (horarios == null) return "libre";

        if (horarios.containsKey(hora)) {
            String actividad = horarios.get(hora);
            if (actividad != null && actividad.equalsIgnoreCase(traducciones.getOrDefault("activity.closed", "Tancat"))) {
                return "cerrado";
            }
            return "ocupado";
        }
        return "libre";
    }

    public String getActividad(LocalDate fecha, String hora) { // Ya no necesita 'sala'
        Map<String, String> horarios = agendaPorDiaHora.get(fecha);
        return (horarios != null && horarios.containsKey(hora)) ? horarios.get(hora) : "";
    }

    // Getters estándar
    public Map<LocalDate, Map<String, String>> getAgendaPorDiaHora() {
        return agendaPorDiaHora;
    }

    public List<String> getIncidencias() {
        return incidencias;
    }

    public int getNumeroSemana(LocalDate fecha) {
        return fecha.get(WeekFields.ISO.weekOfWeekBasedYear());
    }
}