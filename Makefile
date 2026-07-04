.DEFAULT_GOAL := help

ARTIFACT_ID ?= memoria-vault
APP_NAME ?= Memoria Vault
APP_ARTIFACT_NAME ?= Memoria-Vault
APP_ID ?= be.cnoupoue.memoriavault
APP_VERSION ?= $(shell sed -n '/<artifactId>$(ARTIFACT_ID)<\/artifactId>/,/<\/version>/ s:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -n 1)
JPACKAGE_VERSION ?= $(shell printf '%s\n' '$(APP_VERSION)' | sed 's/-SNAPSHOT$$//' | sed 's/[^0-9.].*//' | awk -F. '{ major=$$1 + 0; minor=$$2 + 0; patch=$$3 + 0; if (major < 1) major = 1; printf "%d.%d.%d\n", major, minor, patch }')
JAR_PATH ?= target/$(ARTIFACT_ID)-$(APP_VERSION).jar
SPRING_PROFILE ?= production
SPRING_ARGS ?= --spring.profiles.active=$(SPRING_PROFILE)
DIST_DIR ?= dist
APP_OUTPUT_DIR ?= $(DIST_DIR)/app
INSTALLER_OUTPUT_DIR ?= $(DIST_DIR)/installers
JPACKAGE_INPUT_DIR ?= $(DIST_DIR)/jpackage-input
PACKAGING_DIR ?= packaging
TARGET_OS ?= macos
TARGET_ARCH ?= arm64
MACOS_PACKAGING_DIR ?= $(PACKAGING_DIR)/macos
MACOS_ARCH ?= $(TARGET_ARCH)
MACOS_ICON ?= $(MACOS_PACKAGING_DIR)/icon/MemoriaVault.icns
MACOS_ICON_SOURCE ?= frontend/public/favicon.png
MACOS_APP_PATH ?= $(APP_OUTPUT_DIR)/$(APP_NAME).app
MACOS_DMG_PATH ?= $(INSTALLER_OUTPUT_DIR)/$(APP_ARTIFACT_NAME)-$(APP_VERSION)-macos-$(MACOS_ARCH).dmg
JLINK_OPTIONS ?= --strip-debug --no-man-pages --no-header-files --compress zip-6
BUNDLED_FFMPEG_SOURCE ?= $(MACOS_PACKAGING_DIR)/ffmpeg/$(MACOS_ARCH)/ffmpeg
BUNDLED_FFMPEG_APP_DIR ?= ffmpeg
BUNDLED_FFMPEG_STAGED_PATH ?= $(JPACKAGE_INPUT_DIR)/$(BUNDLED_FFMPEG_APP_DIR)/ffmpeg
BUNDLED_FFMPEG_APP_PATH ?= $(MACOS_APP_PATH)/Contents/app/$(BUNDLED_FFMPEG_APP_DIR)/ffmpeg

.PHONY: help install dev run-backend run-frontend \
	format format-backend format-frontend \
	format-check format-check-backend format-check-frontend \
	lint lint-frontend lint-branding lint-fix test test-backend test-frontend \
	build build-backend build-frontend build-production package-jar \
	run-production verify-production inspect-jar \
	package-macos-app package-macos-dmg package-macos run-macos-app \
	package-windows package-linux \
	inspect-macos-app clean-packaging generate-macos-icon prepare-macos-input \
	check-bundled-ffmpeg prepare-bundled-ffmpeg inspect-bundled-ffmpeg \
	check-macos check-macos-arm64 check-jpackage check-icon-tools \
	check-production-jar tag push-tag verify clean health

help: ## Show available commands
	@echo ""
	@echo "Memoria Vault local commands"
	@echo "Production JAR: $(JAR_PATH)"
	@echo ""
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z0-9_-]+:.*##/ {printf "  \033[36m%-24s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo ""

install: ## Install root tooling and frontend dependencies
	@if [ -f package-lock.json ]; then \
		npm ci; \
	else \
		npm install; \
	fi
	@if [ -f frontend/package-lock.json ]; then \
		npm --prefix frontend ci; \
	else \
		npm --prefix frontend install; \
	fi

dev: ## Start backend and frontend together
	@set -e; \
	./mvnw spring-boot:run & backend_pid=$$!; \
	npm --prefix frontend run dev & frontend_pid=$$!; \
	trap 'kill $$backend_pid $$frontend_pid 2>/dev/null || true' INT TERM EXIT; \
	wait $$backend_pid $$frontend_pid

run-backend: ## Start only the Spring Boot backend
	./mvnw spring-boot:run

run-frontend: ## Start only the React frontend
	npm --prefix frontend run dev

format: ## Automatically format Java and frontend code
	$(MAKE) format-backend
	$(MAKE) format-frontend

format-backend: ## Format Java code with Spotless
	./mvnw spotless:apply

format-frontend: ## Format frontend files with Prettier
	npm --prefix frontend run format

format-check: ## Check formatting without changing files
	$(MAKE) format-check-backend
	$(MAKE) format-check-frontend

format-check-backend: ## Check Java formatting with Spotless
	./mvnw spotless:check

format-check-frontend: ## Check frontend formatting with Prettier
	npm --prefix frontend run format:check

lint: lint-frontend lint-branding ## Run frontend linting and public branding checks

lint-frontend: ## Run frontend linting
	npm --prefix frontend run lint

lint-branding: ## Check public-facing branding guardrails
	npm run lint:branding

lint-fix: ## Automatically fix frontend lint issues where possible
	npm --prefix frontend run lint:fix

test: ## Run backend and frontend tests
	$(MAKE) test-backend
	$(MAKE) test-frontend

test-backend: ## Run Spring Boot tests
	./mvnw test

test-frontend: ## Run React and TypeScript tests
	npm --prefix frontend run test

build: ## Build separate development artifacts; use build-production for the standalone JAR
	$(MAKE) build-backend
	$(MAKE) build-frontend

build-backend: ## Build the Spring Boot JAR without rerunning tests
	./mvnw -DskipTests package

build-frontend: ## Build the frontend production bundle
	npm --prefix frontend run build

build-production: ## Build the standalone production JAR with the React frontend embedded
	./mvnw -P$(SPRING_PROFILE) -DskipTests package

package-jar: build-production ## Alias for creating the final executable production JAR

$(JAR_PATH):
	$(MAKE) package-jar

run-production: $(JAR_PATH) ## Build if missing, then run the packaged production JAR
	java -jar $(JAR_PATH) --spring.profiles.active=$(SPRING_PROFILE)

verify-production: ## Run checks, then build and inspect the production JAR
	$(MAKE) format-check
	$(MAKE) lint
	$(MAKE) test
	$(MAKE) package-jar
	$(MAKE) inspect-jar

inspect-jar: ## Verify the production JAR contains the compiled React entrypoint and favicon
	@test -f "$(JAR_PATH)" || { echo "Missing JAR: $(JAR_PATH). Run 'make package-jar' first."; exit 1; }
	@jar tf "$(JAR_PATH)" | grep -qx 'BOOT-INF/classes/static/index.html'
	@jar tf "$(JAR_PATH)" | grep -qx 'BOOT-INF/classes/static/favicon.png'
	@echo "Production JAR contains embedded React assets."

check-macos:
	@test "$$(uname -s)" = "Darwin" || { echo "macOS packaging requires macOS."; exit 1; }

check-macos-arm64: check-macos
	@test "$$(uname -m)" = "$(MACOS_ARCH)" || { echo "macOS packaging currently requires $(MACOS_ARCH). Found: $$(uname -m)"; exit 1; }

check-jpackage:
	@command -v jpackage >/dev/null 2>&1 || { echo "jpackage is required. Use a JDK that includes jpackage."; exit 1; }

check-icon-tools: check-macos
	@command -v sips >/dev/null 2>&1 || { echo "sips is required to generate the macOS icon."; exit 1; }
	@command -v node >/dev/null 2>&1 || { echo "node is required to generate the macOS icon fallback."; exit 1; }

check-production-jar:
	@test -f "$(JAR_PATH)" || { echo "Missing production JAR: $(JAR_PATH). Run 'make package-jar' first."; exit 1; }

prepare-macos-input: package-jar ## Stage only the production JAR for jpackage
	@rm -rf "$(JPACKAGE_INPUT_DIR)"
	@mkdir -p "$(JPACKAGE_INPUT_DIR)"
	cp "$(JAR_PATH)" "$(JPACKAGE_INPUT_DIR)/"

check-bundled-ffmpeg: check-macos-arm64 ## Verify the macOS arm64 FFmpeg binary is present for packaging
	@test -f "$(BUNDLED_FFMPEG_SOURCE)" || { echo "Missing bundled FFmpeg: $(BUNDLED_FFMPEG_SOURCE). Add a verified macOS $(MACOS_ARCH) FFmpeg binary and update packaging/macos/ffmpeg/README.md plus THIRD_PARTY_NOTICES.md."; exit 1; }
	@test -x "$(BUNDLED_FFMPEG_SOURCE)" || { echo "Bundled FFmpeg is not executable: $(BUNDLED_FFMPEG_SOURCE). Run 'chmod +x $(BUNDLED_FFMPEG_SOURCE)' after verifying the binary."; exit 1; }
	@file "$(BUNDLED_FFMPEG_SOURCE)" | grep -Eq 'arm64' || { echo "Bundled FFmpeg must support macOS arm64: $(BUNDLED_FFMPEG_SOURCE)."; exit 1; }
	@"$(BUNDLED_FFMPEG_SOURCE)" -version >/dev/null || { echo "Bundled FFmpeg failed validation: $(BUNDLED_FFMPEG_SOURCE) -version"; exit 1; }
	@otool -L "$(BUNDLED_FFMPEG_SOURCE)" | grep -Eq '/(opt/homebrew|usr/local)/(Cellar|opt)/' && { echo "Bundled FFmpeg must not depend on Homebrew dynamic libraries."; exit 1; } || true
	@echo "Bundled FFmpeg is present and executable: $(BUNDLED_FFMPEG_SOURCE)"

prepare-bundled-ffmpeg: prepare-macos-input check-bundled-ffmpeg ## Stage bundled FFmpeg for jpackage
	@mkdir -p "$(JPACKAGE_INPUT_DIR)/$(BUNDLED_FFMPEG_APP_DIR)"
	install -m 755 "$(BUNDLED_FFMPEG_SOURCE)" "$(BUNDLED_FFMPEG_STAGED_PATH)"
	@echo "Staged bundled FFmpeg at $(BUNDLED_FFMPEG_STAGED_PATH)"

generate-macos-icon: check-icon-tools ## Generate the macOS app icon from the favicon PNG
	@test -f "$(MACOS_ICON_SOURCE)" || { echo "Missing icon source: $(MACOS_ICON_SOURCE)"; exit 1; }
	@set -e; \
	tmp_dir="$$(mktemp -d "$${TMPDIR:-/tmp}/memoriavault-icon.XXXXXX")"; \
	iconset="$$tmp_dir/$(APP_NAME).iconset"; \
	mkdir -p "$$iconset" "$$(dirname "$(MACOS_ICON)")"; \
	sips -z 16 16 "$(MACOS_ICON_SOURCE)" --out "$$iconset/icon_16x16.png" >/dev/null; \
	sips -z 32 32 "$(MACOS_ICON_SOURCE)" --out "$$iconset/icon_16x16@2x.png" >/dev/null; \
	sips -z 32 32 "$(MACOS_ICON_SOURCE)" --out "$$iconset/icon_32x32.png" >/dev/null; \
	sips -z 64 64 "$(MACOS_ICON_SOURCE)" --out "$$iconset/icon_32x32@2x.png" >/dev/null; \
	sips -z 128 128 "$(MACOS_ICON_SOURCE)" --out "$$iconset/icon_128x128.png" >/dev/null; \
	sips -z 256 256 "$(MACOS_ICON_SOURCE)" --out "$$iconset/icon_128x128@2x.png" >/dev/null; \
	sips -z 256 256 "$(MACOS_ICON_SOURCE)" --out "$$iconset/icon_256x256.png" >/dev/null; \
	sips -z 512 512 "$(MACOS_ICON_SOURCE)" --out "$$iconset/icon_256x256@2x.png" >/dev/null; \
	sips -z 512 512 "$(MACOS_ICON_SOURCE)" --out "$$iconset/icon_512x512.png" >/dev/null; \
	sips -z 1024 1024 "$(MACOS_ICON_SOURCE)" --out "$$iconset/icon_512x512@2x.png" >/dev/null; \
	if command -v iconutil >/dev/null 2>&1 && iconutil -c icns "$$iconset" -o "$(MACOS_ICON)" >/dev/null 2>&1; then \
		true; \
	else \
		node "$(MACOS_PACKAGING_DIR)/scripts/create-icns.mjs" "$$iconset" "$(MACOS_ICON)"; \
	fi; \
	rm -rf "$$tmp_dir"; \
	test -f "$(MACOS_ICON)" || { echo "Icon generation failed: $(MACOS_ICON)"; exit 1; }

package-macos-app: prepare-bundled-ffmpeg generate-macos-icon check-macos-arm64 check-jpackage ## Create dist/app/Memoria Vault.app with a bundled runtime and FFmpeg
	@rm -rf "$(MACOS_APP_PATH)"
	@mkdir -p "$(APP_OUTPUT_DIR)"
	jpackage \
		--type app-image \
		--dest "$(APP_OUTPUT_DIR)" \
		--name "$(APP_NAME)" \
		--app-version "$(JPACKAGE_VERSION)" \
		--vendor "cnoupoue" \
		--description "Private local archive viewer" \
		--mac-package-identifier "$(APP_ID)" \
		--input "$(JPACKAGE_INPUT_DIR)" \
		--main-jar "$$(basename "$(JAR_PATH)")" \
		--arguments "$(SPRING_ARGS)" \
		--icon "$(MACOS_ICON)" \
		--jlink-options "$(JLINK_OPTIONS)"

package-macos-dmg: package-macos-app ## Create dist/installers/Memoria-Vault-<version>-macos-arm64.dmg
	@rm -f "$(MACOS_DMG_PATH)"
	@mkdir -p "$(INSTALLER_OUTPUT_DIR)"
	jpackage \
		--type dmg \
		--dest "$(INSTALLER_OUTPUT_DIR)" \
		--app-image "$(MACOS_APP_PATH)" \
		--name "$(APP_NAME)" \
		--app-version "$(JPACKAGE_VERSION)" \
		--vendor "cnoupoue" \
		--mac-package-identifier "$(APP_ID)"
	@mv "$(INSTALLER_OUTPUT_DIR)/$(APP_NAME)-$(JPACKAGE_VERSION).dmg" "$(MACOS_DMG_PATH)"

package-macos: package-macos-dmg ## Build the macOS app image and DMG

package-windows: ## Future work: Windows packaging is not implemented yet
	@echo "Windows packaging is not implemented yet. See packaging/windows/README.md."
	@exit 1

package-linux: ## Future work: Linux packaging is not implemented yet
	@echo "Linux packaging is not implemented yet. See packaging/linux/README.md."
	@exit 1

run-macos-app: inspect-macos-app ## Open the generated Memoria Vault.app
	open "$(MACOS_APP_PATH)"

inspect-macos-app: ## Verify the generated macOS app bundle looks runnable
	@test "$$(uname -s)" = "Darwin" || { echo "macOS app inspection requires macOS."; exit 1; }
	@test -d "$(MACOS_APP_PATH)" || { echo "Missing app bundle: $(MACOS_APP_PATH). Run 'make package-macos-app' first."; exit 1; }
	@test -d "$(MACOS_APP_PATH)/Contents/runtime" || { echo "Missing bundled runtime in $(MACOS_APP_PATH)."; exit 1; }
	@test -f "$(MACOS_APP_PATH)/Contents/app/$$(basename "$(JAR_PATH)")" || { echo "Missing bundled JAR in $(MACOS_APP_PATH)."; exit 1; }
	@test ! -f "$(MACOS_ICON)" || test -f "$(MACOS_APP_PATH)/Contents/Resources/$(APP_NAME).icns" || { echo "Missing app icon in $(MACOS_APP_PATH)."; exit 1; }
	@test -x "$(MACOS_APP_PATH)/Contents/MacOS/$(APP_NAME)" || { echo "Missing app launcher executable."; exit 1; }
	@echo "macOS app bundle is present and contains its runtime, launcher, JAR, and icon."

inspect-bundled-ffmpeg: inspect-macos-app ## Verify the generated macOS app contains bundled FFmpeg
	@test -f "$(BUNDLED_FFMPEG_APP_PATH)" || { echo "Missing bundled FFmpeg in app: $(BUNDLED_FFMPEG_APP_PATH)."; exit 1; }
	@test -x "$(BUNDLED_FFMPEG_APP_PATH)" || { echo "Bundled FFmpeg in app is not executable: $(BUNDLED_FFMPEG_APP_PATH)."; exit 1; }
	@file "$(BUNDLED_FFMPEG_APP_PATH)" | grep -Eq 'arm64' || { echo "Bundled app FFmpeg must support macOS arm64."; exit 1; }
	@"$(BUNDLED_FFMPEG_APP_PATH)" -version >/dev/null || { echo "Bundled app FFmpeg failed validation: $(BUNDLED_FFMPEG_APP_PATH) -version"; exit 1; }
	@otool -L "$(BUNDLED_FFMPEG_APP_PATH)" | grep -Eq '/(opt/homebrew|usr/local)/(Cellar|opt)/' && { echo "Bundled app FFmpeg must not depend on Homebrew dynamic libraries."; exit 1; } || true
	@echo "macOS app contains executable bundled FFmpeg."

clean-packaging: ## Remove generated packaging artifacts only
	rm -rf "$(DIST_DIR)"

tag: ## Create a verified annotated release tag locally; requires VERSION=MAJOR.MINOR.PATCH
	@test -n "$(VERSION)" || { echo "VERSION is required. Example: make tag VERSION=0.1.0"; exit 1; }
	@printf '%s\n' "$(VERSION)" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$$' || { echo "VERSION must be stable semantic version MAJOR.MINOR.PATCH without a leading v or suffix. Example: 0.1.0"; exit 1; }
	@test -z "$$(git status --porcelain)" || { echo "Refusing to tag because the Git working tree is not clean."; exit 1; }
	@test "$$(git rev-parse --abbrev-ref HEAD)" = "main" || { echo "Refusing to tag because the current branch is not main."; exit 1; }
	@git fetch origin main --tags
	@test "$$(git rev-parse HEAD)" = "$$(git rev-parse origin/main)" || { echo "Refusing to tag because HEAD is not synchronized with origin/main."; exit 1; }
	@if git rev-parse -q --verify "refs/tags/v$(VERSION)" >/dev/null; then \
		echo "Refusing to tag because v$(VERSION) already exists locally."; \
		exit 1; \
	fi
	@if git ls-remote --exit-code --tags origin "refs/tags/v$(VERSION)" >/dev/null 2>&1; then \
		echo "Refusing to tag because v$(VERSION) already exists on origin."; \
		exit 1; \
	fi
	@project_version="$$(sed -n '/<artifactId>$(ARTIFACT_ID)<\/artifactId>/,/<\/version>/ s:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -n 1)"; \
	if [ "$$project_version" != "$(VERSION)" ] && [ "$$project_version" != "$(VERSION)-SNAPSHOT" ]; then \
		echo "Refusing to tag because Maven project version $$project_version does not match $(VERSION) or $(VERSION)-SNAPSHOT."; \
		exit 1; \
	fi
	$(MAKE) verify
	git tag -a "v$(VERSION)" -m "$(APP_NAME) v$(VERSION)"
	@printf '\nTag created locally: v$(VERSION)\n\nTo trigger the GitHub release pipeline, review the tag and run:\n\ngit push origin v$(VERSION)\n'

push-tag: ## Push an existing local release tag; requires VERSION=MAJOR.MINOR.PATCH
	@test -n "$(VERSION)" || { echo "VERSION is required. Example: make push-tag VERSION=0.1.0"; exit 1; }
	@printf '%s\n' "$(VERSION)" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$$' || { echo "VERSION must be stable semantic version MAJOR.MINOR.PATCH without a leading v or suffix. Example: 0.1.0"; exit 1; }
	@git rev-parse -q --verify "refs/tags/v$(VERSION)" >/dev/null || { echo "Local tag v$(VERSION) does not exist. Run 'make tag VERSION=$(VERSION)' first."; exit 1; }
	@printf 'This will trigger the macOS GitHub Release workflow for v$(VERSION).\n'
	git push origin "v$(VERSION)"

verify: ## Run all formatting checks, linting, tests, and builds
	$(MAKE) format-check
	$(MAKE) lint
	$(MAKE) test
	$(MAKE) build

clean: ## Remove generated build and test files
	./mvnw clean
	rm -rf frontend/dist frontend/coverage
	$(MAKE) clean-packaging

health: ## Check whether the local backend is running
	curl --fail http://127.0.0.1:8080/actuator/health
