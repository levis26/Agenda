package net.elpuig.Agenda.model;

import java.time.LocalDate;

public class Reserva {
    private String nombreActividad;
    private String sala;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String diasSemana;
    private String horarios;

    // Constructor
    public Reserva(String nombreActividad, String sala, LocalDate fechaInicio, LocalDate fechaFin, String diasSemana, String horarios) {
        this.nombreActividad = nombreActividad;
        this.sala = sala;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.diasSemana = diasSemana;
        this.horarios = horarios;
    }

    // Getters y Setters
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

    // Setters (si son necesarios)
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
}