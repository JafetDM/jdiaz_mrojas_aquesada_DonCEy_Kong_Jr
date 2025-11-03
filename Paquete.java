public class Paquete {

    // Clase que representa un “paquete” de datos (struct)
    
    public String playerName;
        public float x, y;
        public int movimiento;

    public Paquete(String playerName, float x, float y, int movimiento) {
        this.playerName = playerName;
        this.x = x;
        this.y = y;
        this.movimiento = movimiento;
    }
    
}
