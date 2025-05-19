package net.elpuig.Agenda.controller;

// Importar las clases necesarias
import net.elpuig.Agenda.model.AgendaViewModel;
import net.elpuig.Agenda.model.Reserva;
import net.elpuig.Agenda.service.DataLoaderResult; // Importar DataLoaderResult
import net.elpuig.Agenda.service.AgendaProcessor;
import net.elpuig.Agenda.service.DataLoader;

import org.slf4j.Logger; // Importar Logger
import org.slf4j.LoggerFactory; // Importar LoggerFactory

import org.springframework.beans.factory.annotation.Autowired; // Para inyección de dependencias
import org.springframework.stereotype.Controller; // Indica que es un controlador Spring MVC
import org.springframework.ui.Model; // Para pasar datos a la vista
import org.springframework.web.bind.annotation.GetMapping; // Mapea peticiones GET
import org.springframework.web.bind.annotation.PostMapping; // Mapea peticiones POST
import org.springframework.web.bind.annotation.ModelAttribute; // Para acceder a atributos del modelo
import org.springframework.web.bind.annotation.RequestParam; // Para obtener parámetros de la petición
import org.springframework.web.multipart.MultipartFile; // Para manejar archivos subidos
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // Para pasar atributos después de un redirect

import java.time.LocalDate;
import java.util.ArrayList; // Importar ArrayList
import java.util.List; // Importar List

// Anotación que marca esta clase como un controlador Spring MVC
@Controller
public class AgendaController {
    // Logger para registrar mensajes
    private static final Logger logger = LoggerFactory.getLogger(AgendaController.class);

    // Inyección de dependencias para los servicios
    @Autowired
    private AgendaProcessor agendaProcessor;
    @Autowired
    private DataLoader dataLoader;

    // --- Métodos del controlador para manejar peticiones web ---

    // Mapea las peticiones GET a la URL "/upload"
    @GetMapping("/upload")
    public String mostrarFormulario(Model model) {
        // Puedes añadir un objeto vacío al modelo si la vista upload.html espera alguno
        // model.addAttribute("uploadForm", new Object()); // Ejemplo
        logger.info("Acceso a la página de carga de archivos (/upload)");
        return "upload"; // Devuelve el nombre de la plantilla (upload.html)
    }

    // Mapea las peticiones POST a la URL "/procesar"
    // Se invoca al enviar el formulario de carga de archivos
    @PostMapping("/procesar")
    public String procesarArchivos(
            @RequestParam("configFile") MultipartFile configFile, // Archivo de configuración subido
            @RequestParam("peticionesFile") MultipartFile peticionesFile, // Archivo de peticiones subido
            RedirectAttributes redirectAttributes) { // Objeto para pasar atributos después de la redirección

        logger.info("Petición para procesar archivos recibida.");

        // Usaremos un bloque try-catch para manejar cualquier excepción durante el proceso
        try {
            // 1. Validar y cargar el archivo de configuración usando DataLoader
            // DataLoader cargará mesProcesar y traducciones. Puede lanzar Exception.
            logger.info("Validando archivo de configuración...");
            dataLoader.validarConfig(configFile.getInputStream());
            logger.info("Archivo de configuración validado exitosamente.");

            // 2. Validar y cargar el archivo de peticiones usando DataLoader
            // DataLoader validará líneas individualmente y devolverá reservas válidas y errores de carga.
            logger.info("Validando archivo de peticiones...");
            DataLoaderResult peticionesResult = dataLoader.validarPeticiones(peticionesFile.getInputStream());
            List<Reserva> loadedReservas = peticionesResult.getReservas(); // Reservas cargadas sin errores de formato/parseo
            List<String> loadingParsingErrors = peticionesResult.getErrores(); // Errores encontrados durante la carga/parseo
            logger.info("Archivo de peticiones procesado. {} reservas cargadas válidas, {} errores de carga.", loadedReservas.size(), loadingParsingErrors.size());


            // 3. Procesar las reservas usando AgendaProcessor
            // AgendaProcessor manejará la lógica de conflictos y añadirá errores de procesamiento.
            // Le pasamos las reservas que DataLoader pudo cargar y los errores que encontró DataLoader.
            logger.info("Procesando reservas (detección de conflictos, etc.)...");
            agendaProcessor.procesarReservas(loadedReservas, loadingParsingErrors);
            logger.info("Procesamiento de reservas completado.");


            // 4. Construir el ViewModel que se usará en la vista agenda.html
            // El ViewModel obtiene datos necesarios de DataLoader (mes, traducciones)
            // y de AgendaProcessor (reservas válidas finales, lista combinada de incidencias).
            logger.info("Construyendo AgendaViewModel...");
            AgendaViewModel agendaViewModel = construirAgendaViewModel();
            logger.info("AgendaViewModel construido.");


            // 5. Redirigir a la página de la agenda y pasar el ViewModel usando flash attributes
            // Los flash attributes se guardan en la sesión por una petición y se eliminan después de la siguiente.
            redirectAttributes.addFlashAttribute("agendaViewModel", agendaViewModel);

            logger.info("Redirigiendo a /agenda...");
            return "redirect:/agenda"; // Redirige a la URL /agenda (petición GET)

        } catch (Exception e) {
            // Si ocurre cualquier excepción durante la carga, validación inicial de config o peticiones (antes del procesamiento línea a línea),
            // o si DataLoader/AgendaProcessor lanza alguna excepción no controlada internamente.
            logger.error("Error general durante el procesamiento de archivos: {}", e.getMessage(), e);

            // Añadir el mensaje de error a los flash attributes para mostrarlo en la página /upload
            // La página upload.html tiene un bloque th:if="${error}" para mostrar esto.
            redirectAttributes.addFlashAttribute("error", "Error al procesar archivos: " + e.getMessage());

            // Redirigir de vuelta a la página de carga
            return "redirect:/upload";
        }
    }

    // Mapea las peticiones GET a la URL "/agenda"
    // Se invoca después de la redirección exitosa desde /procesar
    @GetMapping("/agenda")
    public String mostrarAgenda(@ModelAttribute("agendaViewModel") AgendaViewModel agendaViewModel, Model model) {
        // @ModelAttribute intenta obtener el agendaViewModel que fue pasado como flash attribute.
        // Si no existe (ej. acceso directo a /agenda), Spring podría intentar crearlo (si tiene constructor por defecto)
        // o sería null, causando un error al intentar usarlo.
        // Podríamos añadir manejo para el caso null si se permite acceso directo.
        if (agendaViewModel == null) {
            logger.warn("Acceso directo a /agenda sin AgendaViewModel en flash attributes.");
            // Podríamos redirigir a /upload o cargar datos por defecto, o mostrar un error.
            // Por ahora, asumimos que siempre viene del redirect de /procesar.
            // Si fuera null, intentar añadirlo al modelo causaría un error más adelante.
        }

        // Añadir el ViewModel al objeto Model para que sea accesible en la plantilla Thymeleaf
        model.addAttribute("agendaViewModel", agendaViewModel);

        logger.info("Acceso a la página de la agenda (/agenda). Mostrando resultados.");
        return "agenda"; // Devuelve el nombre de la plantilla (agenda.html)
    }

    // --- Método auxiliar para construir el AgendaViewModel ---
    // Este método prepara los datos en el formato que la vista espera.
    private AgendaViewModel construirAgendaViewModel() {
        // Obtener el mes a procesar y las traducciones de DataLoader (ya cargados por validarConfig)
        // Obtener la lista COMBINADA de incidencias (carga + procesamiento) de AgendaProcessor
        AgendaViewModel viewModel = new AgendaViewModel(
                dataLoader.getMesProcesar(), // Mes y año para el título y estructura
                dataLoader.getTraducciones(), // Mapa de traducciones para meses, días, etc.
                agendaProcessor.getIncidencias() // ** Lista combinada de errores **
        );

        // Obtener la lista final de reservas válidas del AgendaProcessor
        List<Reserva> reservasValidas = agendaProcessor.getReservasValidas();

        // Iterar sobre las reservas válidas y expandir las franjas horarias en franjas de 1 hora
        // y añadirlas al mapa interno del ViewModel (agendaPorSala)
        if (reservasValidas != null) {
            logger.info("Expandiendo {} reservas válidas a franjas de 1 hora para el ViewModel...", reservasValidas.size());
            int franjasAñadidas = 0;
            for (Reserva reserva : reservasValidas) {
                LocalDate fechaActual = reserva.getFechaInicio();
                // Iterar desde la fecha de inicio hasta la fecha de fin de la reserva
                while (!fechaActual.isAfter(reserva.getFechaFin())) {
                    // Para cada día en el rango, verificar si el día de la semana está en la máscara de días
                    // (Nota: La lógica de validar la máscara de días "LMCJVSG" y aplicarla a las fechas
                    // podría estar aquí o en AgendaProcessor al validar o expandir.
                    // Actualmente, la validación de formato de máscara de días está en DataLoader/Processor.
                    // Para la expansión aquí, simplemente iteramos por fecha y usamos la máscara.)

                    // Lógica simple: asume que si la reserva llegó aquí, la máscara de días ya se manejó
                    // o que queremos añadir la reserva a todos los días en el rango de fechas.
                    // Si la máscara de días debe filtrar los días aquí, necesitas implementar esa lógica.
                    // EJEMPLO BÁSICO SIN FILTRO POR MÁSCARA DE DÍAS:
                    // (Si necesitas filtrar por máscara, la lógica sería: if(diaCorrespondeAMascara(fechaActual, reserva.getDiasSemana()))) { ... }

                    // Dividir la máscara de horarios por '_' para obtener franjas individuales (ej. "0800-1000")
                    String[] franjasHorariasGrandes = reserva.getHorarios().split("_");
                    for (String franjaGrande : franjasHorariasGrandes) {
                        String[] partesFranja = franjaGrande.split("-");
                        if(partesFranja.length == 2) { // Seguridad: verificar formato HH-HH
                            try {
                                int inicioHora = Integer.parseInt(partesFranja[0]);
                                int finHora = Integer.parseInt(partesFranja[1]);

                                // Expandir la franja horaria grande a franjas de 1 hora (ej. 0800-1000 -> 08:00-09:00, 09:00-10:00)
                                // Iterar desde la hora de inicio hasta (pero sin incluir) la hora de fin
                                for (int h = inicioHora; h < finHora; h++) {
                                    // Formatear la franja de 1 hora a "HH:00-HH:00" (ej. 8 -> "08:00-09:00")
                                    String franjaUnaHora = String.format("%02d:00-%02d:00", h, h + 1);

                                    // Añadir la franja de 1 hora al ViewModel usando el método addReserva
                                    viewModel.addReserva(
                                            reserva.getSala(), // Nombre de la sala
                                            fechaActual, // Fecha actual (día en el bucle)
                                            franjaUnaHora, // La franja de 1 hora (ej. "08:00-09:00")
                                            reserva.getNombreActividad() // Nombre de la actividad
                                    );
                                    franjasAñadidas++;
                                }
                            } catch (NumberFormatException e) {
                                // Esto no debería pasar si DataLoader/AgendaProcessor validaron bien,
                                // pero por seguridad, loggeamos una advertencia.
                                logger.warn("Error al parsear rango horario '{}' al construir ViewModel para reserva {}: {}", franjaGrande, reserva.getNombreActividad(), e.getMessage());
                            }
                        } else {
                            logger.warn("Formato de franja horaria inesperado '{}' al construir ViewModel para reserva {}", franjaGrande, reserva.getNombreActividad());
                        }
                    }
                    // Pasar al siguiente día
                    fechaActual = fechaActual.plusDays(1);
                }
            }
            logger.info("Expandidas a {} franjas de 1 hora y añadidas al ViewModel.", franjasAñadidas);
        } else {
            logger.info("No hay reservas válidas para expandir al ViewModel.");
        }


        // La lista de incidencias ya se pasó en el constructor del ViewModel.
        // El ViewModel ahora tiene la lista combinada de errores de carga y procesamiento.

        return viewModel; // Devolver el ViewModel completamente poblado
    }
}