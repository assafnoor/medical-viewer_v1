#!/usr/bin/env bash
set -e
JAR="target/medical-viewer-1.0.0.jar"
if [ ! -f "$JAR" ]; then
  echo "Building..."
  mvn clean package -q
fi
exec java \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens=java.desktop/java.awt=ALL-UNNAMED \
  --add-opens=java.desktop/java.awt.image=ALL-UNNAMED \
  --add-opens=java.desktop/sun.awt=ALL-UNNAMED \
  --add-opens=java.desktop/sun.java2d=ALL-UNNAMED \
  --add-opens=javafx.fxml/javafx.fxml=ALL-UNNAMED \
  -jar "$JAR" "$@"
