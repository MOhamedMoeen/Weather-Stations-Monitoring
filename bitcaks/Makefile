SRC_DIR   := src/main/java
OUT_DIR   := out
MAIN      := org.example.server.BitcaskServer
JAR       := bitcask-server.jar

SOURCES := $(shell find $(SRC_DIR) -name "*.java")

.PHONY: all compile jar run clean

all: jar

compile:
	@mkdir -p $(OUT_DIR)
	javac -d $(OUT_DIR) $(SOURCES)
	@echo "Compiled $(words $(SOURCES)) source files → $(OUT_DIR)/"

jar: compile
	@echo "Main-Class: $(MAIN)" > $(OUT_DIR)/MANIFEST.MF
	jar cfm $(JAR) $(OUT_DIR)/MANIFEST.MF -C $(OUT_DIR) .
	@echo "Built $(JAR)"


PORT     ?= 8080
DATA_DIR ?= ./bitcask-data

run: compile
	java -cp $(OUT_DIR) $(MAIN) $(PORT) $(DATA_DIR)

run-jar: jar
	java -jar $(JAR) $(PORT) $(DATA_DIR)

clean:
	rm -rf $(OUT_DIR) $(JAR)