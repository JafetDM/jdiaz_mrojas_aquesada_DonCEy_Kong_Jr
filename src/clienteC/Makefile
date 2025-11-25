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
# Detectar compilador disponible
CC := $(shell command -v clang 2> /dev/null)
ifeq ($(CC),)
	CC := gcc
endif

CFLAGS = -Wall -Wextra -std=c11 -Ilibrerias

ifeq ($(UNAME_S),Darwin)
	# ---------- macOS ----------
	CFLAGS += -I/opt/homebrew/include
	LDFLAGS = -L/opt/homebrew/lib -lraylib -framework OpenGL -framework Cocoa -framework IOKit -framework CoreVideo -lpthread
else
	# ---------- Linux / Ubuntu ----------
	CFLAGS += -I/usr/include -I/usr/local/include
	LDFLAGS = -L/usr/lib -L/usr/local/lib -lraylib -lGL -lm -lpthread -ldl -lrt -lX11
endif

# ================================
# Archivos fuente y salida
# ================================
SRC = cliente.c librerias/cJSON.c
HEADERS = config.h librerias/cJSON.h layout.h
BIN = cliente

# ================================
# Reglas principales
# ================================
.PHONY: all clean run run-debug run-player multi help install-deps check-raylib

all: check-raylib $(BIN)

# Verificar si Raylib está instalado
check-raylib:
	@echo "Verificando Raylib..."
	@if ! ldconfig -p | grep -q libraylib; then \
		echo ""; \
		echo "⚠️  ERROR: Raylib no está instalado"; \
		echo ""; \
		echo "Instala Raylib con:"; \
		echo "  make install-deps"; \
		echo ""; \
		echo "O manualmente:"; \
		echo "  sudo apt update"; \
		echo "  sudo apt install -y libraylib-dev"; \
		echo ""; \
		exit 1; \
	fi
	@echo "✓ Raylib encontrado"

# Compilar el cliente
$(BIN): $(SRC) $(HEADERS)
	@echo "Compilando cliente con $(CC)..."
	$(CC) $(CFLAGS) $(SRC) -o $(BIN) $(LDFLAGS)
	@echo "✓ Cliente compilado exitosamente"

# Ejecutar con nombre por defecto
run: $(BIN)
	./$(BIN)

# Ejecutar con nombre personalizado
run-player: $(BIN)
	@read -p "Nombre del jugador: " name; \
	./$(BIN) $$name

# Ejecutar modo debug
run-debug: CFLAGS += -g -O0 -DDEBUG
run-debug: clean $(BIN)
	@echo "✓ Cliente compilado en modo debug"
	./$(BIN)

# Ejecutar múltiples clientes
multi: $(BIN)
	@echo "Iniciando 2 clientes..."
	./$(BIN) Jugador1 &
	sleep 1
	./$(BIN) Jugador2 &
	@echo "✓ 2 clientes iniciados"

# Limpiar archivos compilados
clean:
	@echo "Limpiando archivos..."
	rm -f $(BIN) editor
	@echo "✓ Limpieza completada"

# Mostrar ayuda
help:
	@echo "════════════════════════════════════════════════"
	@echo "  Makefile - Cliente C para Servidor Java"
	@echo "════════════════════════════════════════════════"
	@echo ""
	@echo "Sistema: $(UNAME_S)"
	@echo "Compilador: $(CC)"
	@echo ""
	@echo "Targets disponibles:"
	@echo "  make              - Compilar el cliente"
	@echo "  make run          - Ejecutar (nombre: ClienteC)"
	@echo "  make run-player   - Ejecutar con nombre personalizado"
	@echo "  make run-debug    - Compilar y ejecutar en modo debug"
	@echo "  make multi        - Iniciar 2 clientes"
	@echo "  make clean        - Limpiar archivos compilados"
	@echo "  make install-deps - Instalar dependencias"
	@echo "  make check-raylib - Verificar instalación de Raylib"
	@echo "  make help         - Mostrar esta ayuda"
	@echo ""
	@echo "Ejemplos:"
	@echo "  make install-deps    # Primero instalar dependencias"
	@echo "  make                 # Compilar"
	@echo "  ./cliente Jugador1   # Ejecutar"
	@echo "  make run-player      # Ejecutar con nombre personalizado"
	@echo ""
	@echo "Estructura de archivos:"
	@echo "  cliente.c           - Código principal"
	@echo "  config.h            - Configuración"
	@echo "  layout.h            - Layout del mapa (lianas/plataformas)"
	@echo "  librerias/cJSON.c   - Librería JSON"
	@echo "  librerias/cJSON.h   - Headers JSON"
	@echo "════════════════════════════════════════════════"

# Instalar dependencias según el sistema
install-deps:
ifeq ($(UNAME_S),Darwin)
	@echo "Instalando dependencias en macOS..."
	brew install raylib
else
	@echo "Instalando dependencias en Ubuntu/Linux..."
	@echo ""
	@echo "Se instalarán los siguientes paquetes:"
	@echo "  - libraylib-dev (biblioteca gráfica)"
	@echo "  - build-essential (herramientas de compilación)"
	@echo "  - gcc (compilador C)"
	@echo ""
	sudo apt update
	sudo apt install -y libraylib-dev build-essential gcc
	@echo ""
	@echo "✓ Dependencias instaladas"
	@echo ""
	@echo "Verifica la instalación con:"
	@echo "  make check-raylib"
endif

# Compilar editor de coordenadas
editor: coord_editor.c
	@echo "Compilando editor de coordenadas con $(CC)..."
	$(CC) $(CFLAGS) coord_editor.c -o editor $(LDFLAGS)
	@echo "✓ Editor compilado como ./editor"

# Forzar compilación sin verificar Raylib (usar con cuidado)
force: $(SRC) $(HEADERS)
	@echo "⚠️  Compilando sin verificar dependencias..."
	$(CC) $(CFLAGS) $(SRC) -o $(BIN) $(LDFLAGS)
	@echo "✓ Cliente compilado"