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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
// import java.util.Map; // Importación eliminada, ya no es necesaria

@Controller
public class AgendaController {

    private static final Logger logger = LoggerFactory.getLogger(AgendaController.class);

    @Autowired
    private AgendaProcessor agendaProcessor;

    @Autowired
    private DataLoader dataLoader;

    private AgendaViewModel agendaViewModel; // Almacena el ViewModel para solicitudes GET posteriores

    @GetMapping("/") // Redirige la raíz al formulario de subida
    public String redirectToUpload() {
        return "redirect:/upload";
    }

    @GetMapping("/upload")
    public String mostrarFormulario(Model model) {
        // No re-inicializar DataLoader y AgendaProcessor aquí. Spring los gestiona.
        // agendaViewModel = null; // Se reseteará en procesarArchivos si hay un nuevo procesamiento

        // Pasa los mensajes de error de la redirección
        model.addAttribute("error", model.asMap().get("error"));
        return "upload";
    }

    @PostMapping("/procesar")
    public String procesarArchivos(
            @RequestParam("configFile") MultipartFile configFile,
            @RequestParam("peticionesFile") MultipartFile peticionesFile,
            RedirectAttributes redirectAttributes) {
        logger.info("Iniciando procesamiento de archivos."); // Uso del logger

        try {
            // 1. Cargar configuración
            dataLoader.loadConfig(configFile);

            // 2. Cargar peticiones. `loadPeticiones` ahora devuelve la lista de reservas
            // y las incidencias de parseo se añaden directamente al DataLoader.
            List<String> parsingIncidencias = new ArrayList<>();
            List<Reserva> reservasInput = dataLoader.loadPeticiones(peticionesFile, parsingIncidencias);

            // Añadir las incidencias de parseo a la lista combinada
            List<String> allIncidencias = new ArrayList<>(parsingIncidencias);


            // 3. Procesar las reservas cargadas por DataLoader
            agendaProcessor.procesarReservas(reservasInput, dataLoader.getTraducciones()); // Aquí pasas las traducciones

            // Combina las incidencias de parseo y las de procesamiento
            allIncidencias.addAll(agendaProcessor.getIncidencias());

            redirectAttributes.addFlashAttribute("incidencias", allIncidencias); // Incidencias combinadas

            // 4. Construir el ViewModel después de procesar todo
            this.agendaViewModel = construirAgendaViewModel();

            // Redirige al endpoint que muestra la agenda
            return "redirect:/agenda";

        } catch (Exception e) {
            logger.error("Error al procesar archivos: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error al procesar archivos: " + e.getMessage());
            return "redirect:/upload";
        }
    }

    @GetMapping("/agenda")
    public String mostrarAgenda(Model model) {
        if (agendaViewModel == null) {
            // Si el ViewModel es nulo, significa que no se procesaron archivos o hubo un error en el procesamiento.
            // Redirige de nuevo a la página de subida con un mensaje de error.
            model.addAttribute("error", "No se pudo cargar la agenda. Por favor, suba los archivos de configuración y peticiones de nuevo.");
            return "upload";
        }
        model.addAttribute("agendaViewModel", agendaViewModel);
        // Las incidencias se añadirán automáticamente al modelo si vienen de un RedirectAttributes
        return "agenda";
    }

    // --- Método auxiliar para construir el ViewModel ---
    private AgendaViewModel construirAgendaViewModel() {
        YearMonth mesProcesar = dataLoader.getMesProcesar();
        if (mesProcesar == null) {
            // Esto no debería ocurrir si loadConfig se ejecutó correctamente
            throw new IllegalStateException("mesProcesar no se ha inicializado. ¿Se cargó config.txt?");
        }
        AgendaViewModel viewModel = new AgendaViewModel(mesProcesar, dataLoader.getTraducciones());

        // Para cada reserva válida, marcar los slots en el ViewModel
        for (Reserva reserva : agendaProcessor.getReservasValidas()) {
            // Iterar por cada día dentro del rango de la reserva
            for (LocalDate fecha = reserva.getFechaInicio(); !fecha.isAfter(reserva.getFechaFin()); fecha = fecha.plusDays(1)) {
                // Verificar si el día de la semana actual está incluido en la reserva
                if (isDayIncluded(fecha.getDayOfWeek(), reserva.getDiasSemana())) { // Llamada al método corregido
                    String[] horarios = reserva.getHorarios().split("_");
                    for (String horarioStr : horarios) {
                        try {
                            String[] partesHorario = horarioStr.split("-");
                            int inicioHora = Integer.parseInt(partesHorario[0]);
                            int finHora = Integer.parseInt(partesHorario[1]);

                            for (int h = inicioHora; h < finHora; h++) {
                                String slot = String.format("%02d:00-%02d:00", h, h + 1);
                                viewModel.addReserva(reserva.getSala(), fecha, slot, reserva.getNombreActividad());
                            }
                        } catch (NumberFormatException e) {
                            // Este error debería ser capturado por DataLoader, pero como respaldo
                            logger.warn("Formato de horario numérico inválido en reserva: {} - {}", horarioStr, e.getMessage());
                        }
                    }
                }
            }
        }
        // Añadir incidencias del procesador al ViewModel
        agendaProcessor.getIncidencias().forEach(viewModel::addIncidencia);

        return viewModel;
    }

    // Helper method: This should ideally be in AgendaProcessor or a utility class.
    // Renombrado de isDayIncludedInReserva a isDayIncluded para consistencia.
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