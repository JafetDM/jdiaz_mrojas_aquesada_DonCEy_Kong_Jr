#ifndef CONFIG_H
#define CONFIG_H

// network
#define SERVER_IP       "127.0.0.1"
        // Nota: para conocer el IP de wifi de la Mac en Mac, usar el comando: ipconfig getifaddr en0 
        // 192.168.5.150
        // local: 127
#define SERVER_PORT     8080
#define RECV_BUFFER     8192

// rendering / window
#define SCREEN_WIDTH    800
#define SCREEN_HEIGHT   600
#define TARGET_FPS      60

// game limits (para reservar/realloc)
#define INITIAL_ENEMIES_CAP  16
#define INITIAL_FRUITS_CAP   8

// assets
#define PLAYER_TEXTURE_PATH  "assets/dk.jpg"
#define STAGE_TEXTURE_PATH   "assets/stage.png"

#endif // CONFIG_H
