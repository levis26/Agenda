package net.elpuig.Agenda.service;

import net.elpuig.Agenda.model.Reserva;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AgendaProcessor {
    private List<Reserva> reservasValidas = new ArrayList<>();
    private List<String> incidencias = new ArrayList<>();

    // Internal representation of occupied slots for conflict detection:
    // Map: Sala -> Date -> Hour Range (e.g., "08:00-09:00") -> Activity Name (or "Tancat")
    private Map<String, Map<LocalDate, Map<String, String>>> ocupacionSlots = new HashMap<>();

    // Define a formatter here, or reuse the one from DataLoader if it was public/accessible
    // This is needed for formatting the date in the incidence messages
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public void procesarReservas(List<Reserva> reservas, Map<String, String> traducciones) {
        reservasValidas.clear();
        incidencias.clear();
        ocupacionSlots.clear(); // Clear occupied slots for each new processing cycle

        for (Reserva reserva : reservas) {
            boolean isConflicting = false;
            List<String> currentReservaIncidencias = new ArrayList<>(); // To collect incidencias for the current reservation

            // Iterate over each day within the reservation's date range
            for (LocalDate fecha = reserva.getFechaInicio(); !fecha.isAfter(reserva.getFechaFin()); fecha = fecha.plusDays(1)) {
                // Check if the current day of the week is included in the reservation
                if (isDayIncluded(fecha.getDayOfWeek(), reserva.getDiasSemana())) {
                    // Check for conflicts within this specific day
                    String[] horarios = reserva.getHorarios().split("_");
                    for (String horarioStr : horarios) {
                        try {
                            String[] partesHorario = horarioStr.split("-");
                            int inicioHora = Integer.parseInt(partesHorario[0]);
                            int finHora = Integer.parseInt(partesHorario[1]);

                            for (int h = inicioHora; h < finHora; h++) {
                                String slot = String.format("%02d:00-%02d:00", h, h + 1);

                                // Check if this slot is already occupied by a previously accepted reservation
                                if (isSlotOccupied(reserva.getSala(), fecha, slot)) {
                                    currentReservaIncidencias.add("Conflicto detectado para '" + reserva.getNombreActividad() + "' en Sala '" + reserva.getSala() + "' el " + fecha.format(DATE_FORMATTER) + " a las " + slot + ".");
                                    isConflicting = true;
                                    break; // Stop checking for this slot, move to next reservation if conflicting
                                }
                            }
                        } catch (NumberFormatException e) {
                            currentReservaIncidencias.add("Error de formato de hora en reserva '" + reserva.getNombreActividad() + "': " + horarioStr);
                            isConflicting = true;
                        }
                        if (isConflicting) break; // Break from inner loop if conflict found
                    }
                }
                if (isConflicting) break; // Break from date loop if conflict found
            }

            if (isConflicting) {
                // If any conflict was found for the current reservation, add all its collected incidencias
                incidencias.addAll(currentReservaIncidencias);
            } else {
                // If no conflicts, add to valid reservations and mark the slots as occupied
                reservasValidas.add(reserva);
                // Mark the slots for this reservation as occupied (only if valid)
                for (LocalDate fecha = reserva.getFechaInicio(); !fecha.isAfter(reserva.getFechaFin()); fecha = fecha.plusDays(1)) {
                    if (isDayIncluded(fecha.getDayOfWeek(), reserva.getDiasSemana())) {
                        String[] horarios = reserva.getHorarios().split("_");
                        for (String horarioStr : horarios) {
                            String[] partesHorario = horarioStr.split("-");
                            int inicioHora = Integer.parseInt(partesHorario[0]);
                            int finHora = Integer.parseInt(partesHorario[1]);
                            for (int h = inicioHora; h < finHora; h++) {
                                String slot = String.format("%02d:00-%02d:00", h, h + 1);
                                markSlotOccupied(reserva.getSala(), fecha, slot, reserva.getNombreActividad());
                            }
                        }
                    }
                }
            }
        }
    }

    // Getters
    public List<Reserva> getReservasValidas() {
        return reservasValidas;
    }

    public List<String> getIncidencias() {
        return incidencias;
    }

    // This method needs to check against `ocupacionSlots` not `reservasValidas`
    private boolean isSlotOccupied(String sala, LocalDate fecha, String slot) {
        return ocupacionSlots.getOrDefault(sala, Collections.emptyMap())
                             .getOrDefault(fecha, Collections.emptyMap())
                             .containsKey(slot);
    }

    // This method is used to mark slots as occupied after a reservation is deemed valid
    private void markSlotOccupied(String sala, LocalDate fecha, String slot, String actividad) {
        ocupacionSlots.computeIfAbsent(sala, k -> new HashMap<>())
                      .computeIfAbsent(fecha, k -> new HashMap<>())
                      .put(slot, actividad);
    }

    // This helper method is crucial for processing (it was in the previous solution)
    private boolean isDayIncluded(DayOfWeek dayOfWeek, String diasSemanaCode) {
        String dayCode = "";
        switch (dayOfWeek) {
            case MONDAY: dayCode = "L"; break;
            case TUESDAY: dayCode = "M"; break;
            case WEDNESDAY: dayCode = "C"; break;
            case THURSDAY: dayCode = "J"; break;
            case FRIDAY: dayCode = "V"; break;
            case SATURDAY: dayCode = "S"; break;
            case SUNDAY: dayCode = "G"; break; // 'G' for Sunday in Catalan (Diumenge)
        }
        return diasSemanaCode.contains(dayCode);
    }
}