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
MACOS_NOTARIZATION_ARTIFACT_DIR ?= $(DIST_DIR)/notarization
MACOS_DMG_SHA256_PATH ?= $(MACOS_DMG_PATH).sha256
MACOS_PRISTINE_APP_JAR_BASELINE ?= $(APP_OUTPUT_DIR)/.pristine-packaged-app.jar
MACOS_ENTITLEMENTS_PATH ?= $(MACOS_PACKAGING_DIR)/entitlements/memoria-vault.entitlements.plist

.PHONY: help install dev \
	format format-backend format-frontend \
	format-check format-check-backend format-check-frontend \
	lint lint-frontend lint-branding lint-fix test test-backend test-frontend test-packaging \
	build build-backend build-frontend build-production package-jar \
	run-production verify-production inspect-jar \
	package-macos-app postprocess-macos-sqlite-native-libs sign-macos-app verify-macos-signatures \
	package-macos-dmg package-macos-dmg-from-signed-app sign-macos-dmg \
	verify-macos-dmg-signatures notarize-macos-dmg staple-macos-dmg verify-macos-notarization \
	package-macos-release package-macos checksum-macos-dmg run-macos-app \
	package-windows package-linux \
	inspect-macos-app clean-packaging generate-macos-icon prepare-macos-input \
	clean-macos-app-output validate-macos-packaging-input \
	validate-macos-pristine-packaged-app validate-macos-postprocessed-packaged-app validate-macos-packaged-app \
	check-bundled-ffmpeg prepare-bundled-ffmpeg inspect-bundled-ffmpeg \
	inspect-macos-signing-readiness test-macos-signing-readiness test-macos-release-pipeline \
	check-macos check-macos-arm64 check-jpackage check-icon-tools \
	check-production-jar verify clean health

help: ## Show available commands
	@echo ""
	@echo "Memoria Vault local commands"
	@echo "Production JAR: $(JAR_PATH)"
	@echo ""
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z0-9_-]+:.*##/ {printf "  \033[36m%-24s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@echo ""
	@echo "Signed release path: package-macos-app -> postprocess-macos-sqlite-native-libs -> sign-macos-app -> verify-macos-signatures -> package-macos-dmg-from-signed-app -> sign-macos-dmg -> verify-macos-dmg-signatures -> notarize-macos-dmg -> staple-macos-dmg -> verify-macos-notarization"
	@echo "Development packaging: package-macos builds an unsigned local DMG and is not a release artifact."
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
	$(MAKE) test-packaging

test-backend: ## Run Spring Boot tests
	./mvnw test

test-frontend: ## Run React and TypeScript tests
	npm --prefix frontend run test

test-packaging: test-macos-signing-readiness test-macos-release-pipeline ## Run packaging script tests without requiring Apple credentials

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

clean-macos-app-output: ## Remove generated macOS app-image output before packaging
	@rm -rf "$(APP_OUTPUT_DIR)"
	@mkdir -p "$(APP_OUTPUT_DIR)"

validate-macos-packaging-input: check-production-jar ## Verify the production JAR selected for macOS packaging is current
	@bash -c '. "$(MACOS_PACKAGING_DIR)/scripts/app-jar.sh"; assert_source_production_jar "$$1" "$$2" "$$3"' _ "$(JAR_PATH)" "$(APP_VERSION)" "$(ARTIFACT_ID)"

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

package-macos-app: build-production clean-macos-app-output prepare-bundled-ffmpeg validate-macos-packaging-input generate-macos-icon check-macos-arm64 check-jpackage ## Create dist/app/Memoria Vault.app with a bundled runtime and FFmpeg
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
	@$(MAKE) validate-macos-pristine-packaged-app

validate-macos-pristine-packaged-app: check-production-jar ## Verify the freshly packaged app matches the current production JAR exactly
	@test -d "$(MACOS_APP_PATH)" || { echo "Missing app bundle: $(MACOS_APP_PATH). Run 'make package-macos-app' first."; exit 1; }
	@bash -c '. "$(MACOS_PACKAGING_DIR)/scripts/app-jar.sh"; source_jar="$$(resolve_absolute_path "$$1")"; packaged_jar="$$(find_packaged_app_jar "$$2")"; baseline="$$(resolve_absolute_path "$$5")"; assert_packaged_app_jar_matches_build "$$source_jar" "$$packaged_jar" "$$3" "$$4" "$$baseline"' _ "$(JAR_PATH)" "$(MACOS_APP_PATH)" "$(APP_VERSION)" "$(ARTIFACT_ID)" "$(MACOS_PRISTINE_APP_JAR_BASELINE)"

validate-macos-postprocessed-packaged-app: check-production-jar ## Verify only the nested SQLite JDBC archive changed after post-processing
	@test -d "$(MACOS_APP_PATH)" || { echo "Missing app bundle: $(MACOS_APP_PATH). Run 'make package-macos-app' first."; exit 1; }
	@bash -c '. "$(MACOS_PACKAGING_DIR)/scripts/app-jar.sh"; baseline="$$(resolve_absolute_path "$$1")"; packaged_jar="$$(find_packaged_app_jar "$$2")"; assert_sqlite_only_postprocessed_app_jar "$$baseline" "$$packaged_jar" "$$3" "$$4" "$${APPLE_DEVELOPER_ID_APPLICATION:-}" "$${APPLE_TEAM_ID:-}"' _ "$(MACOS_PRISTINE_APP_JAR_BASELINE)" "$(MACOS_APP_PATH)" "$(APP_VERSION)" "$(ARTIFACT_ID)"

validate-macos-packaged-app: validate-macos-pristine-packaged-app ## Alias for pristine packaged app validation

postprocess-macos-sqlite-native-libs: check-macos ## Sign SQLite native libraries embedded inside the packaged application JAR
	@test -d "$(MACOS_APP_PATH)" || { echo "Missing app bundle: $(MACOS_APP_PATH). Run 'make package-macos-app' first."; exit 1; }
	@$(MAKE) validate-macos-pristine-packaged-app
	@packaging/macos/scripts/sign-sqlite-native-libs.sh "$(MACOS_APP_PATH)"
	@$(MAKE) validate-macos-postprocessed-packaged-app

sign-macos-app: check-macos ## Sign nested Mach-O code and the final existing macOS app bundle
	@test -d "$(MACOS_APP_PATH)" || { echo "Missing app bundle: $(MACOS_APP_PATH). Run 'make package-macos-app' first."; exit 1; }
	@$(MAKE) validate-macos-postprocessed-packaged-app
	@MACOS_ENTITLEMENTS_PATH="$(MACOS_ENTITLEMENTS_PATH)" packaging/macos/scripts/sign-app.sh "$(MACOS_APP_PATH)"

verify-macos-signatures: check-macos ## Strictly verify all nested signatures and the final app bundle
	@$(MAKE) validate-macos-postprocessed-packaged-app
	@MACOS_ENTITLEMENTS_PATH="$(MACOS_ENTITLEMENTS_PATH)" packaging/macos/scripts/verify-signatures.sh "$(MACOS_APP_PATH)"

package-macos-dmg: package-macos-app ## Create an unsigned development DMG; do not use for signed releases
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

package-macos-dmg-from-signed-app: check-macos-arm64 ## Create the release DMG from the already signed app without rebuilding it
	@test -d "$(MACOS_APP_PATH)" || { echo "Signed macOS app is missing or invalid. Refusing to create a DMG."; exit 1; }
	@$(MAKE) validate-macos-postprocessed-packaged-app
	@MACOS_ENTITLEMENTS_PATH="$(MACOS_ENTITLEMENTS_PATH)" packaging/macos/scripts/verify-signatures.sh "$(MACOS_APP_PATH)" >/dev/null 2>&1 || { echo "Signed macOS app is missing or invalid. Refusing to create a DMG."; exit 1; }
	@packaging/macos/scripts/create-dmg.sh "$(MACOS_APP_PATH)" "$(MACOS_DMG_PATH)" "$(APP_NAME)"

sign-macos-dmg: check-macos ## Sign the existing DMG with Developer ID
	@test -f "$(MACOS_DMG_PATH)" || { echo "Missing DMG: $(MACOS_DMG_PATH). Run 'make package-macos-dmg-from-signed-app' first."; exit 1; }
	@test -n "$${APPLE_DEVELOPER_ID_APPLICATION:-}" || { echo "APPLE_DEVELOPER_ID_APPLICATION is required."; exit 1; }
	@if [ -n "$${KEYCHAIN_PATH:-$${APPLE_CODESIGN_KEYCHAIN:-}}" ]; then \
		codesign --force --options runtime --timestamp --sign "$${APPLE_DEVELOPER_ID_APPLICATION}" --keychain "$${KEYCHAIN_PATH:-$${APPLE_CODESIGN_KEYCHAIN:-}}" "$(MACOS_DMG_PATH)" || { echo "Unable to access the configured Developer ID signing identity."; echo "Check that the certificate private key is available and that KEYCHAIN_PATH is configured correctly."; exit 1; }; \
	else \
		codesign --force --options runtime --timestamp --sign "$${APPLE_DEVELOPER_ID_APPLICATION}" "$(MACOS_DMG_PATH)" || { echo "Unable to access the configured Developer ID signing identity."; echo "Check that the certificate private key is available and that KEYCHAIN_PATH is configured correctly."; exit 1; }; \
	fi
	@codesign --verify --strict --verbose=2 "$(MACOS_DMG_PATH)"

verify-macos-dmg-signatures: check-macos ## Mount the DMG and verify the app inside before notarization
	@test -f "$(MACOS_DMG_PATH)" || { echo "Missing signed DMG: $(MACOS_DMG_PATH). Run 'make sign-macos-dmg' first."; exit 1; }
	@test -d "$(MACOS_APP_PATH)" || { echo "Missing source signed app bundle: $(MACOS_APP_PATH)."; exit 1; }
	@MACOS_ENTITLEMENTS_PATH="$(MACOS_ENTITLEMENTS_PATH)" packaging/macos/scripts/verify-dmg-signatures.sh "$(MACOS_DMG_PATH)" "$(MACOS_APP_PATH)"

notarize-macos-dmg: check-macos ## Submit the already signed DMG and wait for Apple notarization acceptance
	@test -f "$(MACOS_DMG_PATH)" || { echo "Missing signed DMG: $(MACOS_DMG_PATH). Run 'make sign-macos-dmg' first."; exit 1; }
	@MACOS_NOTARIZATION_ARTIFACT_DIR="$(MACOS_NOTARIZATION_ARTIFACT_DIR)" packaging/macos/scripts/notarize-dmg.sh submit "$(MACOS_DMG_PATH)"

staple-macos-dmg: check-macos ## Staple the accepted notarization ticket to the DMG
	@test -f "$(MACOS_DMG_PATH)" || { echo "Missing notarized DMG: $(MACOS_DMG_PATH). Run 'make notarize-macos-dmg' first."; exit 1; }
	@MACOS_NOTARIZATION_ARTIFACT_DIR="$(MACOS_NOTARIZATION_ARTIFACT_DIR)" packaging/macos/scripts/notarize-dmg.sh staple "$(MACOS_DMG_PATH)"

verify-macos-notarization: check-macos ## Validate DMG signature, stapling, and Gatekeeper assessment
	@test -f "$(MACOS_DMG_PATH)" || { echo "Missing stapled DMG: $(MACOS_DMG_PATH). Run 'make staple-macos-dmg' first."; exit 1; }
	@MACOS_NOTARIZATION_ARTIFACT_DIR="$(MACOS_NOTARIZATION_ARTIFACT_DIR)" packaging/macos/scripts/notarize-dmg.sh verify "$(MACOS_DMG_PATH)"

checksum-macos-dmg: ## Generate SHA-256 checksum for the final notarized DMG
	@test -f "$(MACOS_DMG_PATH)" || { echo "Missing DMG: $(MACOS_DMG_PATH)."; exit 1; }
	shasum -a 256 "$(MACOS_DMG_PATH)" > "$(MACOS_DMG_SHA256_PATH)"

package-macos-release: package-macos-app postprocess-macos-sqlite-native-libs sign-macos-app verify-macos-signatures package-macos-dmg-from-signed-app sign-macos-dmg verify-macos-dmg-signatures notarize-macos-dmg staple-macos-dmg verify-macos-notarization checksum-macos-dmg ## Build, sign, notarize, staple, verify, and checksum the macOS release DMG

package-macos: package-macos-dmg ## Build an unsigned local macOS app image and development DMG

package-windows: ## Prepare and run Windows packaging helper (PowerShell)
	@echo "Preparing Windows packaging helper. See packaging/windows/README.md and packaging/windows/scripts."
	@if command -v pwsh >/dev/null 2>&1; then \
		pwsh -NoProfile -ExecutionPolicy Bypass -File packaging/windows/scripts/package-windows.ps1 || { echo "PowerShell script failed."; exit 1; }; \
	else \
		echo "PowerShell Core (pwsh) is required to run packaging/windows/scripts/package-windows.ps1. On Windows use 'pwsh' or run the script manually with PowerShell."; exit 1; \
	fi

package-linux: ## Future work: Linux packaging is not implemented yet
	@echo "Linux packaging is not implemented yet. See packaging/linux/README.md."
	@exit 1

run-macos-app: inspect-macos-app ## Open the generated Memoria Vault.app
	open "$(MACOS_APP_PATH)"

inspect-macos-app: ## Verify the generated macOS app bundle looks runnable
	@test "$$(uname -s)" = "Darwin" || { echo "macOS app inspection requires macOS."; exit 1; }
	@test -d "$(MACOS_APP_PATH)" || { echo "Missing app bundle: $(MACOS_APP_PATH). Run 'make package-macos-app' first."; exit 1; }
	@test -d "$(MACOS_APP_PATH)/Contents/runtime" || { echo "Missing bundled runtime in $(MACOS_APP_PATH)."; exit 1; }
	@bash -c '. "$(MACOS_PACKAGING_DIR)/scripts/app-jar.sh"; find_packaged_app_jar "$$1" >/dev/null' _ "$(MACOS_APP_PATH)" || exit 1
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

inspect-macos-signing-readiness: check-macos ## List every Mach-O binary in the app bundle and report signing readiness without requiring signatures
	@packaging/macos/scripts/inspect-signing-readiness.sh inspect "$(MACOS_APP_PATH)"

test-macos-signing-readiness: ## Run shell tests for macOS signing-readiness inspection behavior
	@packaging/macos/scripts/test-signing-readiness.sh

test-macos-release-pipeline: ## Run shell tests for macOS signing, notarization, and Makefile release behavior
	@packaging/macos/scripts/test-release-pipeline.sh

clean-packaging: ## Remove generated packaging artifacts only
	rm -rf "$(DIST_DIR)"

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
