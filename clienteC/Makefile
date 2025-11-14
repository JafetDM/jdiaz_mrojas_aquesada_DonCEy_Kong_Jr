# ================================
# Detectar sistema operativo
# ================================
UNAME_S := $(shell uname -s)

# ================================
# Flags para compilación
# ================================
CFLAGS = -Wall -Wextra -std=c11

ifeq ($(UNAME_S),Darwin)
    # ---------- macOS ----------
    CFLAGS += -I/opt/homebrew/include
    LDFLAGS = -L/opt/homebrew/lib -lraylib -framework OpenGL -framework Cocoa -framework IOKit -framework CoreVideo
else
    # ---------- Linux ----------
    CFLAGS += -I/usr/include
    LDFLAGS = -L/usr/local/lib -lraylib -lGL -lm -lpthread -ldl -lrt -lX11
endif

# ================================
# Compilador
# ================================
CC = clang

# ================================
# Archivos fuente
# ================================
SRC = cliente.c cJSON.c
BIN = cliente

# ================================
# Reglas principales
# ================================
all: $(BIN)

$(BIN): $(SRC)
	$(CC) $(CFLAGS) $(SRC) -o $(BIN) $(LDFLAGS)

run:
	./$(BIN)

clean:
	rm -f $(BIN)

