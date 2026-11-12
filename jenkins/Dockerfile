FROM jenkins/jenkins:lts-jdk21

USER root
# client Docker: la pipeline lo usa per costruire le immagini parlando col Docker dell'host
RUN apt-get update \
 && apt-get install -y --no-install-recommends docker.io \
 && rm -rf /var/lib/apt/lists/*

# resta root: serve per accedere al socket Docker montato (accettabile solo per pratica locale)
