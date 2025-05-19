package net.elpuig.Agenda.service;

import net.elpuig.Agenda.model.Reserva;
// Asegúrate de importar DataLoaderResult
import net.elpuig.Agenda.service.DataLoaderResult; // Ajusta si DataLoaderResult está en otro paquete

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.*;

@Service // Indica que esta clase es un componente de servicio de Spring
public class DataLoader {
    // Logger para registrar mensajes
    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    // Atributos para almacenar la configuración cargada
    private YearMonth mesProcesar;
    private String idiomaEntrada;
    private Map<String, String> traducciones = new HashMap<>();

    // Eliminamos los atributos de instancia 'reservas' e 'incidencias'
    // Ahora se recogen localmente y se devuelven en DataLoaderResult

    // Método para crear Reserva desde una línea (lanza excepción en error de parseo)
    private Reserva crearReservaDesdeLinea(String linea) throws Exception {
        String[] partes = linea.split(" ");
        if (partes.length < 6) {
            // Esto debería ser capturado por validarLineaPeticion antes, pero como seguridad
            throw new Exception("Número incorrecto de partes (" + partes.length + ")");
        }
        // parseFecha sigue lanzando excepciones si el formato o el valor es incorrecto
        return new Reserva(
                partes[0], // nombreActividad
                partes[1], // sala
                parseFecha(partes[2]), // fechaInicio
                parseFecha(partes[3]), // fechaFin
                partes[4], // diasSemana
                partes[5]  // horarios
        );
    }

    // Método para parsear String a LocalDate (lanza excepción en error)
    private LocalDate parseFecha(String fechaStr) throws Exception {
        if (fechaStr == null || fechaStr.isBlank()) {
            throw new Exception("Cadena de fecha vacía o nula");
        }
        String[] partes = fechaStr.split("/");
        if (partes.length != 3) {
            throw new Exception("Formato de fecha inválido, se esperaba dd/MM/yyyy");
        }
        try {
            int dia = Integer.parseInt(partes[0]);
            int mes = Integer.parseInt(partes[1]);
            int año = Integer.parseInt(partes[2]);
            // LocalDate.of valida si la combinación dia/mes/año es válida (ej. no 30 de Febrero)
            return LocalDate.of(año, mes, dia);
        } catch (NumberFormatException e) {
            throw new Exception("Partes de la fecha no numéricas: " + fechaStr);
        } catch (java.time.DateTimeException e) {
            throw new Exception("Fecha inválida: " + fechaStr + " (ej. día fuera de rango para el mes)");
        } catch (Exception e) {
            throw new Exception("Error inesperado al parsear fecha '" + fechaStr + "': " + e.getMessage());
        }
    }

    // Método para validar el archivo de configuración (lanza excepción en error crítico)
    public void validarConfig(InputStream configStream) throws Exception {
        if (configStream == null) {
            throw new Exception("InputStream del archivo de configuración es nulo");
        }
        try (Scanner scanner = new Scanner(configStream)) {
            if (!scanner.hasNextLine()) {
                throw new Exception("Archivo de configuración vacío");
            }
            // Línea 1: Año y Mes
            String lineaFecha = scanner.nextLine().trim();
            if (lineaFecha.isEmpty()) {
                throw new Exception("Primera línea (Año y Mes) en configuración está vacía");
            }
            validarLineaConfig(lineaFecha, true);

            if (!scanner.hasNextLine()) {
                // No lanzamos excepción, asumimos que el idioma es opcional o tiene un valor por defecto
                logger.warn("Segunda línea (Idioma) en configuración está ausente. No se cargarán traducciones específicas.");
                this.idiomaEntrada = null; // O establecer a un valor por defecto como "es"
                this.traducciones.clear(); // Asegurar que no queden traducciones de cargas anteriores
                return; // Salir después de validar la fecha si no hay más líneas
            }

            // Línea 2: Idiomas (Descomentar si quieres cargar traducciones)
            String lineaIdiomas = scanner.nextLine().trim();
            if (lineaIdiomas.isEmpty()) {
                logger.warn("Segunda línea (Idioma) en configuración está vacía. No se cargarán traducciones específicas.");
                this.idiomaEntrada = null;
                this.traducciones.clear();
            } else {
                validarLineaConfig(lineaIdiomas, false);
                cargarTraducciones(idiomaEntrada); // Cargar traducciones si el idioma se leyó
            }

        } catch (Exception e) {
            // Capturar cualquier excepción durante la lectura o validación de config
            logger.error("Error al leer o validar archivo de configuración: {}", e.getMessage(), e);
            // Limpiar estado en caso de error
            this.mesProcesar = null;
            this.idiomaEntrada = null;
            this.traducciones.clear();
            throw e; // Relanzar para que el controlador lo maneje
        }
    }

    private void validarLineaConfig(String linea, boolean esFecha) throws Exception {
        String[] partes = linea.split(" ");
        if (esFecha) {
            if (partes.length != 2) throw new Exception("Formato de fecha de configuración inválido: se esperaba 'AAAA MM'");
            try {
                int año = Integer.parseInt(partes[0]);
                int mes = Integer.parseInt(partes[1]);
                if (mes < 1 || mes > 12) throw new Exception("Mes inválido en configuración: " + mes);
                // Validar rango de año básico si es necesario
                if (año < 1900 || año > Year.now().getValue() + 10) throw new Exception("Año fuera de rango en configuración: " + año);

                this.mesProcesar = YearMonth.of(año, mes);
            } catch (NumberFormatException e) {
                throw new Exception("Año o mes no numérico en configuración '" + linea + "': " + e.getMessage());
            } catch (java.time.DateTimeException e) {
                throw new Exception("Combinación Año/Mes inválida en configuración '" + linea + "': " + e.getMessage());
            }
        } else { // Es la línea de idioma
            // El formato esperado es solo el código de idioma, ej. "CAT"
            if (partes.length != 1) throw new Exception("Formato de idioma de configuración inválido: se esperaba 'CODIGO'");
            this.idiomaEntrada = partes[0].trim(); // Asignar el código de idioma
            // No validamos el código de idioma en sí, solo su presencia.
        }
    }

    private void cargarTraducciones(String idioma) throws Exception {
        // Si idioma es null o vacío, ya se manejó en validarConfig
        if (idioma == null || idioma.trim().isEmpty()) {
            this.traducciones.clear(); // Asegurar que esté vacío
            return;
        }

        String idiomaLimpio = idioma.trim();
        String archivo = "internacional." + idiomaLimpio + ".properties";
        logger.info("Intentando cargar archivo de traducción: {}", archivo);

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(archivo)) {
            if (input == null) {
                logger.warn("Archivo de traducción no encontrado: {}", archivo);
                this.traducciones.clear();
                // No lanzar excepción, solo advertir y continuar sin traducciones
                return;
            }
            this.traducciones.clear(); // Limpiar traducciones anteriores
            try (Scanner scanner = new Scanner(input)) {
                while (scanner.hasNextLine()) {
                    String linea = scanner.nextLine().trim();
                    if (linea.isEmpty() || linea.startsWith("#")) continue; // Ignorar vacías o comentarios
                    // Split por el primer ';' para permitir ';' en el valor traducido
                    String[] partes = linea.split(";", 2);
                    if (partes.length == 2) {
                        traducciones.put(partes[0].trim(), partes[1].trim());
                    } else {
                        logger.warn("Línea de traducción con formato inválido en {}: {}", archivo, linea);
                    }
                }
            }
            logger.info("Traducciones cargadas para idioma '{}' desde {}", idiomaLimpio, archivo);
        } catch (Exception e) {
            // Capturar errores durante la lectura del archivo .properties
            logger.error("Error al leer el archivo de traducción {}: {}", archivo, e.getMessage(), e);
            this.traducciones.clear(); // Asegurar mapa vacío si hubo error
            // No relanzar la excepción aquí, la aplicación puede seguir sin traducciones
        }
    }


    // ** Modificamos el método validarPeticiones para devolver DataLoaderResult **
    public DataLoaderResult validarPeticiones(InputStream peticionesStream) throws Exception {
        // Usaremos una lista local para las reservas cargadas exitosamente en este método
        List<Reserva> reservasCargadasValidas = new ArrayList<>();
        // Usaremos una lista local para los errores encontrados durante la carga/validación
        List<String> erroresDeCarga = new ArrayList<>();

        if (peticionesStream == null) {
            erroresDeCarga.add("InputStream del archivo de peticiones es nulo");
            // Devolver resultado con lista de reservas vacía y el error
            return new DataLoaderResult(reservasCargadasValidas, erroresDeCarga);
        }

        try (Scanner scanner = new Scanner(peticionesStream)) {
            int numeroLinea = 0;
            while (scanner.hasNextLine()) {
                numeroLinea++;
                String linea = scanner.nextLine().trim();
                if (linea.isEmpty() || linea.startsWith("#")) { // Ignorar líneas vacías o comentarios
                    continue;
                }

                // Usamos un try-catch por línea para capturar errores al procesar la línea (parseo, formato, etc.)
                try {
                    // Intentamos procesar la línea. Si es válida y se parsea, se añade a reservasCargadasValidas.
                    // Los errores de validación/parseo internos se añadirán a erroresDeCarga.
                    processSinglePeticionLine(linea, reservasCargadasValidas, erroresDeCarga, numeroLinea);
                } catch (Exception e) {
                    // Este catch es para errores inesperados al leer la línea o errores no capturados internamente
                    erroresDeCarga.add("Línea " + numeroLinea + " - Error inesperado al procesar la línea: " + e.getMessage());
                    logger.error("Error inesperado al procesar línea {}: {}", numeroLinea, linea, e);
                }
            }
        } catch (Exception e) {
            // Capturar errores al abrir o leer el stream completo del archivo de peticiones
            erroresDeCarga.add("Error al leer el archivo de peticiones: " + e.getMessage());
            logger.error("Error al leer el archivo de peticiones: {}", e.getMessage(), e);
            // No relanzar, devolver los errores encontrados hasta ahora
        }

        // Devolvemos los resultados
        return new DataLoaderResult(reservasCargadasValidas, erroresDeCarga);
    }

    // ** Método auxiliar para procesar una sola línea de petición **
    // Realiza validaciones y parseo, añade a listas pasadas por referencia
    private void processSinglePeticionLine(String linea, List<Reserva> validReservas, List<String> loadingErrors, int numeroLinea) {
        String[] partes = linea.split(" ");
        if (partes.length < 6) {
            loadingErrors.add("Línea " + numeroLinea + " - Formato de petición inválido: número de partes incorrecto (" + partes.length + "). Esperado mínimo 6.");
            return; // Formato crítico, no podemos seguir validando partes
        }

        // Variables para las fechas parseadas y estado de validación de la línea
        LocalDate fechaInicio = null;
        LocalDate fechaFin = null;
        boolean lineaCompletaValida = true;

        // --- Validación y Parseo de Fechas ---
        // Intentamos parsear las fechas. parseFecha lanza excepción si hay error de formato/valor.
        try {
            fechaInicio = parseFecha(partes[2]);
            // Validar rango de año si el parseo fue exitoso
            if (!validarRangoAñoFecha(fechaInicio, loadingErrors, numeroLinea, "inicio")) {
                lineaCompletaValida = false; // El rango de año no es válido
            }
        } catch (Exception e) {
            // Capturamos errores de parseo o formato de fecha de inicio
            loadingErrors.add("Línea " + numeroLinea + " - Error en fecha de inicio '" + partes[2] + "': " + e.getMessage());
            lineaCompletaValida = false; // Esta línea no es válida
        }

        try {
            fechaFin = parseFecha(partes[3]);
            if (!validarRangoAñoFecha(fechaFin, loadingErrors, numeroLinea, "fin")) {
                lineaCompletaValida = false; // El rango de año no es válido
            }
        } catch (Exception e) {
            loadingErrors.add("Línea " + numeroLinea + " - Error en fecha de fin '" + partes[3] + "': " + e.getMessage());
            lineaCompletaValida = false; // Esta línea no es válida
        }


        // Validar que fechaInicio no sea posterior a fechaFin (solo si ambas fechas se pudieron parsear)
        if (fechaInicio != null && fechaFin != null && lineaCompletaValida) { // Solo validar si no hay errores previos y fechas existen
            if (fechaInicio.isAfter(fechaFin)) {
                loadingErrors.add("Línea " + numeroLinea + " - Fecha inicio posterior a fecha fin");
                lineaCompletaValida = false; // Esta línea no es válida
            }
        } else if ((fechaInicio == null || fechaFin == null) && lineaCompletaValida) {
            // Esto no debería pasar si parseFecha lanza excepción, pero es una seguridad.
            loadingErrors.add("Línea " + numeroLinea + " - Error interno: Fechas nulas después de parseo exitoso aparente");
            lineaCompletaValida = false;
        }


        // --- Validación de Máscara de Días ---
        // La máscara de días se valida con regex y si es nula/vacía
        String diasSemana = partes[4];
        if (diasSemana == null || diasSemana.isBlank()) {
            loadingErrors.add("Línea " + numeroLinea + " - Máscara de días vacía o nula");
            lineaCompletaValida = false;
        } else if (!diasSemana.matches("^[LMCJVSG]+$")) {
            loadingErrors.add("Línea " + numeroLinea + " - Máscara de días inválida: '" + diasSemana + "'. Debe contener solo L, M, C, J, V, S, G.");
            lineaCompletaValida = false;
        }


        // --- Validación de Horarios ---
        // Validar formato de horarios. Los errores se añaden a loadingErrors dentro del método.
        // Solo llamamos si el número de partes era correcto inicialmente
        if (partes.length > 5) { // Asegurar que la parte 5 existe antes de validar horarios
            if (!validarFormatoHorarios(partes[5], loadingErrors, numeroLinea)) {
                lineaCompletaValida = false; // Si el formato de horarios es inválido, la línea no es válida
            }
        } else {
            // Si no hay parte 5, la máscara de horarios falta
            loadingErrors.add("Línea " + numeroLinea + " - Máscara de horarios ausente");
            lineaCompletaValida = false;
        }


        // --- Si la línea es válida, crear y añadir la Reserva ---
        // Solo creamos el objeto Reserva si todas las validaciones pasaron y no se marcó como inválida
        if (lineaCompletaValida) {
            try {
                // Creamos el objeto Reserva usando las partes originales y las fechas parseadas
                Reserva reserva = new Reserva(
                        partes[0], // nombreActividad
                        partes[1], // sala
                        fechaInicio, // ** Usamos la fecha parseada **
                        fechaFin,    // ** Usamos la fecha parseada **
                        partes[4],   // Máscara de días original
                        partes[5]    // Máscara de horarios original
                );
                validReservas.add(reserva); // Añadimos la reserva válida a la lista
            } catch (Exception e) {
                // Esto debería ser muy raro si las partes y fechas ya fueron validadas,
                // pero por seguridad, capturamos errores al construir el objeto final.
                loadingErrors.add("Línea " + numeroLinea + " - Error inesperado al construir objeto Reserva: " + e.getMessage());
                logger.error("Error inesperado al construir objeto Reserva para línea {}: {}", numeroLinea, linea, e);
                // No marcamos lineaCompletaValida como false aquí, ya que el parseo y formato fueron exitosos,
                // el error está en la construcción final del objeto.
            }
        } else {
            // Si lineaCompletaValida es false, los errores ya se añadieron a loadingErrors.
            // Esta línea no se añade a validReservas.
        }
    }

    // ** Método auxiliar para validar el rango de año de una fecha **
    private boolean validarRangoAñoFecha(LocalDate fecha, List<String> errores, int numeroLinea, String tipoFecha) {
        if (fecha == null) {
            // Este caso debería ser capturado por el try-catch al parsear la fecha,
            // pero como seguridad, lo validamos aquí.
            errores.add("Línea " + numeroLinea + " - Error interno: Fecha nula para validación de rango (" + tipoFecha + ")");
            return false;
        }

        int año = fecha.getYear();
        // Definimos un rango razonable de años permitidos (ej. +/- 10 años del actual)
        int añoActual = Year.now().getValue();
        int minAño = añoActual - 25;
        int maxAño = añoActual + 20;

        if (año < minAño || año > maxAño) {
            errores.add("Línea " + numeroLinea + " - Año fuera de rango en fecha " + tipoFecha + ": " + año + ". Debe estar entre " + minAño + " y " + maxAño + ".");
            return false;
        }
        return true;
    }


    // ** Modificamos el método validarFormatoHorarios para que añada errores a la lista **
    // Retorna true si todas las franjas son válidas, false si alguna no lo es.
    private boolean validarFormatoHorarios(String horariosStr, List<String> errores, int numeroLinea) {
        if (horariosStr == null || horariosStr.isBlank()) {
            // Este caso ya está cubierto en processSinglePeticionLine si partes.length < 6
            // pero como validación interna es bueno tenerlo.
            errores.add("Línea " + numeroLinea + " - Cadena de horarios vacía o nula");
            return false;
        }
        String[] horarios = horariosStr.split("_");
        if (horarios.length == 0 || horarios.length > 5) { // Máximo 5 franjas horarias según descripción
            errores.add("Línea " + numeroLinea + " - Número de franjas horarias inválido (" + horarios.length + "). Máximo 5 franjas separadas por '_'.");
            // No retornamos false inmediatamente, validamos cada franja individualmente
            // return false;
        }

        boolean allHorariosValid = true; // Bandera para saber si TODAS las franjas dentro de la máscara son válidas
        for (String horario : horarios) {
            if (horario == null || horario.isBlank()) {
                errores.add("Línea " + numeroLinea + " - Franja horaria individual vacía o nula dentro de la máscara.");
                allHorariosValid = false;
                continue;
            }
            String[] horas = horario.split("-");
            if (horas.length != 2) {
                errores.add("Línea " + numeroLinea + " - Formato de franja horaria individual inválido: '" + horario + "'. Esperado HH-HH.");
                allHorariosValid = false; // Marca la MÁSCARA como inválida, pero sigue verificando otras franjas
                continue; // Pasar a la siguiente franja horaria
            }
            try {
                int inicio = Integer.parseInt(horas[0]);
                int fin = Integer.parseInt(horas[1]);

                // Validar límites básicos y orden (inicio antes que fin)
                if (inicio < 0 || fin < 0 || inicio > 24 || fin > 24 || inicio >= fin) {
                    errores.add("Línea " + numeroLinea + " - Rango horario inválido en franja '" + horario + "': inicio=" + inicio + ", fin=" + fin + ". Asegúrate que 0 <= inicio < fin <= 24 y inicio < fin.");
                    allHorariosValid = false; // Marca la MÁSCARA como inválida, pero sigue verificando otras franjas
                }
                // La validación de duración mínima de 1 hora (fin - inicio >= 1) está cubierta por inicio < fin

            } catch (NumberFormatException e) {
                errores.add("Línea " + numeroLinea + " - Formato numérico inválido en franja horaria '" + horario + "': " + e.getMessage());
                allHorariosValid = false; // Marca la MÁSCARA como inválida
            }
        }
        return allHorariosValid; // Devuelve si TODAS las franjas individuales fueron válidas
    }


    // Getters para que el Controller y ViewModel accedan a la config y traducciones
    public YearMonth getMesProcesar() {
        return mesProcesar;
    }

    public Map<String, String> getTraducciones() {
        return traducciones;
    }

    // Ya no necesitamos un getter para las reservas cargadas, se devuelven en DataLoaderResult
    // public List<Reserva> getReservas() {
    //     return reservas; // Esto ya no se usa así
    // }

}