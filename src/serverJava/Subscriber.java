
package serverJava;

/**
 * Interfaz Subscriber (Observer)
 * Define el contrato para todos los observadores que quieran
 * recibir actualizaciones del estado del juego
 */
public interface Subscriber {
    /**
     * Método llamado cuando hay una actualización del estado
     * @param paquete El paquete con la información actualizada
     */
    void update(Paquete paquete);
}