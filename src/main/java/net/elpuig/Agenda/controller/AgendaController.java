package net.elpuig.Agenda.controller;

import net.elpuig.Agenda.model.AgendaViewModel;
import net.elpuig.Agenda.model.Reserva;
import net.elpuig.Agenda.service.AgendaProcessor;
import net.elpuig.Agenda.service.DataLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
public class AgendaController {

    @Autowired
    private AgendaProcessor agendaProcessor; // Inyectado por Spring (debe ser @Service)

    @Autowired
    private DataLoader dataLoader; // Inyectado por Spring (debe ser @Service)

    // --- Métodos del controlador ---
    @GetMapping("/upload")
    public String mostrarFormulario() {
        return "upload"; // Invocado al acceder a /upload (GET)
    }

    @PostMapping("/procesar")
    public String procesarArchivos(
            @RequestParam("configFile") MultipartFile configFile,
            @RequestParam("peticionesFile") MultipartFile peticionesFile,
            RedirectAttributes redirectAttributes) { // Invocado al enviar el formulario (POST)

        try {
            dataLoader.validarConfig(configFile.getInputStream());
            dataLoader.validarPeticiones(peticionesFile.getInputStream());
            agendaProcessor.procesarReservas(dataLoader.getReservas());

            // Construir y poblar AgendaViewModel
            AgendaViewModel agendaViewModel = construirAgendaViewModel();
            redirectAttributes.addFlashAttribute("agendaViewModel", agendaViewModel);
            return "redirect:/agenda";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
            return "redirect:/upload";
        }
    }

    @GetMapping("/agenda")
    public String mostrarAgenda(@ModelAttribute("agendaViewModel") AgendaViewModel agendaViewModel, Model model) {
        model.addAttribute("agendaViewModel", agendaViewModel);
        return "agenda"; // Invocado al redirigir a /agenda (GET)
    }

    // --- Método auxiliar ---
    private AgendaViewModel construirAgendaViewModel() {
        AgendaViewModel viewModel = new AgendaViewModel(
                dataLoader.getMesProcesar(),
                dataLoader.getTraducciones()
        );

        for (Reserva reserva : agendaProcessor.getReservasValidas()) {
            LocalDate fechaActual = reserva.getFechaInicio();
            while (!fechaActual.isAfter(reserva.getFechaFin())) {
                String[] horas = reserva.getHorarios().split("_");
                for (String horario : horas) {
                    String[] partes = horario.split("-");
                    String horaInicio = partes[0] + ":00";
                    String horaFin = partes[1] + ":00";
                    viewModel.addReserva(
                            reserva.getSala(),
                            fechaActual,
                            horaInicio + "-" + horaFin,
                            reserva.getNombreActividad()
                    );
                }
                fechaActual = fechaActual.plusDays(1);
            }
        }

        agendaProcessor.getIncidencias().forEach(viewModel::addIncidencia);
        return viewModel;
    }
}