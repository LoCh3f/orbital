.PHONY: pdf diagrams

TECTONIC ?= tectonic
LATEXMK ?= latexmk
MMDC ?= mmdc

# Existing PNGs are checked in; a PDF build does not require Mermaid or Chromium.
pdf:
	@if command -v "$(TECTONIC)" >/dev/null 2>&1; then \
		"$(TECTONIC)" --untrusted --keep-intermediates --keep-logs main.tex; \
	elif command -v "$(LATEXMK)" >/dev/null 2>&1; then \
		"$(LATEXMK)" -pdf -no-shell-escape -interaction=nonstopmode -halt-on-error main.tex; \
	else \
		printf '%s\n' 'Install Tectonic or latexmk with a TeX distribution, then rerun make pdf.' >&2; \
		exit 1; \
	fi

# Mermaid CLI 11.16.0 was used for the reviewed figures. Chromium is also required.
# Set PUPPETEER_EXECUTABLE_PATH when using an existing Chromium installation.
diagrams:
	@set -e; for source in figures/*.mmd; do \
		"$(MMDC)" -c mermaid-config.json -i "$$source" -o "$${source%.mmd}.png" -b white -s 2; \
	done
