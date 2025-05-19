package net.elpuig.Agenda.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle; // Para obtener nombres de meses/días
import java.time.temporal.WeekFields; // Para obtener el número de semana
import java.util.*;
import java.util.stream.Collectors;

public class AgendaViewModel {
    // Estructura para almacenar las actividades por sala, fecha y horario (franja de 1 hora)
    private Map<String, Map<LocalDate, Map<String, String>>> agendaPorSala;
    // Lista para almacenar todos los mensajes de incidencia (carga y procesamiento)
    private List<String> incidencias;
    // Mes y año que se está mostrando
    private YearMonth mesProcesar;
    // Mapa con las traducciones cargadas
    private Map<String, String> traducciones;

    // El locale puede ser útil para formatear fechas y obtener números de semana
    private Locale locale;

    // ** Modificamos el constructor para recibir la lista combinada de incidencias **
    // Recibe el mes, las traducciones y la lista de incidencias (ya combinada por AgendaProcessor)
    public AgendaViewModel(YearMonth mesProcesar, Map<String, String> traducciones, List<String> incidencias) {
        this.mesProcesar = mesProcesar;
        this.traducciones = traducciones != null ? traducciones : Collections.emptyMap(); // Evitar null
        // Inicializamos la lista de incidencias con la lista recibida
        this.incidencias = incidencias != null ? incidencias : new ArrayList<>();

        this.agendaPorSala = new HashMap<>();
        // Intentamos determinar el locale basado en las traducciones cargadas, si existe una clave 'locale'
        this.locale = determinarLocale(this.traducciones); // Método auxiliar para determinar Locale
    }

    // Método auxiliar para intentar determinar el Locale basado en las traducciones
    private Locale determinarLocale(Map<String, String> traducciones) {
        String localeCode = traducciones.get("locale"); // Busca una clave 'locale' en el mapa
        if (localeCode != null && !localeCode.trim().isEmpty()) {
            try {
                return Locale.forLanguageTag(localeCode.trim());
            } catch (Exception e) {
                // Si el código de locale no es válido, loggear y usar el por defecto
                System.err.println("Código de locale inválido en traducciones: " + localeCode);
                return Locale.getDefault();
            }
        }
        return Locale.getDefault(); // Si no hay clave 'locale', usar el locale por defecto del sistema
    }


    // Método para añadir una reserva (una franja de 1 hora) al mapa agendaPorSala
    // Este método es llamado desde el Controller después de expandir las franjas grandes
    public void addReserva(String sala, LocalDate fecha, String horaFranjaUnaHora, String actividad) {
        // Navega el mapa anidado, creando sub-mapas si no existen
        agendaPorSala.computeIfAbsent(sala, k -> new HashMap<>())
                .computeIfAbsent(fecha, k -> new HashMap<>())
                .put(horaFranjaUnaHora, actividad); // horaFranjaUnaHora debería ser algo como "HH:00-HH:00"
    }

    // El método addIncidencia ya no es necesario si la lista de incidencias se pasa en el constructor
    // public void addIncidencia(String incidencia) {
    //     incidencias.add(incidencia);
    // }


    // Getters para la plantilla Thymeleaf

    // Devuelve el nombre del mes traducido
    public String getMesNombre() {
        // Intentar obtener del mapa de traducciones primero
        String claveTraduccion = "month." + mesProcesar.getMonthValue(); // Ej: "month.5"
        String nombreTraducido = traducciones.get(claveTraduccion);
        if (nombreTraducido != null) {
            return nombreTraducido;
        }
        // Si no se encuentra en traducciones, usar el nombre del mes del Locale
        return mesProcesar.getMonth().getDisplayName(TextStyle.FULL, this.locale);
    }

    // Devuelve el año
    public int getAnyo() {
        return mesProcesar.getYear();
    }
    public YearMonth getMesProcesar() {
        return mesProcesar;
    }

    // Devuelve una lista de semanas, donde cada semana es una lista de 7 días (LocalDate)
    // Incluye días de meses adyacentes para completar las semanas inicial y final
    public List<List<LocalDate>> getSemanas() {
        // Obtener el primer y último día del mes a procesar
        LocalDate primerDiaMes = mesProcesar.atDay(1);
        LocalDate ultimoDiaMes = mesProcesar.atEndOfMonth();

        // Determinar el primer día de la primera semana a mostrar (puede ser del mes anterior)
        // Si el mes empieza en MONDAY (valor 1), no añadimos días. Si empieza en TUESDAY (valor 2), añadimos 1 día, etc.
        // WeekFields.of(locale).dayOfWeek() da el campo DayOfWeek según el locale (ej. Lunes=1 en muchos locales)
        int ajusteInicioSemana = primerDiaMes.getDayOfWeek().getValue() - WeekFields.of(this.locale).getFirstDayOfWeek().getValue();
        if (ajusteInicioSemana < 0) ajusteInicioSemana += 7; // Ajuste si el primer día de la semana no es Lunes según el locale

        LocalDate inicioPrimeraSemana = primerDiaMes.minusDays(ajusteInicioSemana);

        // Determinar el último día de la última semana a mostrar (puede ser del mes siguiente)
        LocalDate finUltimaSemana = inicioPrimeraSemana.plusDays(41); // Mostrar 6 semanas completas (6 * 7 = 42 días) desde el inicio de la primera semana
        // Esto cubre cualquier mes, ya que un mes puede extenderse a lo largo de 6 semanas

        // Generar todos los días desde el inicio de la primera semana hasta el fin de la última
        List<LocalDate> todosLosDias = inicioPrimeraSemana.datesUntil(finUltimaSemana.plusDays(1)).collect(Collectors.toList()); // plusDays(1) para incluir el fin

        // Agrupar los días en listas de 7 (representando semanas)
        List<List<LocalDate>> semanas = new ArrayList<>();
        for (int i = 0; i < todosLosDias.size(); i += 7) {
            semanas.add(new ArrayList<>(todosLosDias.subList(i, Math.min(i + 7, todosLosDias.size()))));
        }

        return semanas;
    }


    // Devuelve una lista con las iniciales de los días de la semana para los encabezados (en el orden del locale)
    // Ya no necesitamos este método si getDiaInicialTraducido se usa para cada día individualmente
    /*
    public List<String> getDiasSemana() {
         // Obtener las iniciales de los días de la semana según el locale
         List<String> iniciales = new ArrayList<>();
         LocalDate dia = LocalDate.of(mesProcesar.getYear(), mesProcesar.getMonth(), 1); // Cualquier fecha para empezar
         DayOfWeek primerDiaLocale = WeekFields.of(this.locale).getFirstDayOfWeek();
         dia = dia.with(primerDiaLocale); // Ir al primer día de la semana según el locale

         for (int i = 0; i < 7; i++) {
             iniciales.add(getDiaInicialTraducido(dia));
             dia = dia.plusDays(1);
         }
         return iniciales;
    }
    */


    // ** Nuevo método para obtener la inicial traducida del día de la semana **
    // Usa el mapa de traducciones o un fallback
    public String getDiaInicialTraducido(LocalDate dia) {
        if (dia == null) return "";
        // Obtener el nombre del día de la semana según el Locale del ViewModel
        String nombreDia = dia.getDayOfWeek().getDisplayName(TextStyle.FULL, this.locale); // Ej: "lunes", "martes"

        // Intentar obtener del mapa de traducciones primero (asume claves como "day.lunes", "day.martes")
        String claveTraduccion = "day." + nombreDia.toLowerCase(this.locale);
        String inicialTraducida = traducciones.get(claveTraduccion);
        if (inicialTraducida != null && !inicialTraducida.trim().isEmpty()) {
            // Si el valor es más de una letra, tomar solo la primera (si es una inicial)
            // O asumir que la traducción es *solo* la inicial (más simple)
            // Vamos a asumir que la traducción ya es la inicial correcta
            return inicialTraducida.trim();
        }

        // Si no se encuentra en traducciones, usar la inicial corta del nombre del día en el Locale
        return dia.getDayOfWeek().getDisplayName(TextStyle.SHORT, this.locale); // Ej: "lun.", "mar."
        // Podrías querer truncar a una sola letra si TextStyle.SHORT da más de una.
        // String shortName = dia.getDayOfWeek().getDisplayName(TextStyle.SHORT, this.locale);
        // return shortName.substring(0, 1).toUpperCase(this.locale); // Tomar la primera letra en mayúsculas

    }


    // ** Nuevo método para obtener la actividad en una celda específica **
    // Devuelve el nombre de la actividad o null/cadena vacía si está libre
    public String getActividadEnCelda(String sala, LocalDate fecha, String horarioFranjaUnaHora) {
        // Navega la estructura de mapas anidados de forma segura
        Map<LocalDate, Map<String, String>> agendaFecha = agendaPorSala.get(sala);
        if (agendaFecha == null) {
            return null; // Sala no encontrada en los datos procesados
        }
        Map<String, String> agendaHorario = agendaFecha.get(fecha);
        if (agendaHorario == null) {
            return null; // Fecha no encontrada para esta sala
        }
        // Devuelve la actividad para la franja de 1 hora o null si no está reservada
        return agendaHorario.get(horarioFranjaUnaHora);
    }


    // ** Getter para obtener la lista de incidencias (combinada) **
    // La lista de incidencias se inicializó en el constructor con los errores de carga y procesamiento
    public List<String> getIncidencias() {
        return incidencias;
    }

    // Getter para obtener el mapa de agenda procesado (útil si necesitas acceder a la estructura cruda)
    public Map<String, Map<LocalDate, Map<String, String>>> getAgendaPorSala() {
        return agendaPorSala;
    }


    // ** Getter para obtener las franjas horarias únicas (ordenadas) **
    public List<String> getHorariosUnicos() {
        Set<String> horariosSet = new HashSet<>();
        // Recorrer la estructura de agendaPorSala para recoger todas las claves de horario
        if (agendaPorSala != null) {
            agendaPorSala.values().forEach(fechas -> { // Bucle por salas
                if (fechas != null) {
                    fechas.values().forEach(horariosMap -> { // Bucle por fechas
                        if (horariosMap != null) {
                            horariosSet.addAll(horariosMap.keySet()); // Añadir todas las claves de horario (franjas 1 hora)
                        }
                    });
                }
            });
        }

        // Convertir el Set a una Lista y ordenar
        List<String> horariosList = new ArrayList<>(horariosSet);

        // Ordenar las franjas horarias ("HH:00-HH:00") basándose en la hora de inicio
        horariosList.sort(Comparator.comparingInt(h -> {
            try {
                // Extraer solo la parte numérica de la hora de inicio (antes de los ':')
                String horaInicioStr = h.split(":")[0];
                return Integer.parseInt(horaInicioStr);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                // Loggear advertencia para franjas con formato inesperado
                System.err.println("Error parsing horario string for sorting: " + h);
                return 0; // Devolver 0 o algún valor por defecto para no fallar la ordenación
            }
        }));

        return horariosList; // Devolver la lista ordenada
    }

}