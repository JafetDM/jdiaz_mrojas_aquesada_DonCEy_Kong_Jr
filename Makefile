# Detecta el sistema operativo
UNAME_S := $(shell uname -s)

# Configura rutas según el sistema
ifeq ($(UNAME_S),Darwin) # macOS
    CFLAGS = -Wall -Wextra -std=c11 -I/opt/homebrew/include
    LDFLAGS = -L/opt/homebrew/lib -lraylib -framework OpenGL -framework Cocoa -framework IOKit -framework CoreVideo
else # Linux
    CFLAGS = -Wall -Wextra -std=c11 -I/usr/include
    LDFLAGS = -L/usr/lib -lraylib -lGL -lm -lpthread -ldl -lrt -lX11
endif

# Compilador
CC = clang

# Archivos fuente
CLIENT_SRC = cliente.c
SERVER_SRC = server.c
TEST_SRC = test_raylib.c

# Ejecutables
CLIENT_BIN = cliente
SERVER_BIN = server
TEST_BIN = test_raylib

# Reglas
all: $(CLIENT_BIN) $(SERVER_BIN) $(TEST_BIN)

$(CLIENT_BIN): $(CLIENT_SRC)
	$(CC) $(CFLAGS) $(CLIENT_SRC) -o $(CLIENT_BIN)

$(SERVER_BIN): $(SERVER_SRC)
	$(CC) $(CFLAGS) $(SERVER_SRC) -o $(SERVER_BIN)

$(TEST_BIN): $(TEST_SRC)
	$(CC) $(CFLAGS) $(TEST_SRC) -o $(TEST_BIN) $(LDFLAGS)

run_test: $(TEST_BIN)
	./$(TEST_BIN)

clean:
	rm -f $(CLIENT_BIN) $(SERVER_BIN) $(TEST_BIN)
