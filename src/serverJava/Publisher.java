package serverJava;

/**
 * Interfaz Publisher (Observable)
 * Define un formato para los publicadores en el patrón Observer
 */


public interface Publisher {
    
    /**
     * Suscribe un observer para recibir notificaciones
     * @param subscriber El suscriptor a agregar
     */
    void subscribe(Subscriber subscriber);
    
    /**
     * Cancela la suscripción de un observer
     * @param subscriber El suscriptor a eliminar
     */
    void unsubscribe(Subscriber subscriber);
    
    /**
     * Notifica a todos los suscriptores sobre un cambio
     * @param paquete El paquete con los datos a notificar
     */
    void notifySubscribers(Paquete paquete);
}