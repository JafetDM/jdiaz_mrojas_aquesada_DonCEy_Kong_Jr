// cliente.c - Adaptado para comunicarse con servidor Java usando Paquete y GameState
 
// Escalas para renderizado
#define PLAYER_SCALE 0.3f   
#define FRUIT_SCALE  0.2f   
#define ENEMY_TARGET_SIZE_RED   40.0f
#define ENEMY_TARGET_SIZE_BLUE  42.0f 

// Radio de colisión del jugador contra enemigos (px)
#define PLAYER_HIT_RADIUS 24.0f

// Física del juego
#define GRAVITY    900.0f   // píxeles / s^2
#define MOVE_SPEED 220.0f   // píxeles / s
#define JUMP_SPEED -320.0f  // píxeles / s (negativo = hacia arriba)

// Librerías estándar
#include <math.h> 
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <pthread.h>
#include <stdbool.h>
#include <errno.h>

// Librerías externas
#include "raylib.h"
#include "librerias/cJSON.h"
#include "config.h"
#include "layout.h" 
#include <sys/types.h>
#include <sys/wait.h>

// ========================
// Estructuras de estado
// ========================

// Estados del jugador
typedef enum {
    ESTADO_CAMINANDO,
    ESTADO_TREPANDO,
    ESTADO_CAYENDO,
    ESTADO_SALTANDO
} EstadoJugador;

typedef struct {
    char playerName[64];
    float x, y;
    int vida;
    int puntos;
    bool trepando;           // NUEVO: está trepando?
    int lianaActual;         // NUEVO: índice de liana (-1 si no está)
    char estadoMovimiento[32]; // NUEVO: "CAMINANDO", "TREPANDO", etc.
} Player;

typedef struct {
    char id[32];
    char tipo[32];
    float x, y;
    float velocidad;
    char direccion[16];
} Enemy;

typedef struct {
    char id[32];
    float x, y;
    int puntos;
    bool recolectada;
    char tipo[32];
} Fruit;

typedef struct {
    char evento[32];        // "JUEGO_1" o "JUEGO_2"
    long timestamp;
    
    // Map de jugadores: playerName -> {x, y}
    Player *jugadores;
    int totalJugadores;
    int capacityJugadores;
    
    Enemy *enemigos;
    int totalEnemigos;
    int capacityEnemigos;
    
    Fruit *frutas;
    int totalFrutas;
    int capacityFrutas;
    
    pthread_mutex_t mutex;
} GameState;

// ========================
// Declaraciones adelantadas
// ========================
static void parse_game_state_json(const char *jsonText);
static void parse_paquete_json(const char *jsonText);

// Funciones de física y movimiento
static float get_player_half_height(void);
static void init_player_start_position(void);
static void resolver_colision_plataformas(void);
static void send_input_from_keys(void);
static void respawn_player(void);

// Funciones de red
static int send_paquete(const char *tipo, const char *movimiento, float x, float y);
static void *network_thread(void *arg);

// Funciones de trepar
static int detectar_liana_cercana(float x, float y);
static int buscar_liana_cercana_horizontal(int lianaActual, int direccion);
static void iniciar_trepar(void);
static void soltar_liana(void);
static void procesar_movimiento_trepar(float dt);

// Renderizado
static void render_game(Texture2D stageTex);

static bool g_showLayoutDebug = true;

static GameState g_state;
static int g_sock = -1;
static volatile bool g_running = true;
static pthread_mutex_t g_send_mutex = PTHREAD_MUTEX_INITIALIZER;
static char g_playerName[64] = "ClienteC";
static char g_eventoAsignado[32] = "";
// Flag para indicar si este cliente es espectador
static bool g_isSpectator = false;
// Si somos espectador, nombre del jugador objetivo (ej. "Jugador1")
static char g_spectatorTarget[64] = "";
// Si se lanzó el cliente en modo espejo: esta instancia es un espectador que
// debe renderizar la vista exactamente centrada en un jugador objetivo.
static bool g_spectatorMirrorMode = false;

// Ruta/executable usada para lanzar instancias espejo (copiada de argv[0])
static char g_execPath[512] = "./cliente";

// Lanzar un nuevo proceso cliente en modo espejo para este jugador
static void launch_spectator_instance(const char *playerName) {
    pid_t pid = fork();
    if (pid < 0) {
        perror("fork");
        return;
    }
    if (pid == 0) {
        // Child: ejecutar nueva instancia
        execlp(g_execPath, g_execPath, "--mirror", playerName, (char*)NULL);
        // Si execlp falla
        perror("execlp");
        _exit(1);
    } else {
        // Parent: opcionalmente no esperar; imprimimos PID
        printf("[LAUNCH] Spectator instance launched (pid=%d) for %s\n", (int)pid, playerName);
    }
}

// Física del jugador local (lado cliente)
static float g_playerX = 0.0f;
static float g_playerY = 0.0f;
static float g_playerVy = 0.0f;     // velocidad vertical
static float g_playerPrevY = 0.0f;  // posición Y del frame anterior (para colisiones continuas)
static bool  g_playerGrounded = false;
static bool  g_playerInitialized = false;

// Variables globales adicionales para trepar (declaradas temprano para uso en funciones)
static EstadoJugador g_playerEstado = ESTADO_CAMINANDO;
static int g_lianaActual = -1;  // -1 = no está en ninguna liana
static float g_velocidadTrepar = 150.0f;  // píxeles por segundo


// Texturas globales
static Texture2D g_playerTex = {0};
static Texture2D g_stageTex  = {0};


// Frutas
static Texture2D g_texMango   = {0};
static Texture2D g_texBanano  = {0};
static Texture2D g_texManzana = {0};

// Cocodrilos rojos
static Texture2D g_texCrocRedUp    = {0};
static Texture2D g_texCrocRedDown  = {0};
static Texture2D g_texCrocRedLeft  = {0};
static Texture2D g_texCrocRedRight = {0};

// Cocodrilos azules
static Texture2D g_texCrocBlueUp    = {0};
static Texture2D g_texCrocBlueDown  = {0};
static Texture2D g_texCrocBlueLeft  = {0};
static Texture2D g_texCrocBlueRight = {0};


// ===========================
// Funciones auxiliares para arrays dinámicos
// ===========================

static void ensure_player_capacity(GameState *s, int needed) {
    if (needed <= s->capacityJugadores) return;
    int newcap = s->capacityJugadores ? s->capacityJugadores * 2 : 10;
    while (newcap < needed) newcap *= 2;
    s->jugadores = realloc(s->jugadores, sizeof(Player) * newcap);
    s->capacityJugadores = newcap;
}

static void ensure_enemy_capacity(GameState *s, int needed) {
    if (needed <= s->capacityEnemigos) return;
    int newcap = s->capacityEnemigos ? s->capacityEnemigos * 2 : 10;
    while (newcap < needed) newcap *= 2;
    s->enemigos = realloc(s->enemigos, sizeof(Enemy) * newcap);
    s->capacityEnemigos = newcap;
}

static void ensure_fruit_capacity(GameState *s, int needed) {
    if (needed <= s->capacityFrutas) return;
    int newcap = s->capacityFrutas ? s->capacityFrutas * 2 : 10;
    while (newcap < needed) newcap *= 2;
    s->frutas = realloc(s->frutas, sizeof(Fruit) * newcap);
    s->capacityFrutas = newcap;
}

// -------------------------
// Parse JSON Paquete del servidor
// -------------------------
static void parse_paquete_json(const char *jsonText) {
    if (!jsonText) return;
    
    cJSON *root = cJSON_Parse(jsonText);
    if (!root) {
        printf("[parse] Error parseando JSON\n");
        return;
    }
    
    // Obtener tipo de paquete
    cJSON *tipo = cJSON_GetObjectItem(root, "tipo");
    if (!tipo || !cJSON_IsString(tipo)) {
        cJSON_Delete(root);
        return;
    }
    
    const char *tipoStr = tipo->valuestring;
    // Solo log de tipos importantes
    if (strcmp(tipoStr, "BIENVENIDA") == 0 || 
        strcmp(tipoStr, "DESCONEXION") == 0 ||
        strcmp(tipoStr, "ERROR") == 0) {
        printf("[RECV] %s\n", tipoStr);
    }
    
    // Procesar según el tipo
    if (strcmp(tipoStr, "BIENVENIDA") == 0) {
        cJSON *datos = cJSON_GetObjectItem(root, "datos");
        if (datos && cJSON_IsString(datos)) {
            printf("[SERVER] %s\n", datos->valuestring);
            
            const char *msg = datos->valuestring;

            // ===== Evento asignado =====
            if (strstr(msg, "JUEGO_1")) {
                strcpy(g_eventoAsignado, "JUEGO_1");
            } else if (strstr(msg, "JUEGO_2")) {
                strcpy(g_eventoAsignado, "JUEGO_2");
            }
            printf("[INFO] Asignado a: %s\n", g_eventoAsignado);

            // ===== Nombre de jugador asignado por el servidor =====
            // Mensaje tipo: "Bienvenido Jugador1 al JUEGO_1"
            const char *inicio = strstr(msg, "Bienvenido ");
            if (inicio) {
                inicio += strlen("Bienvenido ");  // saltar "Bienvenido "
                const char *fin = strstr(inicio, " al ");
                size_t len = fin ? (size_t)(fin - inicio) : strlen(inicio);

                if (len >= sizeof(g_playerName)) {
                    len = sizeof(g_playerName) - 1;
                }

                memcpy(g_playerName, inicio, len);
                g_playerName[len] = '\0';

                printf("[INFO] Nombre jugador asignado por server: %s\n", g_playerName);
            }
            // Si el servidor envía una confirmación de espectador, parsear objetivo
            if (strstr(msg, "ESPECTADOR")) {
                // Buscar "mirando a <name>"
                const char *m = strstr(msg, "mirando a ");
                if (m) {
                    m += strlen("mirando a ");
                    // leer hasta espacio o fin
                    char tmp[64] = {0};
                    int i = 0;
                    while (*m && *m != ' ' && i < (int)sizeof(tmp)-1) {
                        tmp[i++] = *m++;
                    }
                    tmp[i] = '\0';
                    if (i > 0) {
                        strncpy(g_spectatorTarget, tmp, sizeof(g_spectatorTarget)-1);
                        g_spectatorTarget[sizeof(g_spectatorTarget)-1] = '\0';
                        g_isSpectator = true;
                        printf("[INFO] Espectador objetivo: %s\n", g_spectatorTarget);
                    }
                } else {
                    // Sin objetivo aún
                    g_spectatorTarget[0] = '\0';
                    g_isSpectator = true;
                }
            }
        }
    }
    else if (strcmp(tipoStr, "MOVIMIENTO") == 0) {
        // Otro jugador se movió
        cJSON *pname = cJSON_GetObjectItem(root, "playerName");
        cJSON *x = cJSON_GetObjectItem(root, "x");
        cJSON *y = cJSON_GetObjectItem(root, "y");
        // cJSON *mov = cJSON_GetObjectItem(root, "movimiento"); // No usado por ahora
        
        if (pname && x && y) {
            printf("[MOVIMIENTO] %s -> (%.1f, %.1f)\n", 
                   pname->valuestring, x->valuedouble, y->valuedouble);
        }
    }
    else if (strcmp(tipoStr, "ESTADO_JUEGO") == 0) {
        // Estado completo del juego
        cJSON *datos = cJSON_GetObjectItem(root, "datos");
        if (datos && cJSON_IsString(datos)) {
            // datos contiene el GameState serializado
            parse_game_state_json(datos->valuestring);
        }
    }
    else if (strcmp(tipoStr, "CREAR_ENEMIGO") == 0) {
        cJSON *enemyTipo = cJSON_GetObjectItem(root, "enemyTipo");
        cJSON *x = cJSON_GetObjectItem(root, "x");
        cJSON *y = cJSON_GetObjectItem(root, "y");
        
        if (enemyTipo && x && y) {
            printf("[CREAR_ENEMIGO] %s en (%.1f, %.1f)\n",
                   enemyTipo->valuestring, x->valuedouble, y->valuedouble);
        }
    }
    else if (strcmp(tipoStr, "DESCONEXION") == 0) {
        cJSON *pname = cJSON_GetObjectItem(root, "playerName");
        if (pname) {
            printf("[DESCONEXION] %s se desconectó\n", pname->valuestring);
        }
    }
    else if (strcmp(tipoStr, "ERROR") == 0) {
        cJSON *datos = cJSON_GetObjectItem(root, "datos");
        if (datos && cJSON_IsString(datos)) {
            printf("[ERROR SERVER] %s\n", datos->valuestring);
        }
    }
    
    cJSON_Delete(root);
}

// -------------------------
// Parse GameState JSON (dentro del campo "datos" del Paquete)
// -------------------------
static void parse_game_state_json(const char *jsonText) {
    if (!jsonText) return;
    
    cJSON *root = cJSON_Parse(jsonText);
    if (!root) {
        printf("[parse_state] Error parseando GameState JSON\n");
        return;
    }
    
    pthread_mutex_lock(&g_state.mutex);
    
    // Evento
    cJSON *evento = cJSON_GetObjectItem(root, "evento");
    if (evento && cJSON_IsString(evento)) {
        strncpy(g_state.evento, evento->valuestring, sizeof(g_state.evento) - 1);
    }
    
    // Timestamp
    cJSON *timestamp = cJSON_GetObjectItem(root, "timestamp");
    if (timestamp && cJSON_IsNumber(timestamp)) {
        g_state.timestamp = (long)timestamp->valuedouble;
    }
    
    // Jugadores (Map<String, Paquete>)
    cJSON *jugadores = cJSON_GetObjectItem(root, "jugadores");
    if (jugadores && cJSON_IsObject(jugadores)) {
        int count = 0;
        cJSON *jugador = NULL;
        
        // Contar jugadores
        cJSON_ArrayForEach(jugador, jugadores) {
            count++;
        }
        
        ensure_player_capacity(&g_state, count);
        g_state.totalJugadores = 0;
        
        // Parsear cada jugador
        cJSON_ArrayForEach(jugador, jugadores) {
            const char *name = jugador->string; // key del map
            
            // Obtener datos del Paquete
            cJSON *x = cJSON_GetObjectItem(jugador, "x");
            cJSON *y = cJSON_GetObjectItem(jugador, "y");
            cJSON *vida   = cJSON_GetObjectItem(jugador, "vida");
            cJSON *puntos = cJSON_GetObjectItem(jugador, "puntos");
            cJSON *trepando = cJSON_GetObjectItem(jugador, "trepando");
            cJSON *lianaActual = cJSON_GetObjectItem(jugador, "lianaActual");
            cJSON *estadoMov = cJSON_GetObjectItem(jugador, "estadoMovimiento");
            
            if (x && y) {
                Player *p = &g_state.jugadores[g_state.totalJugadores++];
                memset(p, 0, sizeof(Player));
                
                strncpy(p->playerName, name, sizeof(p->playerName) - 1);
                p->x = (float)x->valuedouble;
                p->y = (float)y->valuedouble;
                
                p->vida   = (vida   && cJSON_IsNumber(vida))   ? vida->valueint   : 3;
                p->puntos = (puntos && cJSON_IsNumber(puntos)) ? puntos->valueint : 0;

                // parsear estado de trepar
                p->trepando = (trepando && cJSON_IsBool(trepando)) ? cJSON_IsTrue(trepando) : false;
                p->lianaActual = (lianaActual && cJSON_IsNumber(lianaActual)) ? lianaActual->valueint : -1;
                
                if (estadoMov && cJSON_IsString(estadoMov)) {
                    strncpy(p->estadoMovimiento, estadoMov->valuestring, sizeof(p->estadoMovimiento) - 1);
                } else {
                    strcpy(p->estadoMovimiento, "CAMINANDO");
                }
            }
        }
        
        printf("[STATE] Jugadores: %d en %s\n", g_state.totalJugadores, g_state.evento);
    }
    
    // TODO: Parsear enemigos y frutas si el servidor los envía
    // (Por ahora el GameState solo tiene jugadores)

    // Enemigos
    cJSON *enemigos = cJSON_GetObjectItem(root, "enemigos");
    if (enemigos && cJSON_IsArray(enemigos)) {
        int count = cJSON_GetArraySize(enemigos);

        ensure_enemy_capacity(&g_state, count);
        g_state.totalEnemigos = 0;

        for (int i = 0; i < count; i++) {
            cJSON *e = cJSON_GetArrayItem(enemigos, i);
            if (!e || !cJSON_IsObject(e)) continue;

            cJSON *id     = cJSON_GetObjectItem(e, "id");
            cJSON *tipo   = cJSON_GetObjectItem(e, "tipo");
            cJSON *x      = cJSON_GetObjectItem(e, "x");
            cJSON *y      = cJSON_GetObjectItem(e, "y");
            cJSON *vel    = cJSON_GetObjectItem(e, "velocidad");
            cJSON *dir    = cJSON_GetObjectItem(e, "direccion");  

            Enemy *dst = &g_state.enemigos[g_state.totalEnemigos++];
            memset(dst, 0, sizeof(Enemy));

            if (id && cJSON_IsNumber(id)) {
                snprintf(dst->id, sizeof(dst->id), "%d", (int)id->valuedouble);
            } else {
                strcpy(dst->id, "");
            }

            if (tipo && cJSON_IsString(tipo)) {
                strncpy(dst->tipo, tipo->valuestring, sizeof(dst->tipo) - 1);
            }

            dst->x = x ? (float)x->valuedouble : 0.0f;
            dst->y = y ? (float)y->valuedouble : 0.0f;
            dst->velocidad = vel ? (float)vel->valuedouble : 0.0f;

            if (dir && cJSON_IsString(dir)) {               
                strncpy(dst->direccion, dir->valuestring,
                        sizeof(dst->direccion) - 1);
            } else {
                strcpy(dst->direccion, "");
            }
        }
    }

    // Frutas
    cJSON *frutas = cJSON_GetObjectItem(root, "frutas");
    if (frutas && cJSON_IsArray(frutas)) {
        int count = cJSON_GetArraySize(frutas);

        ensure_fruit_capacity(&g_state, count);
        g_state.totalFrutas = 0;

        for (int i = 0; i < count; i++) {
            cJSON *f = cJSON_GetArrayItem(frutas, i);
            if (!f || !cJSON_IsObject(f)) continue;

            cJSON *id        = cJSON_GetObjectItem(f, "id");
            cJSON *x         = cJSON_GetObjectItem(f, "x");
            cJSON *y         = cJSON_GetObjectItem(f, "y");
            cJSON *puntos    = cJSON_GetObjectItem(f, "puntos");
            cJSON *recolecta = cJSON_GetObjectItem(f, "recolectada");
            cJSON *tipo      = cJSON_GetObjectItem(f, "tipo");  

            Fruit *dst = &g_state.frutas[g_state.totalFrutas++];
            memset(dst, 0, sizeof(Fruit));

            if (id && cJSON_IsNumber(id)) {
                snprintf(dst->id, sizeof(dst->id), "%d", (int)id->valuedouble);
            } else {
                strcpy(dst->id, "");
            }

            dst->x = x ? (float)x->valuedouble : 0.0f;
            dst->y = y ? (float)y->valuedouble : 0.0f;
            dst->puntos = puntos ? puntos->valueint : 0;

            if (recolecta && cJSON_IsBool(recolecta)) {
                dst->recolectada = cJSON_IsTrue(recolecta);
            } else {
                dst->recolectada = false;
            }

            if (tipo && cJSON_IsString(tipo)) {              
                strncpy(dst->tipo, tipo->valuestring,
                        sizeof(dst->tipo) - 1);
            } else {
                strcpy(dst->tipo, "");
            }
        }
    }
    
    pthread_mutex_unlock(&g_state.mutex);
    cJSON_Delete(root);
}

// -------------------------
// LIANAS
// -------------------------

// ========================
// FUNCIONES AUXILIARES PARA TREPAR
// ========================

// Detecta si el jugador está cerca de alguna liana
static int detectar_liana_cercana(float x, float y) {
    // Usar tolerancias un poco más generosas y considerar pies/cabeza del jugador
    const float TOLERANCIA_X = 32.0f;  // píxeles de tolerancia horizontal
    const float TOLERANCIA_Y_EXTRA = 32.0f; // margen vertical extra

    // Calcular pies y cabeza del jugador a partir de su centro 'y'
    float halfH = get_player_half_height();
    float feetY = y + halfH;
    float headY  = y - halfH;

    for (int i = 0; i < NUM_LIANAS; i++) {
        const LianaDef *liana = &LIANAS[i];

        // Verificar si está cerca horizontalmente (permitir algo de margen)
        float dx = fabsf(liana->x - x);
        if (dx > TOLERANCIA_X) continue;

        // Verificar si cualquier parte del jugador (pies o cabeza) entra
        // dentro del rango de la liana (con margen)
        float topCheck = liana->yTop - TOLERANCIA_Y_EXTRA;
        float bottomCheck = liana->yBottom + TOLERANCIA_Y_EXTRA;

        if ((feetY >= topCheck && feetY <= bottomCheck) ||
            (headY  >= topCheck && headY  <= bottomCheck) ||
            (headY <= topCheck && feetY >= bottomCheck)) {
            return i;
        }
    }

    return -1; // No está cerca de ninguna liana
}

// Inicia el modo trepar
static void iniciar_trepar(void) {
    int lianaIndex = detectar_liana_cercana(g_playerX, g_playerY);
    
    if (lianaIndex >= 0) {
        g_playerEstado = ESTADO_TREPANDO;
        g_lianaActual = lianaIndex;
        
        // Centrar al jugador en la liana
        g_playerX = LIANAS[lianaIndex].x;
        // Asegurar que la Y quede dentro del rango de la liana
        if (g_playerY < LIANAS[lianaIndex].yTop) g_playerY = LIANAS[lianaIndex].yTop;
        if (g_playerY > LIANAS[lianaIndex].yBottom) g_playerY = LIANAS[lianaIndex].yBottom;

        g_playerVy = 0.0f;
        g_playerGrounded = false;
        
        printf("[TREPAR] Iniciado en liana %d\n", lianaIndex);
        
        // Notificar al servidor
        send_paquete("TREPAR", "TREPAR", g_playerX, g_playerY);
    }
}

// Detiene el modo trepar
static void soltar_liana(void) {
    if (g_playerEstado == ESTADO_TREPANDO) {
        printf("[TREPAR] Soltando liana %d\n", g_lianaActual);
        
        g_playerEstado = ESTADO_CAYENDO;
        g_lianaActual = -1;
        
        // Notificar al servidor
        send_paquete("SOLTAR_LIANA", "QUIETO", g_playerX, g_playerY);
    }
}

// Procesa movimiento mientras trepa
static void procesar_movimiento_trepar(float dt) {
    if (g_playerEstado != ESTADO_TREPANDO || g_lianaActual < 0) {
        return;
    }
    
    const LianaDef *liana = &LIANAS[g_lianaActual];

    // Guardamos la Y anterior para calcular deltaY
    float oldY = g_playerY;
    bool movio = false;
    
    // Movimiento vertical
    if (IsKeyDown(KEY_UP) || IsKeyDown(KEY_W)) {
        g_playerY -= g_velocidadTrepar * dt;
        movio = true;
        
        // Limitar al tope de la liana
        if (g_playerY < liana->yTop) {
            g_playerY = liana->yTop;
        }
    }
    
    if (IsKeyDown(KEY_DOWN) || IsKeyDown(KEY_S)) {
        g_playerY += g_velocidadTrepar * dt;
        movio = true;
        
        // Limitar al fondo de la liana
        if (g_playerY > liana->yBottom) {
            g_playerY = liana->yBottom;
            // Opcional: soltar automáticamente al llegar al fondo
            // soltar_liana();
        }
    }
    
    // Saltar entre lianas (izquierda/derecha)
    bool sentTreparPacket = false;
    if (IsKeyPressed(KEY_LEFT) || IsKeyPressed(KEY_A)) {
        int lianaIzq = buscar_liana_cercana_horizontal(g_lianaActual, -1);
        if (lianaIzq >= 0) {
            g_lianaActual = lianaIzq;
            g_playerX = LIANAS[lianaIzq].x;
            printf("[TREPAR] Saltó a liana %d (izquierda)\n", lianaIzq);
            // Notificar al servidor que cambiamos de liana y sincronizar X/Y
            send_paquete("TREPAR", "TREPAR", g_playerX, g_playerY);
            sentTreparPacket = true;
            movio = true;
        }
    }

    if (IsKeyPressed(KEY_RIGHT) || IsKeyPressed(KEY_D)) {
        int lianaDer = buscar_liana_cercana_horizontal(g_lianaActual, 1);
        if (lianaDer >= 0) {
            g_lianaActual = lianaDer;
            g_playerX = LIANAS[lianaDer].x;
            printf("[TREPAR] Saltó a liana %d (derecha)\n", lianaDer);
            // Notificar al servidor que cambiamos de liana y sincronizar X/Y
            send_paquete("TREPAR", "TREPAR", g_playerX, g_playerY);
            sentTreparPacket = true;
            movio = true;
        }
    }
    
    // Soltar con SPACE
    if (IsKeyPressed(KEY_SPACE)) {
        soltar_liana();
        return;
    }
    
    // Enviar actualización SOLO si se movió
    if (movio) {
        float deltaY = g_playerY - oldY;   // <- ESTO es lo que espera el servidor
        send_paquete("MOVER_EN_LIANA", "TREPAR", g_playerX, deltaY);
    }
}


// Busca liana cercana en dirección horizontal
static int buscar_liana_cercana_horizontal(int lianaActual, int direccion) {
    const float MAX_DISTANCIA = 100.0f;  // píxeles máximos para saltar
    const float TOLERANCIA_Y = 80.0f;     // tolerancia vertical
    
    const LianaDef *origen = &LIANAS[lianaActual];
    float mejorDistancia = MAX_DISTANCIA + 1.0f;
    int mejorLiana = -1;
    
    for (int i = 0; i < NUM_LIANAS; i++) {
        if (i == lianaActual) continue;
        
        const LianaDef *candidata = &LIANAS[i];
        
        // Verificar dirección
        float dx = candidata->x - origen->x;
        if ((direccion < 0 && dx >= 0) || (direccion > 0 && dx <= 0)) {
            continue; // No está en la dirección correcta
        }
        
        // Verificar altura compatible
        if (g_playerY < candidata->yTop - TOLERANCIA_Y || 
            g_playerY > candidata->yBottom + TOLERANCIA_Y) {
            continue; // No está a una altura alcanzable
        }
        
        // Calcular distancia
        float distancia = fabsf(dx);
        if (distancia < mejorDistancia) {
            mejorDistancia = distancia;
            mejorLiana = i;
        }
    }
    
    return mejorLiana;
}

// -------------------------
// Enviar Paquete al servidor (thread-safe)
// -------------------------
static int send_paquete(const char *tipo, const char *movimiento, float x, float y) {
    cJSON *paquete = cJSON_CreateObject();
    
    cJSON_AddStringToObject(paquete, "tipo", tipo);
    cJSON_AddStringToObject(paquete, "playerName", g_playerName);
    
    if (movimiento) {
        cJSON_AddStringToObject(paquete, "movimiento", movimiento);
    }
    
    cJSON_AddNumberToObject(paquete, "x", x);
    cJSON_AddNumberToObject(paquete, "y", y);
    cJSON_AddNumberToObject(paquete, "timestamp", (double)time(NULL) * 1000);

    // Campos especiales según el tipo de paquete
    if (strcmp(tipo, "CREAR_ENEMIGO") == 0) {
        // Por ahora siempre creamos cocodrilo rojo
        cJSON_AddStringToObject(paquete, "enemyTipo", "CROC_RED");
    } else if (strcmp(tipo, "CREAR_FRUTA") == 0) {
        // Puntos fijos de ejemplo
        cJSON_AddNumberToObject(paquete, "puntos", 50);
    }
    
    char *jsonStr = cJSON_PrintUnformatted(paquete);
    if (!jsonStr) {
        cJSON_Delete(paquete);
        return -1;
    }
    
    // Solo log de tipos importantes
    if (strcmp(tipo, "CREAR_ENEMIGO") == 0 || strcmp(tipo, "CREAR_FRUTA") == 0) {
        printf("[SEND] %s\n", tipo);
    }
    
    // Enviar con DataOutputStream compatible (UTF modificado de Java)
    // Java usa writeUTF que prefija con 2 bytes de longitud
    size_t len = strlen(jsonStr);
    uint16_t len_network = htons((uint16_t)len);
    
    pthread_mutex_lock(&g_send_mutex);
    
    // Enviar longitud (2 bytes)
    ssize_t w1 = send(g_sock, &len_network, 2, 0);
    // Enviar JSON
    ssize_t w2 = send(g_sock, jsonStr, len, 0);
    
    pthread_mutex_unlock(&g_send_mutex);
    
    free(jsonStr);
    cJSON_Delete(paquete);
    
    if (w1 < 0 || w2 < 0) {
        perror("[send] Error enviando paquete");
        return -1;
    }
    
    return 0;
}

// -------------------------
// Thread de red: recibir paquetes del servidor
// -------------------------
static void *network_thread(void *arg) {
    int sock = *((int*)arg);
    
    while (g_running) {
        // Leer longitud (2 bytes) - writeUTF de Java
        uint16_t len_network;
        ssize_t n = recv(sock, &len_network, 2, MSG_WAITALL);
        
        if (n == 2) {
            uint16_t len = ntohs(len_network);
            
            if (len > 0) {
                char *buffer = malloc(len + 1);
                
                // Leer JSON completo
                ssize_t total = 0;
                while (total < len) {
                    ssize_t r = recv(sock, buffer + total, len - total, 0);
                    if (r <= 0) break;
                    total += r;
                }
                
                if (total == len) {
                    buffer[len] = '\0';
                    
                    // Parsear Paquete JSON
                    parse_paquete_json(buffer);
                }
                
                free(buffer);
            }
        } else if (n == 0) {
            printf("[network] Servidor cerró la conexión\n");
            g_running = false;
            break;
        } else {
            if (errno == EINTR) continue;
            perror("[network] recv");
            g_running = false;
            break;
        }
    }
    
    return NULL;
}

// Calcula tamaño "de pies" del DK Jr basado en la textura
static float get_player_half_height(void) {
    if (g_playerTex.id != 0) {
        float h = (float)g_playerTex.height * PLAYER_SCALE;
        return h * 0.5f;
    }
    return 24.0f; // valor por defecto
}

// Colocar al jugador sobre la plataforma 0 al inicio
static void init_player_start_position(void) {
    if (g_playerInitialized) return;

    const PlataformaDef *floor = &PLATAFORMAS[0];

    float halfH = get_player_half_height();

    g_playerX = (floor->xLeft + floor->xRight) * 0.5f;  // centro de la plataforma
    g_playerY = floor->y - halfH;                       // pies justo sobre la plataforma
    g_playerVy = 0.0f;
    g_playerGrounded = true;
    g_playerInitialized = true;

    // Mandar posición inicial al servidor (solo si no somos espectador)
    if (!g_isSpectator) {
        send_paquete("MOVIMIENTO", "QUIETO", g_playerX, g_playerY);
    }
}

// Fuerza respawn del jugador en la plataforma 0 (útil cuando cae al vacío)
static void respawn_player(void) {
    const PlataformaDef *floor = &PLATAFORMAS[0];
    float halfH = get_player_half_height();

    g_playerX = (floor->xLeft + floor->xRight) * 0.5f;
    g_playerY = floor->y - halfH;
    g_playerVy = 0.0f;
    g_playerGrounded = true;
    g_playerEstado = ESTADO_CAMINANDO;
    g_lianaActual = -1;

    // Asegurar que el cliente know it's initialized
    g_playerInitialized = true;

    printf("[RESPAWN] Jugador reubicado a (%.1f, %.1f)\n", g_playerX, g_playerY);
    if (!g_isSpectator) {
        send_paquete("RESPAWN", "QUIETO", g_playerX, g_playerY);
    }
}

// Resolver colisión con todas las plataformas
static void resolver_colision_plataformas(void) {
    if (!g_playerInitialized) return;

    float halfH = get_player_half_height();
    float feetY = g_playerY + halfH;
    float prevFeetY = g_playerPrevY + halfH;

    g_playerGrounded = false;
    const float tolerancia = 6.0f;  // rango para "aterrizar" en la plataforma

    for (int i = 0; i < NUM_PLATAFORMAS; i++) {
        const PlataformaDef *p = &PLATAFORMAS[i];

        // ¿Estamos horizontalmente sobre la plataforma?
        if (g_playerX < p->xLeft || g_playerX > p->xRight) {
            continue;
        }

        float platY = p->y;

        // Detección robusta: si en el frame anterior estábamos por encima
        // de la plataforma y ahora hemos cruzado su Y (posible 'tunneling'),
        // o si estamos dentro de la tolerancia actualmente.
        bool crossed = (prevFeetY <= platY) && (feetY >= platY - tolerancia);
        bool inside  = (feetY >= platY - tolerancia) && (feetY <= platY + tolerancia);

        // Solo aplicar corrección cuando estamos cayendo (vy >= 0).
        // Evita que al iniciar un salto (vy < 0) el jugador sea "snappeado"
        // a la plataforma por la comprobación 'inside'.
        if ((g_playerVy >= 0.0f) && (crossed || inside)) {
            // Ajustar al jugador para que quede "parado" justo sobre la plataforma
            g_playerY = platY - halfH;
            g_playerVy = 0.0f;
            g_playerGrounded = true;
            break;
        }
    }
}

// -------------------------
// Enviar input del teclado + física del jugador
// -------------------------
static void send_input_from_keys(void) {
    // Si somos espectador no enviamos inputs al servidor (solo visualizamos)
    if (g_isSpectator) {
        // Aún permitimos toggles locales de depuración
        if (IsKeyPressed(KEY_T)) {
            g_showLayoutDebug = !g_showLayoutDebug;
            printf("[DEBUG] g_showLayoutDebug = %d\n", g_showLayoutDebug);
        }
        return;
    }
    // Asegurar posición inicial
    init_player_start_position();

    // Guardar Y anterior para detección continua de colisiones
    g_playerPrevY = g_playerY;

    float dt = GetFrameTime();
    if (dt <= 0.0f) dt = 1.0f / 60.0f;

    // ===== MODO TREPAR =====
    if (g_playerEstado == ESTADO_TREPANDO) {
        procesar_movimiento_trepar(dt);
        return; // No procesar física normal
    }

    // Atajo: F12 lanza una nueva instancia espectador que duplica nuestra vista
    if (IsKeyPressed(KEY_F12) && !g_isSpectator) {
        // Solo si tenemos un nombre de jugador válido (Jugador1/Jugador2)
        if (strncmp(g_playerName, "Jugador", 7) == 0) {
            launch_spectator_instance(g_playerName);
        } else {
            printf("[LAUNCH] Nombre de jugador no válido para espejo: %s\n", g_playerName);
        }
    }

    // --- Atajos / debug: teclas P y T ---
    if (IsKeyPressed(KEY_P)) {
        // Teleport de prueba a la liana 0
        if (NUM_LIANAS > 0) {
            g_playerX = LIANAS[0].x;
            g_playerY = LIANAS[0].yBottom;
            g_playerVy = 0.0f;
            g_playerGrounded = false;
            printf("[DEBUG] Teleport a L0 -> (%.1f, %.1f)\n", g_playerX, g_playerY);
        }
    }

    if (IsKeyPressed(KEY_T)) {
        g_showLayoutDebug = !g_showLayoutDebug;
        printf("[DEBUG] g_showLayoutDebug = %d\n", g_showLayoutDebug);
    }

    // Log básico de detección de teclas (solo cuando se presionan)
    if (IsKeyPressed(KEY_UP) || IsKeyPressed(KEY_W)) {
        printf("[INPUT] Tecla ARRIBA detectada\n");
    }
    if (IsKeyPressed(KEY_SPACE)) {
        printf("[INPUT] Tecla ESPACIO detectada\n");
    }

    // ===== MODO NORMAL (CAMINANDO/CAYENDO) =====
    
    // Intentar trepar con tecla arriba (permitir mantener la tecla presionada)
    if (IsKeyDown(KEY_UP) || IsKeyDown(KEY_W)) {
        int lianaCercana = detectar_liana_cercana(g_playerX, g_playerY);
        if (lianaCercana >= 0) {
            iniciar_trepar();
            return; // Cambió a modo trepar
        }
    }

    float dx = 0.0f;
    const char *movimiento = "QUIETO";

    // Movimiento lateral
    if (IsKeyDown(KEY_RIGHT) || IsKeyDown(KEY_D)) {
        dx += MOVE_SPEED * dt;
        movimiento = "DERECHA";
    }
    if (IsKeyDown(KEY_LEFT) || IsKeyDown(KEY_A)) {
        dx -= MOVE_SPEED * dt;
        movimiento = "IZQUIERDA";
    }

    // Salto (ESPACIO o W/ARRIBA) solo si está en el suelo
    if ((IsKeyPressed(KEY_SPACE) || IsKeyPressed(KEY_W) || IsKeyPressed(KEY_UP))) {
        // Log de depuración: estado del suelo
        printf("[INPUT] SALTO detectado - grounded=%d, vy=%.2f\n", (int)g_playerGrounded, g_playerVy);
        if (g_playerGrounded) {
            g_playerVy = JUMP_SPEED;
            g_playerGrounded = false;
            g_playerEstado = ESTADO_SALTANDO;
            movimiento = "ARRIBA";
            printf("[ACTION] Saltando -> vy=%.2f\n", g_playerVy);
        } else {
            // Si no está grounded, ignorar salto pero lo informamos
            printf("[ACTION] Intento de salto ignorado (no grounded)\n");
        }
    }

    // Física vertical: gravedad
    g_playerVy += GRAVITY * dt;
    g_playerY  += g_playerVy * dt;

    // Movimiento horizontal
    g_playerX += dx;

    // Limitar a la pantalla
    if (g_playerX < 0) g_playerX = 0;
    if (g_playerX > SCREEN_WIDTH) g_playerX = SCREEN_WIDTH;

    // Colisión con plataformas
    resolver_colision_plataformas();
    
    // Actualizar estado
    if (g_playerGrounded) {
        g_playerEstado = ESTADO_CAMINANDO;
    } else if (g_playerVy > 0) {
        g_playerEstado = ESTADO_CAYENDO;
    }

    // Respawn si cae fuera de límites (p. ej. cae al vacío)
    // Si la Y del centro del jugador supera la pantalla por bastante, respawnear
    if (g_playerY > (float)SCREEN_HEIGHT + 200.0f) {
        respawn_player();
    }


    // Enviar al servidor solo si cambió
    static float lastX = 0.0f;
    static float lastY = 0.0f;
    static bool firstSend = true;

    if (firstSend ||
        fabsf(g_playerX - lastX) > 0.1f ||
        fabsf(g_playerY - lastY) > 0.1f) {

        send_paquete("MOVIMIENTO", movimiento, g_playerX, g_playerY);
        lastX = g_playerX;
        lastY = g_playerY;
        firstSend = false;
    }
}


// -------------------------
// Renderizar estado del juego
// -------------------------
static void render_game(Texture2D stageTex) {
    BeginDrawing();
    ClearBackground(RAYWHITE);
    
    // Render del stage: si estamos en modo espejo (espectador lanzado con
    // --mirror), dibujamos la ventana centrada en el jugador objetivo;
    // en caso contrario, dibujamos el stage completo escalado a la pantalla.
    Rectangle srcStage = { 0, 0, 0, 0 };
    if (g_spectatorMirrorMode && g_spectatorTarget[0] != '\0') {
        float camCenterX = g_playerX;
        float camCenterY = g_playerY;
        // Buscar la posición del objetivo en el estado recibido
        pthread_mutex_lock(&g_state.mutex);
        for (int i = 0; i < g_state.totalJugadores; i++) {
            Player *pt = &g_state.jugadores[i];
            if (strcmp(pt->playerName, g_spectatorTarget) == 0) {
                camCenterX = pt->x;
                camCenterY = pt->y;
                break;
            }
        }
        pthread_mutex_unlock(&g_state.mutex);

        if (stageTex.id != 0) {
            float texW = (float)stageTex.width;
            float texH = (float)stageTex.height;
            float camX = camCenterX - ((float)SCREEN_WIDTH * 0.5f);
            float camY = camCenterY - ((float)SCREEN_HEIGHT * 0.5f);

            // Asegurarnos de no pedir una región fuera del tamaño de la textura.
            float viewW = SCREEN_WIDTH;
            float viewH = SCREEN_HEIGHT;
            if (viewW > texW) viewW = texW;
            if (viewH > texH) viewH = texH;

            if (camX < 0) camX = 0;
            if (camY < 0) camY = 0;
            if (camX + viewW > texW) camX = texW - viewW;
            if (camY + viewH > texH) camY = texH - viewH;
            if (camX < 0) camX = 0;
            if (camY < 0) camY = 0;

            // Usar coordenadas enteras en el source rect para evitar artefactos
            srcStage.x = (float)((int)camX);
            srcStage.y = (float)((int)camY);
            srcStage.width  = (float)((int)viewW);
            srcStage.height = (float)((int)viewH);

            Rectangle dst = {0, 0, (float)SCREEN_WIDTH, (float)SCREEN_HEIGHT};
            DrawTexturePro(stageTex, srcStage, dst, (Vector2){0,0}, 0.0f, WHITE);
        }
    } else {
        if (stageTex.id != 0) {
            srcStage.x = 0;
            srcStage.y = 0;
            srcStage.width  = (float)stageTex.width;
            srcStage.height = (float)stageTex.height;

            Rectangle dst = {0, 0, (float)SCREEN_WIDTH, (float)SCREEN_HEIGHT};
            DrawTexturePro(stageTex, srcStage, dst, (Vector2){0,0}, 0.0f, WHITE);
        }
    }

    // Variables de HUD (vidas/puntos del jugador local)
    int vidaLocal = -1;
    int puntosLocal = 0;

    // Indicador visual cuando está trepando
    if (g_playerEstado == ESTADO_TREPANDO && g_lianaActual >= 0) {
        const LianaDef *liana = &LIANAS[g_lianaActual];
        
        // Resaltar la liana actual
        DrawLine((int)liana->x, (int)liana->yTop, 
                (int)liana->x, (int)liana->yBottom,
                Fade(YELLOW, 0.8f));
        
        // Mostrar indicador
        DrawText("TREPANDO", 10, 110, 20, YELLOW);
        DrawText(TextFormat("Liana: %d", g_lianaActual), 10, 135, 18, YELLOW);
    }

    // Mostrar estado actual
    const char *estadoTexto = "";
    Color estadoColor = WHITE;
    switch (g_playerEstado) {
        case ESTADO_CAMINANDO:
            estadoTexto = "CAMINANDO";
            estadoColor = GREEN;
            break;
        case ESTADO_TREPANDO:
            estadoTexto = "TREPANDO";
            estadoColor = YELLOW;
            break;
        case ESTADO_CAYENDO:
            estadoTexto = "CAYENDO";
            estadoColor = ORANGE;
            break;
        case ESTADO_SALTANDO:
            estadoTexto = "SALTANDO";
            estadoColor = SKYBLUE;
            break;
    }
    DrawText(TextFormat("Estado: %s", estadoTexto), 10, 160, 18, estadoColor);
    
    pthread_mutex_lock(&g_state.mutex);
    
    // Offset de cámara (copiado de srcStage)
    float camOffsetX = 0.0f;
    float camOffsetY = 0.0f;
    if (stageTex.id != 0) {
        camOffsetX = srcStage.x;
        camOffsetY = srcStage.y;
    }

    // Dibujar todos los jugadores
    for (int i = 0; i < g_state.totalJugadores; i++) {
        Player *p = &g_state.jugadores[i];
        
        // ===== JUGADOR LOCAL (YO) =====
        if (strcmp(p->playerName, g_playerName) == 0 && g_playerTex.id != 0) {

            // HUD local: vidas y puntos desde el SERVIDOR
            vidaLocal   = p->vida;
            puntosLocal = p->puntos;

            // ==== SINCRONIZACIÓN SERVER → CLIENTE (suave) ====
            float serverX = p->x;
            float serverY = p->y;

            float dx = serverX - g_playerX;
            float dy = serverY - g_playerY;
            float distTotal = sqrtf(dx*dx + dy*dy);

            // No queremos que el servidor nos aplaste mientras TREPAMOS
            bool estoyTrepandoLocal = (g_playerEstado == ESTADO_TREPANDO);

            // Solo corregir en dos casos:
            //  1) Primer frame (no inicializado)
            //  2) Desfase MUY grande (típico de respawn / caída al vacío)
            if (!estoyTrepandoLocal && 
                (!g_playerInitialized || distTotal > 80.0f)) {

                printf("[SYNC] Corrigiendo posición local desde el servidor: (%.1f, %.1f) (dist=%.1f)\n",
                       serverX, serverY, distTotal);

                g_playerX = serverX;
                g_playerY = serverY;
                g_playerVy = 0.0f;
                g_playerGrounded = true;
                g_playerEstado = ESTADO_CAMINANDO;
                g_lianaActual = -1;
                g_playerInitialized = true;
            }

            // ==== DIBUJAR JUGADOR LOCAL USANDO POSICIÓN LOCAL ====
            Rectangle src = (Rectangle){ 0, 0, (float)g_playerTex.width, (float)g_playerTex.height };

            float w = g_playerTex.width  * PLAYER_SCALE;
            float h = g_playerTex.height * PLAYER_SCALE;

            float drawX = g_playerX - camOffsetX;
            float drawY = g_playerY - camOffsetY;

            Rectangle dst = (Rectangle){ drawX, drawY, w, h };
            Vector2 origin = (Vector2){ w / 2.0f, h / 2.0f };

            DrawTexturePro(g_playerTex, src, dst, origin, 0.0f, WHITE);

            // Nombre del jugador local
            DrawText(p->playerName, (int)drawX - 20, (int)drawY - 30, 10, BLACK);

        } else {
            float screenX = p->x - camOffsetX;
            float screenY = p->y - camOffsetY;
            // ===== OTROS JUGADORES =====
            Color color = GREEN;
            
            // Si está trepando, dibujar indicador especial
            if (p->trepando) {
                if (p->lianaActual >= 0 && p->lianaActual < NUM_LIANAS) {
                    const LianaDef *liana = &LIANAS[p->lianaActual];
                    DrawLine((int)(liana->x - camOffsetX), (int)(liana->yTop - camOffsetY),
                            (int)(liana->x - camOffsetX), (int)(liana->yBottom - camOffsetY),
                            Fade(YELLOW, 0.3f));
                }
                color = YELLOW;
                DrawText("T", (int)screenX - 3, (int)screenY - 40, 16, YELLOW);
            }
            
            DrawCircle((int)screenX, (int)screenY, 15, color);

            // Nombre de los otros jugadores
            DrawText(p->playerName, (int)screenX - 20, (int)screenY - 30, 10, BLACK);
        }
    }

    
    // Dibujar enemigos
    for (int i = 0; i < g_state.totalEnemigos; i++) {
        Enemy *e = &g_state.enemigos[i];

        Texture2D tex = (Texture2D){0};

        bool isRed  = (strncmp(e->tipo, "CROC_RED", 8)  == 0);
        bool isBlue = (strncmp(e->tipo, "CROC_BLUE", 9) == 0);
        const char *dir = e->direccion;

        if (isRed) {
            if (strcmp(dir, "UP") == 0 && g_texCrocRedUp.id)
                tex = g_texCrocRedUp;
            else if (strcmp(dir, "DOWN") == 0 && g_texCrocRedDown.id)
                tex = g_texCrocRedDown;
            else if (strcmp(dir, "LEFT") == 0 && g_texCrocRedLeft.id)
                tex = g_texCrocRedLeft;
            else if (strcmp(dir, "RIGHT") == 0 && g_texCrocRedRight.id)
                tex = g_texCrocRedRight;
        } else if (isBlue) {
            if (strcmp(dir, "UP") == 0 && g_texCrocBlueUp.id)
                tex = g_texCrocBlueUp;
            else if (strcmp(dir, "DOWN") == 0 && g_texCrocBlueDown.id)
                tex = g_texCrocBlueDown;
            else if (strcmp(dir, "LEFT") == 0 && g_texCrocBlueLeft.id)
                tex = g_texCrocBlueLeft;
            else if (strcmp(dir, "RIGHT") == 0 && g_texCrocBlueRight.id)
                tex = g_texCrocBlueRight;
        }

        if (tex.id != 0) {
            Rectangle src = { 0, 0, (float)tex.width, (float)tex.height };

            float texW = (float)tex.width;
            float texH = (float)tex.height;

            float mayor = (texW > texH) ? texW : texH;

            float targetSize = ENEMY_TARGET_SIZE_RED; // por defecto rojo
            if (isBlue) {
                targetSize = ENEMY_TARGET_SIZE_BLUE;
            }

            float scale = targetSize / mayor;

            float w = texW * scale;
            float h = texH * scale;

            Rectangle dst = (Rectangle){ e->x - camOffsetX, e->y - camOffsetY, w, h };
            Vector2 origin = (Vector2){ w / 2.0f, h / 2.0f };

            DrawTexturePro(tex, src, dst, origin, 0.0f, WHITE);
        } else {
            DrawCircle((int)(e->x - camOffsetX), (int)(e->y - camOffsetY), 10, RED);
        }
    }
    
    // Dibujar frutas
    for (int i = 0; i < g_state.totalFrutas; i++) {
        Fruit *f = &g_state.frutas[i];

        if (f->recolectada) continue;

        Texture2D tex = (Texture2D){0};

        if (strcmp(f->tipo, "MANGO") == 0 && g_texMango.id) {
            tex = g_texMango;
        } else if (strcmp(f->tipo, "BANANO") == 0 && g_texBanano.id) {
            tex = g_texBanano;
        } else if (strcmp(f->tipo, "MANZANA") == 0 && g_texManzana.id) {
            tex = g_texManzana;
        }

        if (tex.id != 0) {
            Rectangle src = { 0, 0, (float)tex.width, (float)tex.height };

            float w = tex.width  * FRUIT_SCALE;
            float h = tex.height * FRUIT_SCALE;

            Rectangle dst = { f->x - camOffsetX, f->y - camOffsetY, w, h };
            Vector2 origin = { w / 2.0f, h / 2.0f };

            DrawTexturePro(tex, src, dst, origin, 0.0f, WHITE);
        } else {
            DrawCircle((int)(f->x - camOffsetX), (int)(f->y - camOffsetY), 8, YELLOW);
        }
    }
    
    // Dibujar layout debug si está activado
    if (g_showLayoutDebug) {
        // Dibujar PLATAFORMAS como segmentos horizontales
        for (int i = 0; i < NUM_PLATAFORMAS; i++) {
            const PlataformaDef *p = &PLATAFORMAS[i];

            DrawLine((int)(p->xLeft - camOffsetX), (int)(p->y - camOffsetY), (int)(p->xRight - camOffsetX), (int)(p->y - camOffsetY),
                     Fade(RED, 0.7f));

            float midX = (p->xLeft + p->xRight) * 0.5f;
            DrawText(TextFormat("P%d", i), (int)midX - 10 - (int)camOffsetX, (int)p->y - 15 - (int)camOffsetY, 14, RED);
        }

        // Dibujar LIANAS como segmentos verticales
        for (int j = 0; j < NUM_LIANAS; j++) {
            const LianaDef *l = &LIANAS[j];

            DrawLine((int)(l->x - camOffsetX), (int)(l->yTop - camOffsetY), (int)(l->x - camOffsetX), (int)(l->yBottom - camOffsetY),
                     Fade(BLUE, 0.7f));

            DrawText(TextFormat("L%d", j), (int)(l->x - 10 - camOffsetX), (int)(l->yTop - 20 - camOffsetY), 14, BLUE);
        }
    }
    
    pthread_mutex_unlock(&g_state.mutex);
    
    // ===== HUD / UI =====
    DrawText(TextFormat("Evento: %s", g_eventoAsignado), 10, 10, 20, DARKGREEN);
    DrawText(TextFormat("Jugadores: %d", g_state.totalJugadores), 10, 35, 20, DARKGREEN);
    DrawText("Flechas: Mover | E: Enemigo | F: Fruta", 10, SCREEN_HEIGHT - 25, 15, DARKGRAY);

    // Información adicional cuando está trepando
    if (g_playerEstado == ESTADO_TREPANDO) {
        DrawRectangle(5, SCREEN_HEIGHT - 80, 390, 50, Fade(BLACK, 0.7f));
        DrawText("TREPANDO - Arriba/Abajo: Subir/Bajar", 10, SCREEN_HEIGHT - 75, 14, YELLOW);
        DrawText("           Izq/Der: Cambiar liana | ESPACIO: Soltar", 
                10, SCREEN_HEIGHT - 55, 14, YELLOW);
    }

    DrawText(TextFormat("Vidas: %d", (vidaLocal >= 0 ? vidaLocal : 0)),
             10, 60, 20, RED);
    DrawText(TextFormat("Puntos: %d", puntosLocal),
             10, 85, 20, GOLD);
             
    // Grid
    for (int x = 0; x < SCREEN_WIDTH; x += 32)
        DrawLine(x, 0, x, SCREEN_HEIGHT, Fade(GREEN, 0.15f));
    for (int y = 0; y < SCREEN_HEIGHT; y += 32)
        DrawLine(0, y, SCREEN_WIDTH, y, Fade(GREEN, 0.15f));
    
    EndDrawing();
}



// -------------------------
// Main
// -------------------------
int main(int argc, char *argv[]) {
    // Permitir nombre de jugador como argumento
    // Parsear argumentos simples:
    // - Si se pasa solo un argumento, se toma como nombre de jugador.
    // - Si se pasa '--mirror <playerName>' se activa modo espejo y se usa
    //   el segundo parámetro como objetivo (ej. "Jugador1").
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--mirror") == 0 && i + 1 < argc) {
            // activar modo espejo
            g_spectatorMirrorMode = true;
            g_isSpectator = true;
            strncpy(g_spectatorTarget, argv[i+1], sizeof(g_spectatorTarget)-1);
            g_spectatorTarget[sizeof(g_spectatorTarget)-1] = '\0';
            i++; // saltar el parámetro
        } else if (strncmp(argv[i], "--", 2) == 0) {
            // otros flags desconocidos: ignorar
        } else if (strlen(argv[i]) > 0) {
            // si no es flag, tomar como nombre de jugador (por compatibilidad)
            strncpy(g_playerName, argv[i], sizeof(g_playerName) - 1);
        }
    }
    
    printf("╔════════════════════════════════════════════╗\n");
    printf("║   CLIENTE C - Conectando a Servidor Java  ║\n");
    printf("╚════════════════════════════════════════════╝\n");
    printf("Jugador: %s\n\n", g_playerName);
    
    // Guardar ruta del ejecutable para posibles forks (lanzar espectadores)
    strncpy(g_execPath, argv[0], sizeof(g_execPath)-1);
    g_execPath[sizeof(g_execPath)-1] = '\0';

    // Inicializar GameState
    memset(&g_state, 0, sizeof(GameState));
    pthread_mutex_init(&g_state.mutex, NULL);
    
    struct sockaddr_in serv_addr;
    
    // =======================================
    // PASO 1: Crear socket
    // =======================================
    g_sock = socket(AF_INET, SOCK_STREAM, 0);
    if (g_sock < 0) {
        perror("Error al crear socket");
        return -1;
    }
    
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(SERVER_PORT);
    
    if (inet_pton(AF_INET, SERVER_IP, &serv_addr.sin_addr) <= 0) {
        perror("Dirección inválida");
        close(g_sock);
        return 1;
    }
    
    // ===== Selección de rol (Interfaz gráfica previa) =====
    // Iniciamos una ventana temporal (se reutilizará para el juego) y
    // mostramos botones para elegir: Jugador1, Jugador2, Espectador->J1,
    // Espectador->J2.
    InitWindow(SCREEN_WIDTH, SCREEN_HEIGHT, "Cliente C - Selección");
    SetTargetFPS(TARGET_FPS);

    bool chosen = false;
    int menuChoice = 0; // 1=J1,2=J2,3=Spec->J1,4=Spec->J2

    const int btnW = 360;
    const int btnH = 64;
    const int startY = 180;

    while (!chosen && !WindowShouldClose()) {
        BeginDrawing();
        ClearBackground(RAYWHITE);

        DrawText("Selecciona modo:", 20, 40, 30, DARKGREEN);

        Vector2 mouse = GetMousePosition();
        int cx = SCREEN_WIDTH/2 - btnW/2;

        Rectangle r1 = (Rectangle){ cx, startY + 0*(btnH+16), btnW, btnH };
        Rectangle r2 = (Rectangle){ cx, startY + 1*(btnH+16), btnW, btnH };
        Rectangle r3 = (Rectangle){ cx, startY + 2*(btnH+16), btnW, btnH };
        Rectangle r4 = (Rectangle){ cx, startY + 3*(btnH+16), btnW, btnH };

        // Hover
        Color c1 = CheckCollisionPointRec(mouse, r1) ? Fade(DARKBLUE,0.9f) : Fade(SKYBLUE,0.6f);
        Color c2 = CheckCollisionPointRec(mouse, r2) ? Fade(DARKBLUE,0.9f) : Fade(SKYBLUE,0.6f);
        Color c3 = CheckCollisionPointRec(mouse, r3) ? Fade(DARKBLUE,0.9f) : Fade(SKYBLUE,0.6f);
        Color c4 = CheckCollisionPointRec(mouse, r4) ? Fade(DARKBLUE,0.9f) : Fade(SKYBLUE,0.6f);

        DrawRectangleRec(r1, c1);
        DrawRectangleRec(r2, c2);
        DrawRectangleRec(r3, c3);
        DrawRectangleRec(r4, c4);

        DrawText("Jugador 1", (int)(r1.x + 20), (int)(r1.y + 18), 24, WHITE);
        DrawText("Jugador 2", (int)(r2.x + 20), (int)(r2.y + 18), 24, WHITE);
        DrawText("Espectador -> Mirar Jugador1", (int)(r3.x + 20), (int)(r3.y + 18), 24, WHITE);
        DrawText("Espectador -> Mirar Jugador2", (int)(r4.x + 20), (int)(r4.y + 18), 24, WHITE);

        DrawText("Haz click en una opción o pulsa 1-4.", 20, SCREEN_HEIGHT - 40, 16, DARKGRAY);

        // Click handling
        if (IsMouseButtonPressed(MOUSE_BUTTON_LEFT)) {
            if (CheckCollisionPointRec(mouse, r1)) { menuChoice = 1; chosen = true; }
            else if (CheckCollisionPointRec(mouse, r2)) { menuChoice = 2; chosen = true; }
            else if (CheckCollisionPointRec(mouse, r3)) { menuChoice = 3; chosen = true; }
            else if (CheckCollisionPointRec(mouse, r4)) { menuChoice = 4; chosen = true; }
        }
        if (IsKeyPressed(KEY_ONE))  { menuChoice = 1; chosen = true; }
        if (IsKeyPressed(KEY_TWO))  { menuChoice = 2; chosen = true; }
        if (IsKeyPressed(KEY_THREE)){ menuChoice = 3; chosen = true; }
        if (IsKeyPressed(KEY_FOUR)) { menuChoice = 4; chosen = true; }

        EndDrawing();
    }

    // Si cerraron la ventana desde la selección, salir
    if (WindowShouldClose() && !chosen) {
        CloseWindow();
        return 0;
    }

    // Aplicar elección
    if (menuChoice == 1) {
        g_isSpectator = false;
        strncpy(g_playerName, "Jugador1", sizeof(g_playerName)-1);
    } else if (menuChoice == 2) {
        g_isSpectator = false;
        strncpy(g_playerName, "Jugador2", sizeof(g_playerName)-1);
    } else if (menuChoice == 3) {
        g_isSpectator = true;
        strncpy(g_spectatorTarget, "Jugador1", sizeof(g_spectatorTarget)-1);
        g_spectatorMirrorMode = true;
    } else if (menuChoice == 4) {
        g_isSpectator = true;
        strncpy(g_spectatorTarget, "Jugador2", sizeof(g_spectatorTarget)-1);
        g_spectatorMirrorMode = true;
    }

    // Ajustar título de la ventana para el juego
    SetWindowTitle("Cliente C - Java Server");

    // =======================================
    // PASO 2: Conectar
    // =======================================
    printf("Conectando a %s:%d...\n", SERVER_IP, SERVER_PORT);
    
    if (connect(g_sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        perror("Error en connect");
        close(g_sock);
        return 1;
    }
    
    printf("✓ Conectado exitosamente\n\n");
    
    // =======================================
    // PASO 3: Iniciar thread de red
    // =======================================
    pthread_t net_thread;
    if (pthread_create(&net_thread, NULL, network_thread, &g_sock) != 0) {
        perror("pthread_create");
        close(g_sock);
        return 1;
    }

    // Enviar rol al servidor (PLAYER o ESPECTADOR) usando la selección previa
    if (g_isSpectator) {
        int choice = 1;
        if (strncmp(g_spectatorTarget, "Jugador", 7) == 0) {
            // extraer dígito final si existe
            int v = atoi(g_spectatorTarget + 7);
            if (v == 1 || v == 2) choice = v;
        }
        send_paquete("ROLE", "ESPECTADOR", (float)choice, 0.0f);
        printf("Conectado como ESPECTADOR mirando %s\n", g_spectatorTarget[0] ? g_spectatorTarget : "Jugador1");
    } else {
        send_paquete("ROLE", "JUGADOR", 0.0f, 0.0f);
        printf("Conectado como JUGADOR\n");
    }
    
    // Nota: la ventana Raylib ya fue inicializada para la selección previa.
    
    // Texturas locales
    Texture2D playerTex = {0};
    Texture2D stageTex  = {0};

    if (FileExists(PLAYER_TEXTURE_PATH)) {
        playerTex = LoadTexture(PLAYER_TEXTURE_PATH);
    }
    if (FileExists(STAGE_TEXTURE_PATH)) {
        stageTex = LoadTexture(STAGE_TEXTURE_PATH);
    }

    // Copiarlas a las globales para usarlas en render_game
    g_playerTex = playerTex;
    g_stageTex  = stageTex;

    // Cargar frutas (ajusta las rutas a tus archivos reales)
    if (FileExists("assets/mango.png"))   g_texMango   = LoadTexture("assets/mango.png");
    if (FileExists("assets/banano.png"))  g_texBanano  = LoadTexture("assets/banano.png");
    if (FileExists("assets/manzana.png")) g_texManzana = LoadTexture("assets/manzana.png");

    // Cocodrilos rojos
    if (FileExists("assets/r_arr.png"))    g_texCrocRedUp    = LoadTexture("assets/r_arr.png");
    if (FileExists("assets/r_ab.png"))  g_texCrocRedDown  = LoadTexture("assets/r_ab.png");
    if (FileExists("assets/r_izq.png"))  g_texCrocRedLeft  = LoadTexture("assets/r_izq.png");
    if (FileExists("assets/r_der.png")) g_texCrocRedRight = LoadTexture("assets/r_der.png");

    // Cocodrilos azules
    if (FileExists("assets/a_arr.png"))    g_texCrocBlueUp    = LoadTexture("assets/a_arr.png");
    if (FileExists("assets/a_ab.png"))  g_texCrocBlueDown  = LoadTexture("assets/a_ab.png");
    if (FileExists("assets/a_izq.png"))  g_texCrocBlueLeft  = LoadTexture("assets/a_izq.png");
    if (FileExists("assets/a_der.png")) g_texCrocBlueRight = LoadTexture("assets/a_der.png");
    
    // =======================================
    // PASO 5: Loop principal
    // =======================================
    while (!WindowShouldClose() && g_running) {
        // Procesar input y enviar
        send_input_from_keys();
        
        // Renderizar
        render_game(stageTex);
    }
    
    // =======================================
    // PASO 6: Limpieza
    // =======================================
    printf("\nCerrando conexión...\n");
    
    g_running = false;
    shutdown(g_sock, SHUT_RDWR);
    pthread_join(net_thread, NULL);
    
    if (playerTex.id != 0) UnloadTexture(playerTex);
    if (stageTex.id != 0) UnloadTexture(stageTex);
    if (g_texMango.id)        UnloadTexture(g_texMango);
    if (g_texBanano.id)       UnloadTexture(g_texBanano);
    if (g_texManzana.id)      UnloadTexture(g_texManzana);

    if (g_texCrocRedUp.id)    UnloadTexture(g_texCrocRedUp);
    if (g_texCrocRedDown.id)  UnloadTexture(g_texCrocRedDown);
    if (g_texCrocRedLeft.id)  UnloadTexture(g_texCrocRedLeft);
    if (g_texCrocRedRight.id) UnloadTexture(g_texCrocRedRight);

    if (g_texCrocBlueUp.id)    UnloadTexture(g_texCrocBlueUp);
    if (g_texCrocBlueDown.id)  UnloadTexture(g_texCrocBlueDown);
    if (g_texCrocBlueLeft.id)  UnloadTexture(g_texCrocBlueLeft);
    if (g_texCrocBlueRight.id) UnloadTexture(g_texCrocBlueRight);

    CloseWindow();
    
    free(g_state.jugadores);
    free(g_state.enemigos);
    free(g_state.frutas);
    pthread_mutex_destroy(&g_state.mutex);
    
    close(g_sock);
    
    printf("✓ Cliente cerrado correctamente\n");
    return 0;
}