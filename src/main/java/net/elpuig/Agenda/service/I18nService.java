package net.elpuig.Agenda.service;

import jakarta.annotation.PostConstruct; 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class I18nService {

    private static final Logger logger = LoggerFactory.getLogger(I18nService.class);

    private final Map<String, Map<String, String>> todosLosIdiomasTraducciones = new HashMap<>();

    @PostConstruct
    public void init() {
        logger.info("Iniciando carga de traducciones...");
        // Define aquí todos los archivos de propiedades de internacionalización que esperas tener
        // y el código de idioma asociado.
        // Asegúrate de que estos archivos estén en src/main/resources/i18n/
        loadTranslations("ESP", "internacional.ESP.properties");
        loadTranslations("ENG", "internacional.ENG.properties");
        loadTranslations("CAT", "internacional.CAT.properties");
        loadTranslations("ZHO", "internacional.ZHO.properties");
        loadTranslations("JPN", "internacional.JPN.properties");
        loadTranslations("ARA", "internacional.ARA.properties");
        // NUEVAS ADICIONES: Francés, Alemán y Ruso
        loadTranslations("FRA", "internacional.FRA.properties"); // Francés
        loadTranslations("DEU", "internacional.DEU.properties"); // Alemán
        loadTranslations("RUS", "internacional.RUS.properties"); // Ruso

        logger.info("Carga de traducciones finalizada. Idiomas cargados: {}", todosLosIdiomasTraducciones.keySet());
        if (todosLosIdiomasTraducciones.isEmpty()) {
            logger.warn("¡Advertencia! No se cargó ningún archivo de traducción. Verifique la ruta y los nombres de archivo.");
        }
    }

    private void loadTranslations(String langCode, String filename) {
        String path = "i18n/" + filename;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                logger.warn("No se pudo encontrar el archivo de traducciones: {}. Asegúrese de que esté en src/main/resources/i18n/", path);
                return;
            }
            Properties prop = new Properties();
            prop.load(new InputStreamReader(input, "UTF-8"));
            Map<String, String> langMap = new HashMap<>();
            prop.forEach((key, value) -> langMap.put(key.toString(), value.toString()));
            todosLosIdiomasTraducciones.put(langCode, langMap);
            logger.info("Traducciones para {} cargadas exitosamente desde {}.", langCode, path);
        } catch (IOException e) {
            logger.error("Error al cargar traducciones para {} desde {}: {}", langCode, path, e.getMessage(), e);
        }
    }

    public Map<String, Map<String, String>> getTodosLosIdiomasTraducciones() {
        return Collections.unmodifiableMap(todosLosIdiomasTraducciones);
    }

    public Map<String, String> getTraduccionesPorIdioma(String langCode) {
        return Collections.unmodifiableMap(todosLosIdiomasTraducciones.getOrDefault(langCode, Collections.emptyMap()));
    }
}