package net.elpuig.Agenda.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

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
        return mesProcesar != null ? mesProcesar.getYear() : LocalDate.now().getYear();
    }


    public YearMonth getMesProcesar() {
        return mesProcesar;
    }

    // -- INICIO: Implementación CORRECTA de getSemanas() --

    public List<List<LocalDate>> getSemanas() {
        List<List<LocalDate>> semanas = new ArrayList<>();
        LocalDate fechaInicioMes = mesProcesar.atDay(1);
        LocalDate fechaFinMes = mesProcesar.atEndOfMonth();


        LocalDate currentWeekStart = fechaInicioMes.with(DayOfWeek.MONDAY);


        // Encuentra el primer lunes (o el inicio de la semana configurado)
        // que cae en o antes del inicio del mes.
        LocalDate currentWeekStart = fechaInicioMes.with(DayOfWeek.MONDAY);

        // Ajusta si el mes comienza en un día *después* del lunes
        // (ej. el mes comienza el miércoles, por lo que currentWeekStart es el lunes de la semana *anterior*).
        // Esto asegura que la primera semana mostrada incluya correctamente los días del mes anterior
        // si el mes no comienza en lunes.

        if (currentWeekStart.isAfter(fechaInicioMes)) {
            currentWeekStart = currentWeekStart.minusWeeks(1);
        

        // Itera a través de las semanas hasta que pasemos el final del mes

        while (!currentWeekStart.isAfter(fechaFinMes)) {
            List<LocalDate> semanaActual = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate dia = currentWeekStart.plusDays(i);
                semanaActual.add(dia);
            }
            semanas.add(semanaActual);
            currentWeekStart = currentWeekStart.plusWeeks(1); // Mueve al inicio de la siguiente semana
        }
        return semanas;
    }
    // -- FIN: Implementación CORRECTA de getSemanas() --

    public List<String> getDiasSemana() {
        return Arrays.asList("L", "M", "C", "J", "V", "S", "D");
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
    // -- FIN: Nuevo método estático getCodigoDia --


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

    public List<String> getHorariosUnicos() {
        // Genera una lista ordenada de todos los rangos horarios únicos presentes en la agenda
        // Esto es útil si los horarios no son siempre 00:00-01:00, 01:00-02:00, etc.
        return agendaPorSala.values().stream()
                .flatMap(fechas -> fechas.values().stream())
                .flatMap(horarios -> horarios.keySet().stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public int getNumeroSemana(LocalDate fecha) {
        // Ajustar el WeekFields para que la semana empiece en Lunes (o el día que consideres)
        // y el primer día del año pertenezca a la primera semana
        WeekFields weekFields = WeekFields.of(DayOfWeek.MONDAY, 1);
        return fecha.get(weekFields.weekOfWeekBasedYear());
    }
}