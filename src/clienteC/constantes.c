// constantes.c
#include "constantes.h"

// ========================
// CONSTANTES DE FÍSICA
// ========================
const float GRAVITY = 900.0f;      // píxeles / s^2
const float MOVE_SPEED = 220.0f;   // píxeles / s
const float JUMP_SPEED = -320.0f;  // píxeles / s (negativo = arriba)

// ========================
// CONSTANTES DE RENDERIZADO
// ========================
const float PLAYER_SCALE = 0.3f;
const float FRUIT_SCALE = 0.2f;
const float ENEMY_TARGET_SIZE_RED = 40.0f;
const float ENEMY_TARGET_SIZE_BLUE = 42.0f;
const float DK_SCALE = 0.320f;       
const float MARIO_SCALE = 0.32f;    

// ========================
// CONSTANTES DE COLISIÓN
// ========================
const float PLAYER_HIT_RADIUS = 24.0f;
const float DK_COLLISION_RADIUS = 28.0f;     
const float MARIO_COLLISION_RADIUS = 26.0f; 

// ========================
// CONSTANTES DE LÓGICA
// ========================
const int PLATFORM_GOAL_INDEX = 10;  // P10 es la meta
const int PLATFORM_DK_MARIO_INDEX = 3; // P3 es la plataforma de DK y Mario
// ========================
// CONSTANTES DE TREPAR
// ========================
const float VELOCIDAD_TREPAR = 150.0f;      // píxeles por segundo
const float TOLERANCIA_X_LIANA = 32.0f;     // px tolerancia horizontal
const float TOLERANCIA_Y_LIANA = 32.0f;     // px tolerancia vertical