package serverJava;
// Cualquier cosa que exista en el mundo del juego
public interface ElementoJuego {
    Integer getId();              
    Float getX();                 
    Float getY();                 
    void actualizar(Float dt);    
    String getTipo();             
}