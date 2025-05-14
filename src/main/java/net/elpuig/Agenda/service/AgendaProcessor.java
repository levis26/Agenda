package net.elpuig.Agenda.service;

import net.elpuig.Agenda.model.Reserva;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AgendaProcessor {
    private List<Reserva> reservasValidas = new ArrayList<>();
    private List<String> incidencias = new ArrayList<>();
    private Map<String, List<Reserva>> agendaCache;

    public void procesarReservas(List<Reserva> reservas) {
        reservasValidas.clear();
        incidencias.clear();
        agendaCache = null; // Resetear caché

        for (Reserva reserva : reservas) {
            if (esReservaValida(reserva)) {
                reservasValidas.add(reserva);
            } else {
                incidencias.add("Conflicto detectado: " + reserva.getNombreActividad());
            }
        }
    }


    // Ejemplo de implementación avanzada de esReservaValida
    private boolean esReservaValida(Reserva reserva) {
        // Primero validar si diasSemana es nulo o vacío
        if (reserva.getDiasSemana() == null || reserva.getDiasSemana().isBlank()) {
            incidencias.add("Reserva sin días especificados: " + reserva.getNombreActividad());
            return false;
        }

        // Luego validar el formato de los días
        if (!reserva.getDiasSemana().matches("^[LMCJVSG]+$")) {
            incidencias.add("Días inválidos en reserva: " + reserva.getNombreActividad());
            return false;
        }

        // Validar conflictos de horarios
        if (tieneConflictos(reserva)) {
            incidencias.add("Conflicto de horario en reserva: " + reserva.getNombreActividad());
            return false;
        }

        return true;
    }

    // Getters
    public List<Reserva> getReservasValidas() {
        return reservasValidas;
    }

    public List<String> getIncidencias() {
        return incidencias;
    }

    public Map<String, List<Reserva>> getAgenda() {
        if (agendaCache == null) {
            agendaCache = new HashMap<>();
            for (Reserva reserva : reservasValidas) {
                agendaCache.computeIfAbsent(reserva.getSala(), k -> new ArrayList<>()).add(reserva);
            }
        }
        return agendaCache;
    }

    private boolean tieneConflictos(Reserva nuevaReserva) {
        for (Reserva existente : reservasValidas) {
            boolean mismaSala = existente.getSala().equals(nuevaReserva.getSala());
            boolean solapamientoFechas = !nuevaReserva.getFechaFin().isBefore(existente.getFechaInicio()) &&
                    !nuevaReserva.getFechaInicio().isAfter(existente.getFechaFin());

            if (mismaSala && solapamientoFechas) {
                // Validar solapamiento de horas dentro del mismo día
                if (existeSolapamientoHorario(existente, nuevaReserva)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean existeSolapamientoHorario(Reserva existente, Reserva nueva) {
        String[] horariosExistente = existente.getHorarios().split("_");
        String[] horariosNueva = nueva.getHorarios().split("_");
        // Comparar cada horario de ambas reservas
        for (String hExistente : horariosExistente) {
            for (String hNueva : horariosNueva) {
                int inicioExistente = Integer.parseInt(hExistente.split("-")[0]);
                int finExistente = Integer.parseInt(hExistente.split("-")[1]);
                int inicioNueva = Integer.parseInt(hNueva.split("-")[0]);
                int finNueva = Integer.parseInt(hNueva.split("-")[1]);
                if (inicioNueva < finExistente && finNueva > inicioExistente) {
                    return true;
                }
            }
        }
        return false;
    }

}