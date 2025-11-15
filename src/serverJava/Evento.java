package serverJava;

/**
 * Enum para identificar diferentes instancias de juego
 * Cada cliente se conecta a un juego específico
 */
public enum Evento {
    JUEGO_1,
    JUEGO_2;
    
    /**
     * Obtiene un evento por índice
     * @param index índice del juego (0 = JUEGO_1, 1 = JUEGO_2, etc.)
     * @return El evento correspondiente
     */
    public static Evento fromIndex(int index) {
        Evento[] valores = values();
        if (index >= 0 && index < valores.length) {
            return valores[index];
        }
        return JUEGO_1; // Por defecto
    }
    
    /**
     * Obtiene el índice del evento
     * @return índice (0, 1, etc.)
     */
    public int getIndex() {
        return this.ordinal();
    }
    
    @Override
    public String toString() {
        return this.name();
    }
}