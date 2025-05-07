package net.elpuig.Agenda.controller;

import net.elpuig.Agenda.service.DataLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
public class AgendaController {

    @Autowired
    private DataLoader dataLoader;

    @GetMapping("/upload")
    public String mostrarFormulario() {
        return "upload"; // Renderiza upload.html
    }

    @PostMapping("/procesar")
    public String procesarArchivos(
            @RequestParam("configFile") MultipartFile configFile,
            @RequestParam("peticionesFile") MultipartFile peticionesFile,
            Model model) {

        try {
            // Validar y procesar archivos (lógica en DataLoader)
            //DataLoader dataLoader = new DataLoader();
            dataLoader.validarConfig(configFile.getInputStream());
            dataLoader.validarPeticiones(peticionesFile.getInputStream());

            // Redirigir a la vista de agenda (se implementará en feature/outputs)
            return "redirect:/agenda";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "upload"; // Mostrar error en el formulario
        }
    }

    @GetMapping("/agenda")
    public String mostrarAgenda(Model model) {
        // Obtener datos procesados (ejemplo simplificado)
        Map<String, List<Reserva>> agendaPorSala = agendaProcessor.getAgenda();
        List<String> incidencias = agendaProcessor.getIncidencias();

        model.addAttribute("agenda", agendaPorSala);
        model.addAttribute("incidencias", incidencias);
        return "agenda";
    }
}