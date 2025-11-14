#ifndef GAME_STATE_H
#define GAME_STATE_H

typedef struct {
    float x;
    float y;
    int vida;
} Player;

typedef struct {
    float x;
    float y;
    int tipo;
    int estado;
} Enemy;

typedef struct {
    float x;
    float y;
    int tipo;
    int estado;
} Fruit;

typedef struct {
    Player jugador;

    Enemy *enemigos;
    int totalEnemigos;

    Fruit *frutas;
    int totalFrutas;

} GameState;

#endif
