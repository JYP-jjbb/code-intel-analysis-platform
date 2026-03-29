# 找到所有子目录
SUBDIRS := $(shell ls -d */)

# ===== 统一工具与参数（实验配置中心） =====
JAVAC = javac
JAVAC_FLAGS = --release 11 -encoding UTF-8 -g
JAR = jar

# ===== 默认目标：先清理，再重新构建 =====
all: clean-all build-all

# ===== 构建所有子目录 =====
build-all:
	@for d in $(SUBDIRS); do \
		echo ">>> building $$d"; \
		$(MAKE) -C $$d \
			JAVAC="$(JAVAC)" \
			JAVAC_FLAGS="$(JAVAC_FLAGS)" \
			JAR="$(JAR)"; \
	done

# ===== 清理所有子目录 =====
clean-all:
	@for d in $(SUBDIRS); do \
		echo ">>> cleaning $$d"; \
		$(MAKE) -C $$d clean; \
	done

.PHONY: all build-all clean-all
