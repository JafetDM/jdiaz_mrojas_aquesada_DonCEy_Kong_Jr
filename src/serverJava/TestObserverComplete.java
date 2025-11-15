package serverJava;

/**
 * Test completo del patrón Observer
 * Demuestra Publisher/Subscriber con múltiples eventos
 */
public class TestObserverComplete {
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════╗");
        System.out.println("║  TEST: PATRÓN OBSERVER COMPLETO           ║");
        System.out.println("╚════════════════════════════════════════════╝\n");
        
        test1_CrearPublishers();
        test2_SuscribirObservers();
        test3_NotificarPorEvento();
        test4_ProcesarPaquetes();
        test5_Desuscribir();
        
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║  ✅ TODOS LOS TESTS PASARON               ║");
        System.out.println("╚════════════════════════════════════════════╝");
    }
    
    /**
     * Test 1: Crear Publishers para cada evento
     */
    private static void test1_CrearPublishers() {
        System.out.println("=== TEST 1: Crear Publishers ===");
        
        EventPublisher pub1 = new EventPublisher(Evento.JUEGO_1);
        EventPublisher pub2 = new EventPublisher(Evento.JUEGO_2);
        
        System.out.println("✓ " + pub1);
        System.out.println("✓ " + pub2);
        
        assert pub1.getEvento() == Evento.JUEGO_1;
        assert pub2.getEvento() == Evento.JUEGO_2;
        assert pub1.getCantidadSuscriptores() == 0;
        
        System.out.println("✅ Test 1 pasado\n");
    }
    
    /**
     * Test 2: Suscribir Observers
     */
    private static void test2_SuscribirObservers() {
        System.out.println("=== TEST 2: Suscribir Observers ===");
        
        EventPublisher publisher = new EventPublisher(Evento.JUEGO_1);
        
        // Crear subscribers mock
        Subscriber sub1 = crearMockSubscriber("Observer1", Evento.JUEGO_1);
        Subscriber sub2 = crearMockSubscriber("Observer2", Evento.JUEGO_1);
        Subscriber sub3 = crearMockSubscriber("Observer3", Evento.JUEGO_2); // Diferente evento
        
        // Suscribir
        publisher.subscribe(sub1);
        publisher.subscribe(sub2);
        publisher.subscribe(sub3); // No debería agregarse (diferente evento)
        
        System.out.println("Suscriptores en JUEGO_1: " + publisher.getCantidadSuscriptores());
        
        assert publisher.getCantidadSuscriptores() == 2 : "Deben haber 2 suscriptores";
        
        System.out.println("✅ Test 2 pasado\n");
    }
    
    /**
     * Test 3: Notificar solo a observers del mismo evento
     */
    private static void test3_NotificarPorEvento() {
        System.out.println("=== TEST 3: Notificar por Evento ===");
        
        EventPublisher pub1 = new EventPublisher(Evento.JUEGO_1);
        EventPublisher pub2 = new EventPublisher(Evento.JUEGO_2);
        
        // Contadores para verificar notificaciones
        final int[] contador1 = {0};
        final int[] contador2 = {0};
        
        // Subscribers que cuentan notificaciones
        Subscriber sub1 = new Subscriber() {
            public void update(Paquete p) {
                contador1[0]++;
                System.out.println("  → Sub1 (JUEGO_1) recibió: " + p.tipo);
            }
            public Evento getEvento() { return Evento.JUEGO_1; }
        };
        
        Subscriber sub2 = new Subscriber() {
            public void update(Paquete p) {
                contador2[0]++;
                System.out.println("  → Sub2 (JUEGO_2) recibió: " + p.tipo);
            }
            public Evento getEvento() { return Evento.JUEGO_2; }
        };
        
        pub1.subscribe(sub1);
        pub2.subscribe(sub2);
        
        // Notificar solo a JUEGO_1
        Paquete paquete = new Paquete("MOVIMIENTO", "Test", 100, 200);
        pub1.notifySubscribers(paquete);
        
        System.out.println("Notificaciones a JUEGO_1: " + contador1[0]);
        System.out.println("Notificaciones a JUEGO_2: " + contador2[0]);
        
        assert contador1[0] == 1 : "Sub1 debe recibir 1 notificación";
        assert contador2[0] == 0 : "Sub2 NO debe recibir notificaciones";
        
        System.out.println("✅ Test 3 pasado\n");
    }
    
    /**
     * Test 4: Procesar paquetes actualiza GameState
     */
    private static void test4_ProcesarPaquetes() {
        System.out.println("=== TEST 4: Procesar Paquetes ===");
        
        EventPublisher publisher = new EventPublisher(Evento.JUEGO_1);
        GameState gameState = publisher.getGameState();
        
        // Crear subscriber
        Subscriber sub = crearMockSubscriber("TestPlayer", Evento.JUEGO_1);
        publisher.subscribe(sub);
        
        // Procesar movimiento
        Paquete movimiento = Paquete.crearMovimiento("Jugador1", "ARRIBA", 150.5f, 250.3f);
        publisher.procesarPaquete(movimiento);
        
        // Verificar que se actualizó el GameState
        Paquete datos = gameState.obtenerJugador("Jugador1");
        
        System.out.println("Jugadores en GameState: " + gameState.getCantidadJugadores());
        System.out.println("Datos del jugador: x=" + datos.x + ", y=" + datos.y);
        
        assert gameState.getCantidadJugadores() == 1;
        assert datos != null;
        assert datos.x == 150.5f;
        assert datos.y == 250.3f;
        
        System.out.println("✅ Test 4 pasado\n");
    }
    
    /**
     * Test 5: Desuscribir observers
     */
    private static void test5_Desuscribir() {
        System.out.println("=== TEST 5: Desuscribir ===");
        
        EventPublisher publisher = new EventPublisher(Evento.JUEGO_1);
        
        Subscriber sub1 = crearMockSubscriber("Sub1", Evento.JUEGO_1);
        Subscriber sub2 = crearMockSubscriber("Sub2", Evento.JUEGO_1);
        
        publisher.subscribe(sub1);
        publisher.subscribe(sub2);
        
        System.out.println("Suscriptores antes: " + publisher.getCantidadSuscriptores());
        
        publisher.unsubscribe(sub1);
        
        System.out.println("Suscriptores después: " + publisher.getCantidadSuscriptores());
        
        assert publisher.getCantidadSuscriptores() == 1;
        
        System.out.println("✅ Test 5 pasado\n");
    }
    
    /**
     * Helper: Crea un subscriber mock para testing
     */
    private static Subscriber crearMockSubscriber(String nombre, Evento evento) {
        return new Subscriber() {
            @Override
            public void update(Paquete paquete) {
                System.out.println("  [" + nombre + "] Recibió: " + paquete.tipo);
            }
            
            @Override
            public Evento getEvento() {
                return evento;
            }
            
            @Override
            public String toString() {
                return "MockSubscriber{" + nombre + ", " + evento + "}";
            }
        };
    }
    
    /* 

     * Test de integración completo con GameServer
    
    public static void testIntegracion() {
        System.out.println("\n=== TEST INTEGRACIÓN: GameServer ===");
        
        // Crear server
        GameServer server = new GameServer(9999);
        
        // Verificar que se crearon publishers
        EventPublisher pub1 = server.getPublisher(Evento.JUEGO_1);
        EventPublisher pub2 = server.getPublisher(Evento.JUEGO_2);
        
        assert pub1 != null : "Publisher JUEGO_1 debe existir";
        assert pub2 != null : "Publisher JUEGO_2 debe existir";
        
        System.out.println("✓ Publishers creados correctamente");
        System.out.println("  - " + pub1);
        System.out.println("  - " + pub2);
        
        System.out.println("✅ Test de integración pasado\n");
    }
    */
}