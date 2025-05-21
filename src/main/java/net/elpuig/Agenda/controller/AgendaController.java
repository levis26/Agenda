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

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader; // <<-- ¡NUEVA IMPORTACIÓN!
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Controller
public class AgendaController {

    private static final Logger logger = LoggerFactory.getLogger(AgendaController.class);

    @Autowired
    private AgendaProcessor agendaProcessor;

    @Autowired
    private DataLoader dataLoader;

    private Map<String, Map<String, String>> allTranslations = new HashMap<>();

    @PostConstruct
    public void init() {
        // Cargar todas las traducciones disponibles al iniciar la aplicación
        loadTranslations("ESP", "internacional.ESP.properties");
        loadTranslations("ENG", "internacional.ENG.properties");
        loadTranslations("CAT", "internacional.CAT.properties");
        loadTranslations("ZHO", "internacional.ZHO.properties");
        loadTranslations("JPN", "internacional.JPN.properties");
        logger.info("Traducciones de I18n cargadas en el controlador: {}", allTranslations.keySet());
    }

    private void loadTranslations(String langCode, String filename) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("i18n/" + filename)) {
            if (input == null) {
                logger.warn("No se pudo encontrar el archivo de traducciones: i18n/{}", filename);
                return;
            }
            Properties prop = new Properties();
            prop.load(new InputStreamReader(input, "UTF-8"));
            Map<String, String> langMap = new HashMap<>();
            prop.forEach((key, value) -> langMap.put(key.toString(), value.toString()));
            allTranslations.put(langCode, langMap);
            logger.info("Traducciones para {} cargadas exitosamente.", langCode);
        } catch (IOException e) {
            logger.error("Error al cargar traducciones para {}: {}", langCode, e.getMessage());
        }
    }


    @GetMapping("/upload")
    public String mostrarFormulario() {
        return "upload";
    }

    @PostMapping("/procesar")
    public String procesarArchivos(
            @RequestParam("configFile") MultipartFile configFile,
            @RequestParam("peticionesFile") MultipartFile peticionesFile,
            RedirectAttributes redirectAttributes) {

        if (configFile.isEmpty() || peticionesFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Por favor, selecciona ambos archivos.");
            return "redirect:/upload";
        }

        try (InputStream configInputStream = configFile.getInputStream();
             InputStream peticionesInputStream = peticionesFile.getInputStream()) {

            dataLoader.cargarArchivos(configInputStream, peticionesInputStream, allTranslations);

            // Get the current language's translations
            String currentLang = dataLoader.getIdiomaSalida();
            Map<String, String> currentTranslations = allTranslations.get(currentLang);
            
            // Call procesarReservas with both required parameters
            agendaProcessor.procesarReservas(dataLoader.getReservas(), currentTranslations);

            return "redirect:/agenda";

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

    @GetMapping("/agenda")
    public String mostrarAgenda(Model model) {
        if (dataLoader.getMesProcesar() == null || dataLoader.getIdiomaSalida() == null) {
            model.addAttribute("error", "No se ha cargado la configuración de la agenda. Por favor, sube los archivos.");
            return "upload";
        }

        AgendaViewModel agendaViewModel = construirAgendaViewModel();
        model.addAttribute("agendaViewModel", agendaViewModel);

        if (model.asMap().containsKey("incidenciasProcesamiento")) {
            model.addAttribute("incidenciasProcesamiento", model.asMap().get("incidenciasProcesamiento"));
        }
        if (model.asMap().containsKey("incidenciasCarga")) {
            model.addAttribute("incidenciasCarga", model.asMap().get("incidenciasCarga"));
        }

        return "agenda";
    }

    private AgendaViewModel construirAgendaViewModel() {
        YearMonth mesProcesar = dataLoader.getMesProcesar();
        Map<String, String> traduccionesSalida = dataLoader.getTraducciones();

        if (mesProcesar == null || traduccionesSalida == null || traduccionesSalida.isEmpty()) {
            throw new IllegalStateException("Datos de configuración o traducciones no inicializados. ¿Se cargó config.txt correctamente?");
        }

        AgendaViewModel viewModel = new AgendaViewModel(mesProcesar, traduccionesSalida);

        for (Reserva reserva : agendaProcessor.getReservasValidas()) {
            LocalDate fechaActual = reserva.getFechaInicio();
            while (!fechaActual.isAfter(reserva.getFechaFin())) {
                if (fechaActual.getMonth() == mesProcesar.getMonth() && fechaActual.getYear() == mesProcesar.getYear()) {
                    // Obtener el código de día interno (L, M, C, J, V, S, D) usando el nuevo método estático
                    String diaCodigo = AgendaViewModel.getCodigoDia(fechaActual.getDayOfWeek()); // <<-- ¡USANDO NUEVO MÉTODO!
                    if (reserva.getDiasSemana().contains(diaCodigo)) {
                        String[] horas = reserva.getHorarios().split("_");
                        for (String horario : horas) {
                            String[] partes = horario.split("-");
                            int inicio = Integer.parseInt(partes[0]);
                            int fin = Integer.parseInt(partes[1]);
                            for (int hora = inicio; hora < fin; hora++) {
                                String horaStr = String.format("%02d:00-%02d:00", hora, hora + 1);
                                viewModel.addReserva(reserva.getSala(), fechaActual, horaStr, reserva.getNombreActividad());
                            }
                        }
                    }
                }
                fechaActual = fechaActual.plusDays(1);
            }
        }

        agendaProcessor.getIncidencias().forEach(viewModel::addIncidencia);
        return viewModel;
    }
}