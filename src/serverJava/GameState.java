package serverJava;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * GameState - Estado simple de un juego
 * Contiene solo las posiciones (x, y) de los jugadores por ahora
 */
public class GameState {
    
    // Evento al que pertenece este estado
    private Evento evento;
    
    // Map con los datos de cada jugador: playerName -> Paquete con (x, y)
    private Map<String, PlayerState> jugadores;

    //Enemigos y frutas
    private List<EnemyState> enemigos;
    private List<FruitState> frutas;
    
    // Timestamp de última actualización
    private Long timestamp;

    // Map para controlar el cooldown de daño por jugador
    private Map<String, Long> ultimoDaño;

    // Cooldown en ms entre daños (enemigo/caída)
    private static final Long HIT_COOLDOWN_MS = 1000L; // 1 segundo
    
    /**
     * Constructor
     */
    public GameState(Evento evento) {
        this.evento = evento;
        this.jugadores = new HashMap<>();
        this.enemigos = new ArrayList<>();
        this.frutas = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
        this.ultimoDaño = new HashMap<>();
    }
    
    /**
     * Actualiza la posición de un jugador
     */
    public void actualizarJugador(String playerName, Float x, Float y) {
        PlayerState p = jugadores.get(playerName);
        if (p == null) {
            p = new PlayerState();
            p.playerName = playerName;
            p.vida = 3;     // vidas iniciales
            p.puntos = 0;   // puntos iniciales
            jugadores.put(playerName, p);
        }

        p.x = x;
        p.y = y;

        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Suma puntos al jugador indicado, si existe.
     */
    public void sumarPuntosAJugador(String playerName, Integer puntos) {
        if (puntos <= 0) return;

        PlayerState p = jugadores.get(playerName);
        if (p != null) {
            p.puntos += puntos;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Resta vidas a un jugador
     * @return true si el jugador llegó a 0 vidas (Game Over)
     */
    public Boolean restarVidaAJugador(String playerName, Integer cantidad) {
        PlayerState jugador = obtenerJugador(playerName);
        if (jugador != null) {
            jugador.vida -= cantidad;
            if (jugador.vida <= 0) {
                jugador.vida = 0;
                return Boolean.TRUE; // GAME OVER
            }
            jugador.lastDamageTime = System.currentTimeMillis();
        }
        return Boolean.FALSE;
    }

    /**
     * Resetea un jugador tras Game Over (vidas y puntos a inicial)
     */
    public void resetearJugador(String playerName) {
        PlayerState jugador = obtenerJugador(playerName);
        if (jugador != null) {
            jugador.vida = 3;
            jugador.puntos = 0;
            System.out.println("[GAME OVER] " + playerName + " reseteado");
        }
    }
    
    /**
     * Obtiene los datos de un jugador
     */
    public PlayerState obtenerJugador(String playerName) {
        return jugadores.get(playerName);
    }
    
    /**
     * Elimina un jugador del estado
     */
    public void eliminarJugador(String playerName) {
        jugadores.remove(playerName);
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Obtiene todos los jugadores
     */
    public Map<String, PlayerState> obtenerJugadores() {
        return new HashMap<>(jugadores); // copia defensiva
    }

    // Clase interna para el estado de un jugador
    public static class PlayerState {
        public String playerName;
        public Float x;
        public Float y;
        public Integer vida;
        public Integer puntos;
        public Boolean trepando;      // está trepando?
        public Integer lianaActual;       // índice de liana (-1 si no está en ninguna)
        public String estadoMovimiento; // "CAMINANDO", "TREPANDO", "CAYENDO", "SALTANDO"
        public Long invulnerableHastaMs;
        public Boolean recienRespawneado;
        public Long lastDamageTime; 
        public Long ultimaVictoriaMs;
        
        public PlayerState() {
            this.trepando = Boolean.FALSE;
            this.lianaActual = -1;
            this.estadoMovimiento = "CAMINANDO";
            this.recienRespawneado = Boolean.FALSE;
            this.invulnerableHastaMs = 0L;
            this.lastDamageTime = 0L;
            this.ultimaVictoriaMs = 0L;
        }
    }

    // Clases internas para enemigos y frutas
    public static class EnemyState {
        public Integer id;
        public String tipo;
        public Float x;
        public Float y;
        public Float velocidad;
        public String direccion;
    }

    public static class FruitState {
        public Integer id;
        public Float x;
        public Float y;
        public Integer puntos;
        public Boolean recolectada;
        public String tipo;
    }
    
    // Actualiza la lista de enemigos y frutas
    public void actualizarEnemigosYFrutas(List<ElementoJuego> elementos) {
        enemigos.clear();
        frutas.clear();

        if (elementos == null) return;

        for (ElementoJuego e : elementos) {
            if (e instanceof Enemigo) {
                Enemigo en = (Enemigo) e;

                EnemyState es = new EnemyState();
                es.id = en.getId();
                es.tipo = en.getTipo();
                es.x = en.getX();
                es.y = en.getY();
                es.velocidad = en.velocidad; // protegido, pero mismo paquete
                es.direccion = en.getDireccion();

                enemigos.add(es);

            } else if (e instanceof Fruta) {
                Fruta fr = (Fruta) e;

                FruitState fs = new FruitState();
                fs.id = fr.getId();
                fs.x = fr.getX();
                fs.y = fr.getY();
                fs.puntos = fr.getPuntos();
                fs.recolectada = Boolean.FALSE; // por ahora siempre false
                fs.tipo = fr.getTipoFruta();

                frutas.add(fs);
            }
        }

        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Procesa colisiones y respawns
     * @return Lista de jugadores que llegaron a 0 vidas (Game Over)
     */
    public List<String> procesarColisionesYRespawns(GestorJuego gestor) {
        List<String> jugadoresGameOver = new ArrayList<>(); 
        
        if (jugadores.isEmpty()) return jugadoresGameOver;  

        // Spawn en la plataforma 0
        Float spawnX = LayoutDKJr.getXCentroPlataforma(0);
        Float spawnY = LayoutDKJr.getYForPlataforma(0) - 15.0f;

        // RADIOS AJUSTADOS SEGÚN ESTADO DEL JUGADOR
        final Float PLAYER_RADIUS_NORMAL = 14.0f;
        final Float PLAYER_RADIUS_TREPAR = 24.0f;  // MÁS GRANDE cuando trepa
        final Float ENEMY_RADIUS  = 16.0f;
        final Float FRUIT_RADIUS  = 18.0f;

        // Límite de caída
        Float fallLimitY = 700.0f;
        Long now = System.currentTimeMillis();

        List<FruitState> frutasAEliminar = new ArrayList<>();

        // DEBUG flag temporal para imprimir info detallada de colisiones
        final Boolean DEBUG_COLLISIONS = true;

        for (PlayerState p : jugadores.values()) {
            Float px = p.x;
            Float py = p.y;
            // AJUSTAR RADIO DE COLISIÓN SEGÚN SI ESTÁ TREPANDO
            Float playerRadius = p.trepando.booleanValue() ? PLAYER_RADIUS_TREPAR : PLAYER_RADIUS_NORMAL;

            if (p.recienRespawneado.booleanValue()) {
                Float margenSeguro = 30.0f; // píxeles por encima del límite de caída
                // Si ya está en zona segura (bien por encima del límite), le quitamos la protección
                if (py < (fallLimitY - margenSeguro)) {
                    p.recienRespawneado = Boolean.FALSE;
                } else {
                    // Sigue con coordenadas "raras" (por paquetes viejos de caída),
                    // saltamos toda la lógica de daño en este frame
                    continue;
                }
            }
            
            // Cooldown de daño
            Long lastHit = ultimoDaño.get(p.playerName);
            Long elapsed = (lastHit == null) ? Long.MAX_VALUE : (now - lastHit);
            Boolean invulnerable = (now < p.invulnerableHastaMs);
            Boolean puedeRecibirDaño = !invulnerable.booleanValue() && (elapsed > HIT_COOLDOWN_MS);

            // DETECTAR CAÍDA AL VACÍO
            Boolean caida = (py > fallLimitY);
            Boolean chocaEnemigo = Boolean.FALSE;

            // DETECTAR COLISIÓN CON ENEMIGOS
            if (puedeRecibirDaño.booleanValue() && !caida.booleanValue()) {
                for (EnemyState e : enemigos) {
                    Float dx = e.x - px;
                    Float dy = e.y - py;

                    // USAR RADIO AJUSTADO
                    Float minDist = ENEMY_RADIUS + playerRadius;
                    if (p.trepando.booleanValue()) {
                        // Cuando el jugador está trepando, la distancia vertical puede ser
                        // mayor (está sobre la liana). Para que los enemigos puedan golpear
                        // en la liana, comprobamos separación horizontal y una tolerancia
                        // vertical específica en lugar de la distancia euclidiana completa.
                        final Float VERTICAL_TOLERANCE_TREPAR = 40.0f; // px
                        Boolean hitTrepar = (Math.abs(dx) <= minDist && Math.abs(dy) <= VERTICAL_TOLERANCE_TREPAR);
                        if (hitTrepar.booleanValue()) {
                            chocaEnemigo = Boolean.TRUE;
                            String estado = "TREPANDO";
                            System.out.println("[COLISION] " + p.playerName + 
                                    " [" + estado + "] golpeado por " + e.tipo + 
                                    " | dx=" + String.format("%.1f", Math.abs(dx)) +
                                    " dy=" + String.format("%.1f", Math.abs(dy)) +
                                    " | pos=(" + String.format("%.1f,%.1f", px, py) + ")");
                            break;
                        } else if (DEBUG_COLLISIONS.booleanValue()) {
                            // Imprimir información detallada para debug cuando no encaja
                            //System.out.println("[DEBUG-COL] " + p.playerName + " trepando - enemigo " + e.tipo +
                                    //" | p=(" + String.format("%.1f,%.1f", px, py) + ") e=(" + String.format("%.1f,%.1f", e.x, e.y) + ")" +
                                    //" | dx=" + String.format("%.1f", dx) + " dy=" + String.format("%.1f", dy) +
                                    //" | minDist=" + String.format("%.1f", minDist) + " V_TOL=" + VERTICAL_TOLERANCE_TREPAR);
                        }
                    } else {
                        Float dist2 = dx * dx + dy * dy;
                        if (dist2 < minDist * minDist) {
                            chocaEnemigo = Boolean.TRUE;
                            String estado = "NORMAL";
                            System.out.println("[COLISION] " + p.playerName + 
                                    " [" + estado + "] golpeado por " + e.tipo + 
                                    " | dist=" + String.format("%.1f", Math.sqrt(dist2)) +
                                    " | pos=(" + String.format("%.1f,%.1f", px, py) + ")"); 
                            break;
                        }
                    }
                }
            }

            // --- Colisión con frutas ---
            for (FruitState f : frutas) {
                if (f.recolectada.booleanValue()) continue;

                Float dx = f.x - px;
                Float dy = f.y - py;
                Float dist2 = dx * dx + dy * dy;
                if (dist2 < FRUIT_RADIUS * FRUIT_RADIUS) {
                    p.puntos += f.puntos;
                    f.recolectada = Boolean.TRUE;
                    frutasAEliminar.add(f);
                    System.out.println("[FRUTA] " + p.playerName + 
                                    " recolectó " + f.tipo + " (+" + f.puntos + " pts)");
                }
            }

            // APLICAR DAÑO SI CORRESPONDE
            if (puedeRecibirDaño.booleanValue() && (caida.booleanValue() || chocaEnemigo.booleanValue())) {

                // Log del evento
                if (caida.booleanValue()) {
                    System.out.println("[CAÍDA] " + p.playerName + 
                                    " cayó al vacío (y=" + String.format("%.1f", py) + ")");
                }

                // 1) Restar vida
                Integer vidasAntes = p.vida;
                if (p.vida > 0) {
                    p.vida--;
                }
                System.out.println("[VIDA] " + p.playerName + 
                                " -> " + vidasAntes + " => " + p.vida + " vidas");

                // 2) Si llegó a 0 vidas: GAME OVER
                if (p.vida <= 0) {
                    jugadoresGameOver.add(p.playerName);  
                    
                    p.vida = 3;
                    p.puntos = 0;
                    p.invulnerableHastaMs = now + 2000L;
                    System.out.println("[GAME OVER] " + p.playerName + 
                                    " -> vidas=3, puntos=0 (RESET COMPLETO)");
                }

                // 3) RESPAWN FORZOSO
                Float oldX = p.x;
                Float oldY = p.y;
                
                p.x = spawnX;
                p.y = spawnY;

                p.recienRespawneado = Boolean.TRUE;
                
                // 4) Resetear estado de movimiento
                p.trepando = Boolean.FALSE;
                p.lianaActual = -1;
                p.estadoMovimiento = "CAMINANDO";

                System.out.println("[RESPAWN] " + p.playerName + 
                                " | desde (" + String.format("%.1f,%.1f", oldX, oldY) + 
                                ") => P0 (" + String.format("%.1f,%.1f", spawnX, spawnY) + ")");

                // 5) Actualizar cooldown
                ultimoDaño.put(p.playerName, now);
                
                // FORZAR TIMESTAMP PARA QUE EL CLIENTE RECIBA ACTUALIZACIÓN INMEDIATA
                this.timestamp = now;
            }
        }

        // Eliminar frutas recolectadas
        for (FruitState f : frutasAEliminar) {
            gestor.eliminarFrutaPorPosicion(f.x, f.y, 5.0f);
        }

        // ACTUALIZAR TIMESTAMP SIEMPRE
        this.timestamp = System.currentTimeMillis();

        return jugadoresGameOver; 
    }

    /**
     * Verifica si un jugador llegó a la plataforma objetivo (victoria)
     * Incluye cooldown para evitar victorias múltiples
     */
    public Boolean verificarVictoria(String playerName) {
        PlayerState p = obtenerJugador(playerName);
        if (p == null) return Boolean.FALSE;
        
        //COOLDOWN DE VICTORIA: 3 segundos desde la última victoria
        final Long COOLDOWN_VICTORIA_MS = 3000L;  
        Long ahora = System.currentTimeMillis();  
        
        if (ahora - p.ultimaVictoriaMs < COOLDOWN_VICTORIA_MS) {
            // Aún en cooldown, no puede ganar de nuevo
            return Boolean.FALSE;
        }
        
        // Plataforma 10 es la meta
        final Integer PLATFORM_GOAL_INDEX = 10;     
        LayoutDKJr.PlataformaDef goal = LayoutDKJr.getPlataforma(PLATFORM_GOAL_INDEX);
        
        // Tolerancias
        final Float TOLERANCE_Y = 30.0f;  
        final Float TOLERANCE_X = 20.0f;  
        
        // Verificar posición
        Boolean enRangoVertical = Math.abs(p.y - goal.y) < TOLERANCE_Y;  
        Boolean enRangoHorizontal = (p.x >= goal.xLeft - TOLERANCE_X) &&   
                                     (p.x <= goal.xRight + TOLERANCE_X);
        
        if (enRangoVertical.booleanValue() && enRangoHorizontal.booleanValue()) {  
            System.out.println("[VICTORIA] " + playerName + 
                             " llegó a la plataforma objetivo! (" + 
                             String.format("%.1f, %.1f", p.x, p.y) + ")");
            return Boolean.TRUE;  
        }
        
        return Boolean.FALSE;  
    }
    
    /**
     * Procesa victoria: suma vida y respawnea en plataforma inicial
     */
    public void procesarVictoria(String playerName) {
        PlayerState jugador = obtenerJugador(playerName);
        if (jugador != null) {
            Long ahora = System.currentTimeMillis();  
            
            // ⚡ Actualizar timestamp de última victoria (para cooldown)
            jugador.ultimaVictoriaMs = ahora;
            
            // Sumar vida por victoria
            jugador.vida++;
            
            // Respawn en plataforma inicial
            Float spawnX = LayoutDKJr.getXCentroPlataforma(0);  
            Float spawnY = LayoutDKJr.getYForPlataforma(0) - 15.0f;  
            
            jugador.x = spawnX;
            jugador.y = spawnY;
            jugador.trepando = Boolean.FALSE;  
            jugador.lianaActual = -1;
            jugador.estadoMovimiento = "CAMINANDO";
            jugador.recienRespawneado = Boolean.TRUE;  
            jugador.invulnerableHastaMs = ahora + 2000L;  // 2 segundos de invulnerabilidad
            
            System.out.println("[VICTORIA] " + playerName + 
                             " reseteado con " + jugador.vida + " vidas");
            
            this.timestamp = ahora;
        }
    }

    /**
     * Reinicia completamente un jugador (vidas, puntos, posición)
     */
    public void reiniciarJugadorCompleto(String playerName) {
        PlayerState jugador = obtenerJugador(playerName);
        if (jugador != null) {
            Long ahora = System.currentTimeMillis();
            
            // Resetear stats
            jugador.vida = 3;
            jugador.puntos = 0;
            
            // Respawn en plataforma inicial
            Float spawnX = LayoutDKJr.getXCentroPlataforma(0);
            Float spawnY = LayoutDKJr.getYForPlataforma(0) - 15.0f;
            
            jugador.x = spawnX;
            jugador.y = spawnY;
            jugador.trepando = Boolean.FALSE;
            jugador.lianaActual = -1;
            jugador.estadoMovimiento = "CAMINANDO";
            jugador.recienRespawneado = Boolean.TRUE;
            jugador.invulnerableHastaMs = ahora + 2000L;
            jugador.ultimaVictoriaMs = 0L;
            
            System.out.println("[RESET] " + playerName + 
                             " reiniciado completamente (vida=3, puntos=0)");
            
            this.timestamp = ahora;
        }
    }


    /**
     * Convierte el GameState a JSON usando Gson
     */
    public String toJson() {
        return JsonUtils.toJson(this);
    }
    
    /**
     * Crea un GameState desde JSON
     */
    public static GameState fromJson(String json) {
        return JsonUtils.fromJson(json, GameState.class);
    }

    // =============== LIANAS ==========================

    /**
     * Detecta si el jugador está cerca de una liana y puede trepar
     */
    private Integer detectarLianaCercana(Float x, Float y) {
        final Float TOLERANCIA_X = 15.0f; // píxeles de tolerancia horizontal
        
        for (Integer i = 0; i < LayoutDKJr.getCantidadLianas(); i++) {
            LayoutDKJr.LianaDef liana = LayoutDKJr.getLiana(i);
            
            // Verificar si está cerca horizontalmente
            Float dx = Math.abs(liana.x - x);
            if (dx <= TOLERANCIA_X) {
                // Verificar si está dentro del rango vertical de la liana
                if (y >= liana.yTop - 20.0f && y <= liana.yBottom + 20.0f) {
                    return i;
                }
            }
        }
        return -1; // No está cerca de ninguna liana
    }

    /**
     * Actualiza el estado de trepar del jugador
     */
    public void actualizarEstadoTrepar(String playerName, Boolean intentaTrepar) {
        PlayerState p = jugadores.get(playerName);
        if (p == null) return;
        
        if (intentaTrepar.booleanValue()) {
            // Detectar si está cerca de una liana
            Integer lianaIndex = detectarLianaCercana(p.x, p.y);
            
            if (lianaIndex >= 0) {
                p.trepando = Boolean.TRUE;
                p.lianaActual = lianaIndex;
                p.estadoMovimiento = "TREPANDO";
                
                // Ajustar X para centrar en la liana
                LayoutDKJr.LianaDef liana = LayoutDKJr.getLiana(lianaIndex);
                p.x = liana.x;
            }
        } else {
            // Soltar la liana
            p.trepando = Boolean.FALSE;
            p.lianaActual = -1;
            p.estadoMovimiento = "CAMINANDO";
        }
        
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Variante que recibe la Y propuesta por el cliente para sincronizar la posición
     * al iniciar el trepar. El servidor la clampa dentro del rango de la liana.
     */
    public void actualizarEstadoTrepar(String playerName, Boolean intentaTrepar, Float x, Float y) {
        PlayerState p = jugadores.get(playerName);
        if (p == null) return;

        if (intentaTrepar.booleanValue()) {
            // Detectar liana cercana usando la X/Y propuestas por el cliente
            Integer lianaIndex = detectarLianaCercana(x, y);

            if (lianaIndex >= 0) {
                p.trepando = Boolean.TRUE;
                p.lianaActual = lianaIndex;
                p.estadoMovimiento = "TREPANDO";

                // Ajustar X para centrar en la liana
                LayoutDKJr.LianaDef liana = LayoutDKJr.getLiana(lianaIndex);
                p.x = liana.x;

                // Clampear la Y dentro de la liana
                if (y < liana.yTop) p.y = liana.yTop;
                else if (y > liana.yBottom) p.y = liana.yBottom;
                else p.y = y;
            }
        } else {
            p.trepando = Boolean.FALSE;
            p.lianaActual = -1;
            p.estadoMovimiento = "CAMINANDO";
            // Actualizar la Y del jugador al valor proporcionado por el cliente
            p.y = y;
        }

        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Procesa movimiento vertical cuando está trepando
     */
    public void moverEnLiana(String playerName, Float deltaY) {
        PlayerState p = jugadores.get(playerName);
        if (p == null || !p.trepando.booleanValue()) return;
        LayoutDKJr.LianaDef liana = LayoutDKJr.getLiana(p.lianaActual);
        
        // Mover en Y
        p.y += deltaY;
        
        // Limitar movimiento dentro de la liana
        if (p.y < liana.yTop) {
            p.y = liana.yTop;
        }
        if (p.y > liana.yBottom) {
            p.y = liana.yBottom;
            // Opcional: soltar al llegar al fondo
            // p.trepando = false;
            // p.lianaActual = -1;
        }
        
        this.timestamp = System.currentTimeMillis();
    }
    
    // =============== GETTERS Y SETTERS ===============
    
    public Evento getEvento() {
        return evento;
    }
    
    public void setEvento(Evento evento) {
        this.evento = evento;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public Integer getCantidadJugadores() {
        return jugadores.size();
    }

    public List<EnemyState> getEnemigos() { 
        return enemigos; 
    }

    public List<FruitState> getFrutas() { 
        return frutas; 
    }

    @Override
    public String toString() {
        return "GameState{" +
                "evento=" + evento +
                ", jugadores=" + jugadores.size() +
                ", timestamp=" + timestamp +
                '}';
    }
}
