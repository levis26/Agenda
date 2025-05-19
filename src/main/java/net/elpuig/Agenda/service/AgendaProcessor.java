package net.elpuig.Agenda.service;

import net.elpuig.Agenda.model.Reserva;
import org.slf4j.Logger; // Importar Logger
import org.slf4j.LoggerFactory; // Importar LoggerFactory
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service // Indica que esta clase es un componente de servicio de Spring
public class AgendaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(AgendaProcessor.class); // Añadir Logger

    // Mantenemos la lista de reservas válidas después del procesamiento
    private List<Reserva> reservasValidas = new ArrayList<>();
    // Mantenemos la lista de incidencias (combinará errores de carga y procesamiento)
    private List<String> incidencias = new ArrayList<>();
    // Cache para organizar las reservas válidas por sala
    private Map<String, List<Reserva>> agendaCache;

    // ** Modificamos el método procesarReservas para aceptar errores de carga **
    // Ahora recibe las reservas válidas cargadas por DataLoader Y los errores que encontró DataLoader
    public void procesarReservas(List<Reserva> loadedReservas, List<String> loadingErrors) {
        // Limpiar las listas de resultados del procesamiento anterior
        reservasValidas.clear();
        this.incidencias.clear(); // Limpiar la lista de incidencias del procesador

        // ** 1. Añadir los errores de carga recibidos del DataLoader a nuestra lista combinada **
        if (loadingErrors != null && !loadingErrors.isEmpty()) {
            this.incidencias.addAll(loadingErrors);
            logger.warn("Se añadieron {} errores de carga/validación de DataLoader.", loadingErrors.size());
        }

        // Resetear la cache de la agenda
        agendaCache = null;

        // Si no hay reservas cargadas válidas, terminamos aquí después de añadir los errores de carga
        if (loadedReservas == null || loadedReservas.isEmpty()) {
            logger.info("No hay reservas válidas cargadas para procesar.");
            return;
        }
        logger.info("Iniciando procesamiento de {} reservas válidas cargadas...", loadedReservas.size());

        // --- Implementación de la prioridad de "Tancat" ---

        // Separar las reservas "Tancat" del resto DE LA LISTA RECIBIDA del DataLoader
        List<Reserva> tancatReservas = loadedReservas.stream()
                .filter(r -> "Tancat".equals(r.getNombreActividad()))
                .collect(Collectors.toList());

        List<Reserva> otherReservas = loadedReservas.stream()
                .filter(r -> !"Tancat".equals(r.getNombreActividad()))
                .collect(Collectors.toList());

        // Opcional: Ordenar Tancat para procesamiento determinista si hay múltiples Tancat
        // Collections.sort(tancatReservas, Comparator.comparing(Reserva::getFechaInicio).thenComparing(Reserva::getHorarios));


        // 2. Procesar primero las reservas "Tancat"
        // Las reservas "Tancat" válidas (formato, fechas, horas) ya están en tancatReservas (DataLoader las validó)
        // Las añadimos directamente a reservasValidas. Su validación de conflictos se maneja en tieneConflictos
        // cuando otras reservas intentan solapar con ellas.
        for (Reserva reservaTancat : tancatReservas) {
            // Podemos hacer una validación de formato final aquí si no confiamos completamente en DataLoader
            // if (esFormatoReservaValido(reservaTancat)) { // esFormatoReservaValido añadiría errores a this.incidencias
            this.reservasValidas.add(reservaTancat);
            // No llamamos a tieneConflictos para Tancat, ya que no causan conflicto al añadirse ellas mismas
            // }
        }
        logger.info("Procesadas {} reservas 'Tancat'. Total reservas válidas hasta ahora: {}", tancatReservas.size(), this.reservasValidas.size());


        // 3. Procesar el resto de reservas (no-"Tancat")
        // Las reservas no-"Tancat" en otherReservas ya fueron validadas en formato/parseo por DataLoader.
        // Ahora las validamos contra las reservas que ya están en reservasValidas (incluyendo las Tancat).
        for (Reserva reserva : otherReservas) {
            // esReservaValida validará formato (redundante) Y conflictos (crucial)
            // esReservaValida y tieneConflictos añadirán incidencias si hay problemas
            if (esReservaValida(reserva)) {
                this.reservasValidas.add(reserva);
            } // Si no es válida (formato o conflicto), el error se añadió a this.incidencias
        }
        logger.info("Procesadas {} reservas no-'Tancat'. Total reservas válidas al final: {}", otherReservas.size(), this.reservasValidas.size());
        logger.info("Procesamiento de reservas completado.");


        // Opcional: Ordenar las reservas válidas finales para visualización si es necesario
        // Collections.sort(this.reservasValidas, Comparator.comparing(Reserva::getFechaInicio).thenComparing(Reserva::getSala).thenComparing(Reserva::getHorarios));
    }


    // Método para validar solo el formato básico (días, fechas, horarios) de una reserva
    // Añade incidencias a this.incidencias
    private boolean esFormatoReservaValido(Reserva reserva) {
        boolean valido = true;
        // Validaciones de formato de días y horarios (se asumen ya hechas en DataLoader, pero se repiten por seguridad)
        // Validaciones de fechas nulas o invertidas (se asumen ya hechas en DataLoader)

        if (reserva.getDiasSemana() == null || reserva.getDiasSemana().isBlank()) {
            this.incidencias.add("Incidencia de formato (Processor): Reserva sin días especificados: " + reserva.getNombreActividad());
            valido = false;
        } else if (!reserva.getDiasSemana().matches("^[LMCJVSG]+$")) {
            this.incidencias.add("Incidencia de formato (Processor): Días inválidos en reserva: " + reserva.getNombreActividad() + " - '" + reserva.getDiasSemana() + "'");
            valido = false;
        }

        // Validación de formato de horarios (reutilizamos lógica, pero añadimos a this.incidencias)
        try {
            // Usamos un método auxiliar que encapsula la lógica de validación de formato de horarios
            validarFormatoHorariosInterno(reserva.getHorarios());
        } catch (Exception e) {
            this.incidencias.add("Incidencia de formato (Processor): Formato horario inválido en reserva: " + reserva.getNombreActividad() + " - " + e.getMessage());
            valido = false;
        }

        // Validación de fechas nulas o invertidas (redundante si DataLoader valida, pero seguridad)
        if (reserva.getFechaInicio() == null || reserva.getFechaFin() == null) {
            this.incidencias.add("Incidencia de formato (Processor): Fechas nulas en reserva: " + reserva.getNombreActividad());
            valido = false;
        } else if (reserva.getFechaInicio().isAfter(reserva.getFechaFin())) {
            this.incidencias.add("Incidencia de formato (Processor): Fecha inicio posterior a fecha fin en reserva: " + reserva.getNombreActividad());
            valido = false;
        }


        return valido;
    }


    // Método para validar formato Y conflictos (usado para reservas no-"Tancat")
    // Añade incidencias a this.incidencias
    private boolean esReservaValida(Reserva reserva) {
        // Primero validamos formato básico de nuevo (seguridad, los errores se añaden a incidencias)
        if (!esFormatoReservaValido(reserva)) {
            return false; // Las incidencias de formato ya se añadieron
        }

        // Luego validamos conflictos de horarios con las reservas ya añadidas (incluyendo Tancat)
        // tieneConflictos añade incidencias si hay conflictos
        if (tieneConflictos(reserva)) {
            return false; // El conflicto ya fue añadido a incidencias
        }

        return true; // Pasa formato y no tiene conflictos
    }

    // Getters
    // ** Este getter devuelve la lista de incidencias combinada (carga + procesamiento) **
    public List<String> getIncidencias() {
        return incidencias;
    }

    public List<Reserva> getReservasValidas() {
        return reservasValidas;
    }


    public Map<String, List<Reserva>> getAgenda() {
        // Construir la cache si es la primera vez que se solicita
        if (agendaCache == null) {
            logger.info("Construyendo cache de agenda por sala...");
            agendaCache = new HashMap<>();
            for (Reserva reserva : reservasValidas) {
                // Agrupar reservas válidas por sala
                agendaCache.computeIfAbsent(reserva.getSala(), k -> new ArrayList<>()).add(reserva);
            }
            // Opcional: Ordenar las listas de reservas dentro del cache por fecha/hora para consistencia
            agendaCache.values().forEach(list -> Collections.sort(list, Comparator.comparing(Reserva::getFechaInicio).thenComparing(Reserva::getHorarios)));
            logger.info("Cache de agenda construida.");
        }
        return agendaCache;
    }

    // Método para verificar si una nueva reserva tiene conflictos con las ya validadas
    // Añade incidencias de conflicto a this.incidencias
    private boolean tieneConflictos(Reserva nuevaReserva) {
        // --- Implementación de la prioridad de "Tancat" en la detección de conflictos ---
        // Si la nueva reserva es "Tancat", nunca consideramos que causa un conflicto al añadirse ella misma.
        // Las reservas Tancat siempre prevalecen y marcan el espacio como "cerrado".
        if ("Tancat".equals(nuevaReserva.getNombreActividad())) {
            // No añadimos incidencia aquí, Tancat "gana" el espacio
            return false;
        }

        // Para todas las demás reservas (no-"Tancat"), verificamos si hay conflicto con CUALQUIER reserva válida existente (incluyendo Tancat)
        for (Reserva existente : reservasValidas) {
            boolean mismaSala = existente.getSala().equals(nuevaReserva.getSala());
            // Comprobar solapamiento de rangos de fechas
            boolean solapamientoFechas = !nuevaReserva.getFechaFin().isBefore(existente.getFechaInicio()) &&
                    !nuevaReserva.getFechaInicio().isAfter(existente.getFechaFin());

            if (mismaSala && solapamientoFechas) {
                // Si hay solapamiento de fechas y es la misma sala, validar solapamiento de horas dentro de esos días solapados
                if (existeSolapamientoHorario(existente, nuevaReserva)) {
                    // Si encontramos un conflicto para una reserva NO-"Tancat", añadimos la incidencia y retornamos true
                    // El mensaje de incidencia ya incluye la sala y el nombre de actividad de la nueva reserva
                    this.incidencias.add("Conflicto de horario (Processor): '" + nuevaReserva.getNombreActividad() + "' en sala '" + nuevaReserva.getSala() + "' se solapa con '" + existente.getNombreActividad() + "'.");
                    return true; // Hay un conflicto
                }
            }
        }
        // Si terminamos el bucle sin encontrar conflictos, la nueva reserva es válida con respecto a las existentes
        return false;
    }

    // Método auxiliar interno para validar el formato de horarios (similal a DataLoader pero añade a this.incidencias)
    // Lanza excepción si el formato es inválido (ej. para usar en try-catch)
    private void validarFormatoHorariosInterno(String horariosStr) throws Exception {
        if (horariosStr == null || horariosStr.isBlank()) {
            throw new Exception("Cadena de horarios vacía o nula");
        }
        String[] horarios = horariosStr.split("_");
        if (horarios.length == 0 || horarios.length > 5) { // Máximo 5 franjas horarias según descripción
            throw new Exception("Número de franjas horarias inválido (" + horarios.length + "). Máximo 5 separadas por '_'.");
        }
        for (String horario : horarios) {
            if (horario == null || horario.isBlank()) {
                throw new Exception("Franja horaria individual vacía o nula dentro de la máscara.");
            }
            String[] horas = horario.split("-");
            if (horas.length != 2) throw new Exception("Formato de franja horaria individual inválido: '" + horario + "'. Esperado HH-HH.");
            try {
                int inicio = Integer.parseInt(horas[0]);
                int fin = Integer.parseInt(horas[1]);

                // Validar límites básicos y orden (inicio antes que fin)
                if (inicio < 0 || fin < 0 || inicio > 24 || fin > 24 || inicio >= fin) {
                    throw new Exception("Rango horario inválido en franja '" + horario + "': inicio=" + inicio + ", fin=" + fin + ". Asegúrate que 0 <= inicio < fin <= 24 y inicio < fin.");
                }
                // La validación de duración mínima de 1 hora (fin - inicio >= 1) está cubierta por inicio < fin

            } catch (NumberFormatException e) {
                throw new Exception("Formato numérico inválido en franja horaria '" + horario + "': " + e.getMessage());
            }
        }
    }


    // Método auxiliar para verificar si dos rangos de horario (ej. "0800-1000") se solapan
    private boolean existeSolapamientoHorario(Reserva existente, Reserva nueva) {
        // Asumimos que las cadenas de horarios ya vienen validadas en formato HH-HH separadas por _

        String[] horariosExistente = existente.getHorarios().split("_");
        String[] horariosNueva = nueva.getHorarios().split("_");

        // Comparamos cada franja horaria de la reserva existente con cada franja de la nueva reserva
        for (String hExistenteStr : horariosExistente) {
            if (hExistenteStr == null || hExistenteStr.isBlank()) continue;
            String[] partesExistente = hExistenteStr.split("-");
            if (partesExistente.length != 2) continue; // Debería estar validado, pero seguridad

            for (String hNuevaStr : horariosNueva) {
                if (hNuevaStr == null || hNuevaStr.isBlank()) continue;
                String[] partesNueva = hNuevaStr.split("-");
                if (partesNueva.length != 2) continue; // Debería estar validado, pero seguridad

                try {
                    // Parsear las horas de inicio y fin como enteros
                    int inicioExistente = Integer.parseInt(partesExistente[0]);
                    int finExistente = Integer.parseInt(partesExistente[1]);
                    int inicioNueva = Integer.parseInt(partesNueva[0]);
                    int finNueva = Integer.parseInt(partesNueva[1]);

                    // Comprobar si los dos rangos de tiempo se solapan.
                    // Solapamiento ocurre si el inicio de uno es antes del fin del otro Y el fin de uno es después del inicio del otro.
                    // Es decir, si NO es cierto que (uno termina antes que el otro empiece) O (el otro termina antes que el uno empiece).
                    // O: (inicioNueva < finExistente) && (finNueva > inicioExistente)
                    // O: (inicioExistente < finNueva) && (finExistente > inicioNueva) <-- Esta es equivalente y a veces más clara

                    if ((inicioNueva < finExistente) && (finNueva > inicioExistente)) {
                        // Se encontró un solapamiento entre la franja existente y la nueva
                        return true; // Hay solapamiento, la nueva reserva tiene conflicto
                    }
                    // No solapamiento entre este par específico, seguir probando otros pares
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    // Si llegamos aquí, significa que hubo un problema parseando las partes del horario
                    // que debería haber sido capturado por la validación de formato.
                    // Lo loggeamos como advertencia y asumimos que no hay solapamiento para este par malformado.
                    logger.warn("Error interno o datos malformados al comparar horarios '{}' y '{}' en existeSolapamientoHorario: {}", hExistenteStr, hNuevaStr, e.getMessage());
                    // Continuar con el siguiente par de horarios
                }
            }
        }
        // Si terminamos de comparar todas las franjas sin encontrar solapamiento, no hay conflicto horario
        return false;
    }
}