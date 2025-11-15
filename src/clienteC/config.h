// config.h - Configuración del cliente C

#ifndef CONFIG_H
#define CONFIG_H

// ========================
// Configuración de Red
// ========================

#define SERVER_IP "127.0.0.1"  // Cambiar según tu red
#define SERVER_PORT 8080

// ========================
// Configuración de Pantalla
// ========================

#define SCREEN_WIDTH 800
#define SCREEN_HEIGHT 600
#define TARGET_FPS 60

// ========================
// Rutas de Assets
// ========================

#define PLAYER_TEXTURE_PATH "assets/dk.jpg"
#define STAGE_TEXTURE_PATH "assets/stage.png"

// ========================
// Capacidades iniciales
// ========================

#define INITIAL_PLAYERS_CAP 10
#define INITIAL_ENEMIES_CAP 20
#define INITIAL_FRUITS_CAP 30

#endif // CONFIG_H