package serverJava;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Clase de utilidades para manejo avanzado de JSON con Gson
 * Proporciona métodos para convertir objetos complejos, listas y mapas
 */
public class JsonUtils {
    
    // Gson con configuración formato general
    private static final Gson gson = new GsonBuilder()
        .setPrettyPrinting()           // genera JSON con saltos de línea (legible)
        .serializeNulls()              // mantiene campos nulos (en mensajes incompletos)
        .setDateFormat("yyyy-MM-dd HH:mm:ss")  // Formato de fechas (no se usa aqui)
        .create();
    
    // Gson compacto para red (más simple)
    private static final Gson gsonCompact = new GsonBuilder()
        .serializeNulls()
        .create();
    
    // =============== SERIALIZACIÓN ===============
    
    /**
     * Convierte cualquier objeto a JSON (versión legible)
     */
    public static String toJson(Object obj) {
        if (obj == null) return "null";
        try {
            return gson.toJson(obj); 
        } catch (Exception e) {
            System.err.println("Error al serializar: " + e.getMessage());
            return "{}";
        }
    }
    
    /**
     * Convierte cualquier objeto a JSON compacto (para red)
     */
    public static String toJsonCompact(Object obj) {
        if (obj == null) return "null";
        try {
            return gsonCompact.toJson(obj);
        } catch (Exception e) {
            System.err.println("Error al serializar: " + e.getMessage());
            return "{}";
        }
    }
    
    /**
     * Convierte una lista a JSON
     */
    public static <T> String listToJson(List<T> lista) {
        if (lista == null) return "[]";
        try {
            return gson.toJson(lista);
        } catch (Exception e) {
            System.err.println("Error al serializar lista: " + e.getMessage());
            return "[]";
        }
    }
    
    /**
     * Convierte un mapa a JSON
     */
    public static <K, V> String mapToJson(Map<K, V> mapa) {
        if (mapa == null) return "{}";
        try {
            return gson.toJson(mapa);
        } catch (Exception e) {
            System.err.println("Error al serializar mapa: " + e.getMessage());
            return "{}";
        }
    }
    
    // =============== DESERIALIZACIÓN ===============
    
    /**
     * Convierte JSON a un objeto de la clase especificada
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.trim().isEmpty()) {
            System.err.println("JSON vacío");
            return null;
        }
        try {
            return gson.fromJson(json, clazz);
        } catch (Exception e) {
            System.err.println("Error al deserializar a " + clazz.getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Convierte JSON a una lista de objetos
     * Uso: List<Paquete> lista = JsonUtils.fromJsonList(json, new TypeToken<List<Paquete>>(){})
     */
    public static <T> T fromJsonList(String json, TypeToken<T> typeToken) {
        if (json == null || json.trim().isEmpty()) {
            System.err.println("JSON vacío");
            return null;
        }
        try {
            return gson.fromJson(json, typeToken.getType());
        } catch (Exception e) {
            System.err.println("Error al deserializar lista: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Convierte JSON a un Map genérico
     */
    public static Map<String, Object> jsonToMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            return gson.fromJson(json, type);
        } catch (Exception e) {
            System.err.println("Error al deserializar mapa: " + e.getMessage());
            return null;
        }
    }
    
    // =============== UTILIDADES ===============
    
    /**
     * Valida si un String es JSON válido
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        try {
            gson.fromJson(json, Object.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Formatea (embellece) un JSON compacto
     */
    public static String prettifyJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }
        try {
            Object obj = gsonCompact.fromJson(json, Object.class);
            return gson.toJson(obj);
        } catch (Exception e) {
            System.err.println("Error al formatear JSON: " + e.getMessage());
            return json;
        }
    }
    
    /**
     * Compacta un JSON (elimina espacios y saltos de línea)
     */
    public static String minifyJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }
        try {
            Object obj = gson.fromJson(json, Object.class);
            return gsonCompact.toJson(obj);
        } catch (Exception e) {
            System.err.println("Error al minificar JSON: " + e.getMessage());
            return json;
        }
    }
    
    /**
     * Clona un objeto mediante serialización/deserialización JSON
     */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T objeto) {
        if (objeto == null) return null;
        try {
            String json = gson.toJson(objeto);
            return (T) gson.fromJson(json, objeto.getClass());
        } catch (Exception e) {
            System.err.println("Error al clonar objeto: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Obtiene el Gson configurado (para usos avanzados)
     */
    public static Gson getGson() {
        return gson;
    }
    
    /**
     * Obtiene el Gson compacto (para usos avanzados)
     */
    public static Gson getGsonCompact() {
        return gsonCompact;
    }
}