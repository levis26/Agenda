package net.elpuig.Agenda.model;

import java.time.LocalDate;
import java.util.Objects;

public class Reserva {
    private String nombreActividad;
    private String sala;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String diasSemana; // e.g., "LMCJVSG"
    private String horarios;    // e.g., "08-10_14-16"

    public Reserva(String nombreActividad, String sala, LocalDate fechaInicio, LocalDate fechaFin, String diasSemana, String horarios) {
        this.nombreActividad = nombreActividad;
        this.sala = sala;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.diasSemana = diasSemana;
        this.horarios = horarios;
    }

    // Getters
    public String getNombreActividad() {
        return nombreActividad;
    }

    public String getSala() {
        return sala;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public String getDiasSemana() {
        return diasSemana;
    }

    public String getHorarios() {
        return horarios;
    }

    // Setters (if needed, though immutable objects are often preferred for data)
    public void setNombreActividad(String nombreActividad) {
        this.nombreActividad = nombreActividad;
    }

    public void setSala(String sala) {
        this.sala = sala;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
    }

    public void setDiasSemana(String diasSemana) {
        this.diasSemana = diasSemana;
    }

    public void setHorarios(String horarios) {
        this.horarios = horarios;
    }

    @Override
    public String toString() {
        return "Actividad: '" + nombreActividad + '\'' +
               ", Sala: '" + sala + '\'' +
               ", Fecha: " + fechaInicio + " a " + fechaFin +
               ", Días: '" + diasSemana + '\'' +
               ", Horarios: '" + horarios + '\'';
    }

    // hashCode and equals for proper collection behavior if needed
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reserva reserva = (Reserva) o;
        return Objects.equals(nombreActividad, reserva.nombreActividad) &&
               Objects.equals(sala, reserva.sala) &&
               Objects.equals(fechaInicio, reserva.fechaInicio) &&
               Objects.equals(fechaFin, reserva.fechaFin) &&
               Objects.equals(diasSemana, reserva.diasSemana) &&
               Objects.equals(horarios, reserva.horarios);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nombreActividad, sala, fechaInicio, fechaFin, diasSemana, horarios);
    }
}