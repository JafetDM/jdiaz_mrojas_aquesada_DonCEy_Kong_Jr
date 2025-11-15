package serverJava;

/**
 * Interfaz Subscriber (Observer)
 * Define el contrato para todos los observadores que quieran
 * recibir actualizaciones del Publisher
 */
public interface Subscriber {
    /**
     * Método llamado cuando el Publisher notifica una actualización
     * @param paquete El paquete con la información actualizada
     */
    void update(Paquete paquete);
    
    /**
     * Obtiene el evento al que pertenece este subscriber
     * @return El evento asignado
     */
    Evento getEvento();
}