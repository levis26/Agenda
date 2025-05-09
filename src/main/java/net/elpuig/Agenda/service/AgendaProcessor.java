package net.elpuig.Agenda.service;

import net.elpuig.Agenda.model.Reserva;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class AgendaProcessor {
    private List<Reserva> reservasValidas = new ArrayList<>();
    private List<String> incidencias = new ArrayList<>();

    public void procesarReservas(List<Reserva> reservas) {
        // Lógica para validar y agrupar reservas por sala
        // (detectar conflictos, generar estructura de agenda, etc.)
    }

    // AgendaProcessor.java
    public Map<String, List<Reserva>> getAgenda() {
        // Lógica para agrupar reservas por sala en listas
        Map<String, List<Reserva>> agenda = new HashMap<>();
        for (Reserva reserva : reservasValidas) {
            agenda.computeIfAbsent(reserva.getSala(), k -> new ArrayList<>()).add(reserva);
        }
        return agenda;
    }

    public List<String> getIncidencias() {
        return incidencias;
    }
}