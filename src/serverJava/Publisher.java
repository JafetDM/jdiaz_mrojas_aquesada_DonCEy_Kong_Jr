package src.serverJava;
public interface Publisher {
    void addSubscriber(Subscriber suscriptor);
    void removeSubscriber(Subscriber suscriptor);
    void notifySubscribers();
}
