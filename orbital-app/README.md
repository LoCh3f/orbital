Orbital Compose Desktop app

This module builds a Compose Multiplatform desktop application targeting Windows, macOS, and Linux.

Note about Docker: Desktop apps are installed and run natively on users' machines. There is no Dockerfile included for the desktop client because containerizing a GUI application is not a supported distribution model for end users. The backend services (gateway, market-service, news-service) remain containerized and can be run via docker-compose for local or CI environments.

If a CI artifact is desired (e.g., headless JAR for smoke tests), create a dedicated CI Dockerfile under ci/docker and document its use.