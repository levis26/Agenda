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

    // Necesitamos las traducciones aquí para identificar "Tancat"
    private Map<String, String> traducciones;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public void procesarReservas(List<Reserva> reservas, Map<String, String> traducciones) {
        this.traducciones = traducciones; // Almacenar las traducciones
        reservasValidas.clear();
        incidencias.clear();
        ocupacionSlots.clear();

        // PASO 1: Procesar primero las reservas con "Tancat" (o su traducción) para darles prioridad
        List<Reserva> reservasConTancat = new ArrayList<>();
        List<Reserva> otrasReservas = new ArrayList<>();

        String closedActivityName = traducciones.getOrDefault("activity.closed", "Tancat"); // Usar la clave de traducción

        for (Reserva r : reservas) {
            if (r.getNombreActividad().equalsIgnoreCase(closedActivityName)) {
                reservasConTancat.add(r);
            } else {
                otrasReservas.add(r);
            }
        }

        // Procesar reservas con "Tancat" primero (siempre se aceptan y ocupan el slot)
        for (Reserva reserva : reservasConTancat) {
            // "Tancat" siempre se considera válido y ocupa el slot
            reservasValidas.add(reserva);
            markSlotsForReserva(reserva, reserva.getNombreActividad());
        }

        // PASO 2: Procesar las otras reservas, verificando conflictos con las ya establecidas (incluido "Tancat")
        for (Reserva reserva : otrasReservas) {
            boolean isConflicting = false;
            List<String> currentReservaIncidencias = new ArrayList<>();

            // Iterar sobre cada día dentro del rango de la reserva
            for (LocalDate fecha = reserva.getFechaInicio(); !fecha.isAfter(reserva.getFechaFin()); fecha = fecha.plusDays(1)) {
                if (isDayIncluded(fecha.getDayOfWeek(), reserva.getDiasSemana())) {
                    String[] horarios = reserva.getHorarios().split("_");
                    for (String horarioStr : horarios) {
                        try {
                            String[] partesHorario = horarioStr.split("-");
                            int inicioHora = Integer.parseInt(partesHorario[0]);
                            int finHora = Integer.parseInt(partesHorario[1]);

                            for (int h = inicioHora; h < finHora; h++) {
                                // MODIFICACIÓN: Ajustar el formato de la hora final a 00 si es 24
                                String slot = String.format("%02d:00-%02d:00", h, (h + 1) == 24 ? 0 : h + 1);

                                // Check if this slot is already occupied by a previously accepted reservation
                                if (isSlotOccupied(reserva.getSala(), fecha, slot)) {
                                    currentReservaIncidencias.add("Conflicto detectado para '" + reserva.getNombreActividad() + "' en Sala '" + reserva.getSala() + "' el " + fecha.format(DATE_FORMATTER) + " a las " + slot + ". Ocupado por '" + getSlotActivity(reserva.getSala(), fecha, slot) + "'.");
                                    isConflicting = true;
                                    break; // Stop checking for this slot, move to next reservation if conflicting
                                }
                            }
                        } catch (NumberFormatException e) {
                            currentReservaIncidencias.add("Error de formato de hora en reserva '" + reserva.getNombreActividad() + "': " + horarioStr + ".");
                            isConflicting = true;
                        }
                        if (isConflicting) break; // Romper el bucle de horarios
                    }
                }
                if (isConflicting) break; // Romper el bucle de fechas
            }

            if (isConflicting) {
                incidencias.addAll(currentReservaIncidencias);
            } else {
                reservasValidas.add(reserva);
                markSlotsForReserva(reserva, reserva.getNombreActividad());
            }
        }
    }

    // Nuevo método auxiliar para marcar los slots de una reserva
    private void markSlotsForReserva(Reserva reserva, String actividadParaSlot) {
        for (LocalDate fecha = reserva.getFechaInicio(); !fecha.isAfter(reserva.getFechaFin()); fecha = fecha.plusDays(1)) {
            if (isDayIncluded(fecha.getDayOfWeek(), reserva.getDiasSemana())) {
                String[] horarios = reserva.getHorarios().split("_");
                for (String horarioStr : horarios) {
                    String[] partesHorario = horarioStr.split("-");
                    int inicioHora = Integer.parseInt(partesHorario[0]);
                    int finHora = Integer.parseInt(partesHorario[1]);
                    for (int h = inicioHora; h < finHora; h++) {
                        // MODIFICACIÓN: Ajustar el formato de la hora final a 00 si es 24
                        String slot = String.format("%02d:00-%02d:00", h, (h + 1) == 24 ? 0 : h + 1);
                        markSlotOccupied(reserva.getSala(), fecha, slot, actividadParaSlot);
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

    private boolean isSlotOccupied(String sala, LocalDate fecha, String slot) {
        return ocupacionSlots.getOrDefault(sala, Collections.emptyMap())
                .getOrDefault(fecha, Collections.emptyMap())
                .containsKey(slot);
    }

    // Nuevo método para obtener la actividad que ocupa un slot
    private String getSlotActivity(String sala, LocalDate fecha, String slot) {
        return ocupacionSlots.getOrDefault(sala, Collections.emptyMap())
                .getOrDefault(fecha, Collections.emptyMap())
                .getOrDefault(slot, "");
    }


    private void markSlotOccupied(String sala, LocalDate fecha, String slot, String actividad) {
        ocupacionSlots.computeIfAbsent(sala, k -> new HashMap<>())
                .computeIfAbsent(fecha, k -> new HashMap<>())
                .put(slot, actividad);
    }

    private boolean isDayIncluded(DayOfWeek dayOfWeek, String diasSemanaCode) {
        String dayCode = "";
        switch (dayOfWeek) {
            case MONDAY: dayCode = "L"; break;
            case TUESDAY: dayCode = "M"; break;
            case WEDNESDAY: dayCode = "C"; break;
            case THURSDAY: dayCode = "J"; break;
            case FRIDAY: dayCode = "V"; break;
            case SATURDAY: dayCode = "S"; break;
            case SUNDAY: dayCode = "G"; break;
        }
        return diasSemanaCode.contains(dayCode);
    }
}