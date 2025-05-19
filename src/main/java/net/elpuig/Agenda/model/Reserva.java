package net.elpuig.Agenda.model;

import java.time.LocalDate;

public class Reserva {
    private String nombreActividad;
    private String sala;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String diasSemana; // Máscara de días (ej. LMCJVSG)
    private String horarios;     // Máscara de horarios (ej. 0800-1000_1900-2100)

    // Constructor
    public Reserva(String nombreActividad, String sala, LocalDate fechaInicio, LocalDate fechaFin, String diasSemana, String horarios) {
        this.nombreActividad = nombreActividad;
        this.sala = sala;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.diasSemana = diasSemana;
        this.horarios = horarios;
    }

    // Getters (necesarios para acceder a los datos de la reserva)
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

    // Setters (pueden ser útiles, aunque el constructor ya inicializa todo)
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
        return "Reserva{" +
                "nombreActividad='" + nombreActividad + '\'' +
                ", sala='" + sala + '\'' +
                ", fechaInicio=" + fechaInicio +
                ", fechaFin=" + fechaFin +
                ", diasSemana='" + diasSemana + '\'' +
                ", horarios='" + horarios + '\'' +
                '}';
    }
}