package net.elpuig.Agenda.controller;

import net.elpuig.Agenda.model.AgendaViewModel;
import net.elpuig.Agenda.model.Reserva;
import net.elpuig.Agenda.service.AgendaProcessor;
import net.elpuig.Agenda.service.DataLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable; // Nuevo import
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


import java.time.DayOfWeek; // No se usa en el controller, se puede quitar
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors; // Para collect


@Controller
public class AgendaController {

    private static final Logger logger = LoggerFactory.getLogger(AgendaController.class);

    @Autowired
    private AgendaProcessor agendaProcessor;

    @Autowired
    private DataLoader dataLoader;


    // Mapa para almacenar AgendaViewModel por nombre de sala
    private Map<String, AgendaViewModel> agendaViewModelsPorSala;
    private List<String> globalIncidencias; // Incidencias globales (parseo, etc.)

    @GetMapping("/")
    public String redirectToUpload() {
        return "redirect:/upload";
    }

    @GetMapping("/upload")
    public String mostrarFormulario(Model model) {
        model.addAttribute("error", model.asMap().get("error"));
        // También pasar incidencias globales si hay
        model.addAttribute("incidencias", model.asMap().get("incidencias"));
        return "upload";
    }

    @PostMapping("/procesar")
    public String procesarArchivos(
            @RequestParam("configFile") MultipartFile configFile,
            @RequestParam("peticionesFile") MultipartFile peticionesFile,
            RedirectAttributes redirectAttributes) {

        logger.info("Iniciando procesamiento de archivos.");

        try {
            dataLoader.loadConfig(configFile);

            List<String> parsingIncidencias = new ArrayList<>();
            List<Reserva> reservasInput = dataLoader.loadPeticiones(peticionesFile, parsingIncidencias);

            agendaProcessor.procesarReservas(reservasInput, dataLoader.getTraducciones());

            // Combinar todas las incidencias
            globalIncidencias = new ArrayList<>(parsingIncidencias);
            globalIncidencias.addAll(agendaProcessor.getIncidencias());

            redirectAttributes.addFlashAttribute("incidencias", globalIncidencias);

            // Construir los ViewModels para cada sala
            this.agendaViewModelsPorSala = construirAgendasPorSala(
                    agendaProcessor.getReservasValidas(),
                    dataLoader.getMesProcesar(),
                    dataLoader.getTraducciones()
            );

            // Redirigir a la primera sala o a una página de índice de salas
            if (agendaViewModelsPorSala.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "No se pudieron procesar reservas válidas para ninguna sala.");
                return "redirect:/upload";
            }
            // Redirige a la primera sala que encuentre en el mapa
            String primeraSala = agendaViewModelsPorSala.keySet().iterator().next();
            return "redirect:/agenda/" + primeraSala;

        } catch (Exception e) {
            logger.error("Error al procesar archivos: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error al procesar los archivos: " + e.getMessage());
            if (!dataLoader.getIncidenciasCarga().isEmpty()) {
                redirectAttributes.addFlashAttribute("incidenciasCarga", dataLoader.getIncidenciasCarga());
            }
            if (!agendaProcessor.getIncidencias().isEmpty()) {
                redirectAttributes.addFlashAttribute("incidenciasProcesamiento", agendaProcessor.getIncidencias());
            }
            return "redirect:/upload";
        }
    }


    @GetMapping("/agenda/{nombreSala}")
    public String mostrarAgendaSala(@PathVariable String nombreSala, Model model, RedirectAttributes redirectAttributes) {
        if (agendaViewModelsPorSala == null || agendaViewModelsPorSala.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "No se pudo cargar la agenda. Por favor, suba los archivos de configuración y peticiones de nuevo.");
            return "redirect:/upload";
        }

        AgendaViewModel currentViewModel = agendaViewModelsPorSala.get(nombreSala);
        if (currentViewModel == null) {
            redirectAttributes.addFlashAttribute("error", "La sala '" + nombreSala + "' no existe o no tiene agenda.");
            // Redirigir a una sala válida o a la página de selección de salas
            return "redirect:/agenda/" + agendaViewModelsPorSala.keySet().iterator().next();
        }

        model.addAttribute("agendaViewModel", currentViewModel);
        model.addAttribute("todasLasSalas", agendaViewModelsPorSala.keySet()); // Pasar todas las salas para la navegación
        model.addAttribute("globalIncidencias", globalIncidencias); // Pasar las incidencias globales

        return "agenda"; // Usaremos una única plantilla "agenda.html" para todas las salas
    }

    // --- Método auxiliar para construir ViewModels para cada sala ---
    private Map<String, AgendaViewModel> construirAgendasPorSala(
            List<Reserva> reservasValidas,
            YearMonth mesProcesar,
            Map<String, String> traducciones) {

        Map<String, AgendaViewModel> agendasPorSala = new TreeMap<>(); // TreeMap para mantener el orden

        // Primero, identifica todas las salas únicas
        Set<String> nombresSalas = reservasValidas.stream()
                .map(Reserva::getSala)
                .collect(Collectors.toSet());

        // Crea un ViewModel para cada sala
        for (String sala : nombresSalas) {
            agendasPorSala.put(sala, new AgendaViewModel(sala, mesProcesar, traducciones));
        }

        // Ahora, distribuye las reservas válidas en sus respectivos ViewModels
        for (Reserva reserva : reservasValidas) {
            AgendaViewModel viewModelSala = agendasPorSala.get(reserva.getSala());
            if (viewModelSala != null) {
                // Iterar por cada día dentro del rango de la reserva
                for (LocalDate fecha = reserva.getFechaInicio(); !fecha.isAfter(reserva.getFechaFin()); fecha = fecha.plusDays(1)) {
                    // Verificar si el día de la semana actual está incluido en la reserva
                    if (isDayIncluded(fecha.getDayOfWeek(), reserva.getDiasSemana())) {
                        String[] horarios = reserva.getHorarios().split("_");
                        for (String horarioStr : horarios) {
                            try {
                                String[] partesHorario = horarioStr.split("-");
                                int inicioHora = Integer.parseInt(partesHorario[0]);
                                int finHora = Integer.parseInt(partesHorario[1]);

                                for (int h = inicioHora; h < finHora; h++) {
                                    String slot = String.format("%02d:00-%02d:00", h, (h + 1) == 24 ? 0 : h + 1);
                                    viewModelSala.addReserva(fecha, slot, reserva.getNombreActividad());
                                }
                            } catch (NumberFormatException e) {
                                // Este error debería ser capturado por DataLoader, pero como respaldo
                                logger.warn("Formato de horario numérico inválido en reserva: {} - {}", horarioStr, e.getMessage());

                            }
                        }
                    }
                }
                fechaActual = fechaActual.plusDays(1);
            }
        }

        return agendasPorSala;

    }
}