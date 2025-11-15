# ================================
# Makefile para Cliente C - Servidor Java
# ================================

# ================================
# Detectar sistema operativo
# ================================
UNAME_S := $(shell uname -s)

# ================================
# Compilador y flags base
# ================================
CC = clang
CFLAGS = -Wall -Wextra -std=c11 -Ilibrerias

ifeq ($(UNAME_S),Darwin)
    # ---------- macOS ----------
    CFLAGS += -I/opt/homebrew/include
    LDFLAGS = -L/opt/homebrew/lib -lraylib -framework OpenGL -framework Cocoa -framework IOKit -framework CoreVideo -lpthread
else
    # ---------- Linux ----------
    CFLAGS += -I/usr/include
    LDFLAGS = -L/usr/local/lib -lraylib -lGL -lm -lpthread -ldl -lrt -lX11
endif

# ================================
# Archivos fuente y salida
# ================================
SRC = cliente.c librerias/cJSON.c
HEADERS = config.h librerias/cJSON.h
BIN = cliente

# ================================
# Reglas principales
# ================================
.PHONY: all clean run run-debug run-player multi help

all: $(BIN)

# Compilar el cliente
$(BIN): $(SRC) $(HEADERS)
	@echo "Compilando cliente..."
	$(CC) $(CFLAGS) $(SRC) -o $(BIN) $(LDFLAGS)
	@echo "✓ Cliente compilado exitosamente"

# Ejecutar con nombre por defecto
run: $(BIN)
	./$(BIN)

# Ejecutar con nombre personalizado
run-player: $(BIN)
	@read -p "Nombre del jugador: " name; \
	./$(BIN) $name

# Ejecutar modo debug
run-debug: CFLAGS += -g -O0 -DDEBUG
run-debug: clean $(BIN)
	@echo "✓ Cliente compilado en modo debug"
	./$(BIN)

# Ejecutar múltiples clientes
multi: $(BIN)
	@echo "Iniciando 3 clientes..."
	./$(BIN) Jugador1 &
	sleep 1
	./$(BIN) Jugador2 &
	sleep 1
	./$(BIN) Jugador3 &
	@echo "✓ 3 clientes iniciados"

# Limpiar archivos compilados
clean:
	@echo "Limpiando archivos..."
	rm -f $(BIN)
	@echo "✓ Limpieza completada"

# Mostrar ayuda
help:
	@echo "════════════════════════════════════════════════"
	@echo "  Makefile - Cliente C para Servidor Java"
	@echo "════════════════════════════════════════════════"
	@echo ""
	@echo "Targets disponibles:"
	@echo "  make              - Compilar el cliente"
	@echo "  make run          - Ejecutar (nombre: ClienteC)"
	@echo "  make run-player   - Ejecutar con nombre personalizado"
	@echo "  make run-debug    - Compilar y ejecutar en modo debug"
	@echo "  make multi        - Iniciar 3 clientes"
	@echo "  make clean        - Limpiar archivos compilados"
	@echo "  make help         - Mostrar esta ayuda"
	@echo ""
	@echo "Ejemplos:"
	@echo "  make"
	@echo "  ./cliente Jugador1"
	@echo "  make run-player"
	@echo ""
	@echo "Estructura de archivos:"
	@echo "  cliente.c           - Código principal"
	@echo "  config.h            - Configuración"
	@echo "  librerias/cJSON.c   - Librería JSON"
	@echo "  librerias/cJSON.h   - Headers JSON"
	@echo "════════════════════════════════════════════════"

# Instalar dependencias según el sistema
install-deps:
ifeq ($(UNAME_S),Darwin)
	@echo "Instalando dependencias en macOS..."
	brew install raylib
else
	@echo "Instalando dependencias en Linux..."
	sudo apt update
	sudo apt install -y libraylib-dev build-essential
endif
	@echo "✓ Dependencias instaladas"