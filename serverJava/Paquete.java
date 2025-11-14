package serverJava;
public class Paquete {
    public String player;
    public float x;
    public float y;
    public int movimiento;

    public Paquete(String player, float x, float y, int movimiento) {
        this.player = player;
        this.x = x;
        this.y = y;
        this.movimiento = movimiento;
    }


    public String toJson() {
        return "{"
            + "\"player\":\"" + player + "\","
            + "\"x\":" + x + ","
            + "\"y\":" + y + ","
            + "\"movimiento\":" + movimiento
            + "}";
    }

    public static Paquete fromJson(String json) {
        json = json.replace("{", "").replace("}", "");

        String[] tokens = json.split(",");

        String player = tokens[0].split(":")[1].replace("\"", "");
        float x = Float.parseFloat(tokens[1].split(":")[1]);
        float y = Float.parseFloat(tokens[2].split(":")[1]);
        int mov = Integer.parseInt(tokens[3].split(":")[1]);

        return new Paquete(player, x, y, mov);
    }

}
