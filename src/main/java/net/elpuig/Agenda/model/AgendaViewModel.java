package net.elpuig.Agenda.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

public class AgendaViewModel {
    private Map<String, Map<LocalDate, Map<String, String>>> agendaPorSala;
    private List<String> incidencias;
    private YearMonth mesProcesar;
    private Map<String, String> traducciones;

    public AgendaViewModel(YearMonth mesProcesar, Map<String, String> traducciones) {
        this.mesProcesar = mesProcesar;
        this.traducciones = traducciones;
        this.agendaPorSala = new HashMap<>();
        this.incidencias = new ArrayList<>();
    }

    // Métodos para agregar datos
    public void addReserva(String sala, LocalDate fecha, String hora, String actividad) {
        agendaPorSala.computeIfAbsent(sala, k -> new HashMap<>())
                .computeIfAbsent(fecha, k -> new HashMap<>())
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
        return traducciones.get("month." + mesProcesar.getMonthValue());
    }

    public int getAnyo() {
        return mesProcesar != null ? mesProcesar.getYear() : LocalDate.now().getYear();
    }

    // -- INICIO: Implementación CORRECTA de getSemanas() --
    public List<List<LocalDate>> getSemanas() {
        List<List<LocalDate>> semanas = new ArrayList<>();
        LocalDate fechaInicioMes = mesProcesar.atDay(1);
        LocalDate fechaFinMes = mesProcesar.atEndOfMonth();

        // Encuentra el primer lunes (o el inicio de la semana configurado)
        // que cae en o antes del inicio del mes.
        LocalDate currentWeekStart = fechaInicioMes.with(DayOfWeek.MONDAY);

        // Ajusta si el mes comienza en un día *después* del lunes
        // (ej. el mes comienza el miércoles, por lo que currentWeekStart es el lunes de la semana *anterior*).
        // Esto asegura que la primera semana mostrada incluya correctamente los días del mes anterior
        // si el mes no comienza en lunes.
        if (currentWeekStart.isAfter(fechaInicioMes)) {
            currentWeekStart = currentWeekStart.minusWeeks(1);
        }

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

    public String traducirDia(String dia) {
        return traducciones.get("day." + dia);
    }

    // -- INICIO: Nuevo método estático getCodigoDia --
    /**
     * Convierte un objeto DayOfWeek de Java al código de un solo carácter (L, M, C, J, V, S, D).
     * @param dayOfWeek El día de la semana (ej. DayOfWeek.MONDAY).
     * @return El código de un solo carácter para el día (ej. "L").
     */
    public static String getCodigoDia(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "L";
            case TUESDAY -> "M";
            case WEDNESDAY -> "C";
            case THURSDAY -> "J";
            case FRIDAY -> "V";
            case SATURDAY -> "S";
            case SUNDAY -> "D";
            default -> throw new IllegalArgumentException("Día de la semana inválido: " + dayOfWeek);
        };
    }
    // -- FIN: Nuevo método estático getCodigoDia --


    public String getEstado(String sala, LocalDate fecha, String hora) {
        Map<LocalDate, Map<String, String>> fechas = agendaPorSala.get(sala);
        if (fechas == null) return "libre";
        
        Map<String, String> horarios = fechas.get(fecha);
        return (horarios != null && horarios.containsKey(hora)) ? "ocupado" : "libre";
    }

    // Getters estándar
    public Map<String, Map<LocalDate, Map<String, String>>> getAgendaPorSala() {
        return agendaPorSala;
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