package net.elpuig.Agenda.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
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
        return traducciones.get("month." + mesProcesar.getMonthValue());
    }

    public int getAnyo() {
        return mesProcesar.getYear();
    }

    public List<List<LocalDate>> getSemanas() {
        List<LocalDate> todosLosDias = mesProcesar.atDay(1).datesUntil(mesProcesar.atEndOfMonth()).collect(Collectors.toList());
        List<List<LocalDate>> semanas = new ArrayList<>();
        List<LocalDate> semanaActual = new ArrayList<>();
        for (LocalDate dia : todosLosDias) {
            semanaActual.add(dia);
            if (dia.getDayOfWeek() == DayOfWeek.SUNDAY || dia.equals(mesProcesar.atEndOfMonth())) {
                semanas.add(semanaActual);
                semanaActual = new ArrayList<>();
            }
        }
        return semanas;
    }

    public List<String> getDiasSemana() {
        return Arrays.asList("L", "M", "C", "J", "V", "S", "D");
    }

    public String traducirDia(String dia) {
        return traducciones.get("day." + dia);
    }

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
        return agendaPorSala.values().stream()
                .flatMap(fechas -> fechas.values().stream())
                .flatMap(horarios -> horarios.keySet().stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}