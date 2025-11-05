// Cualquier cosa que exista en el mundo del juego
public interface ElementoJuego {
    int getId();
    float getX();
    float getY();
    void actualizar(float dt);  // mover, caer, etc.
    String getTipo();           // "CROC_RED", "CROC_BLUE", "FRUTA"
}