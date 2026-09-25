# If you have folders or files which you wish to persist in between containers,
# you must set them up as volumes in `docker-compose.yml` and use the command
# `docker-compose up` to launch the container.

# Note: You will need to run the `build` gradle task at least once on the server
# before you can run this docker config properly. You will also need to run said
# task whenever you want the docker image to contain the latest changes in your
# code.

# Audit S-16: the code targets JVM 11+ (jvmTarget 11); the old JDK 8 image could not run it.
# Debian/Ubuntu based (glibc) so the bundled Argon2 native library loads.
FROM eclipse-temurin:17-jre

# unzip is needed to unpack the distribution archive below.
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

# Define `directory` as '/app/'
ENV directory /app/

# Set the work directory as ${directory}
WORKDIR ${directory}

# Copy distribution archive
ADD game/build/distributions/game-shadow-*.zip .

# Unzip distribution archive
RUN unzip -q -o *.zip

# Delete distribution archive after unzipping
RUN rm -fv *.zip

# Move distribution files from "game-${version}" to ${WORKDIR} ["."]
RUN mv game*/* .

# Delete "game-${version}" folder as its contents have been moved
# to working directory
RUN rm -rf game*

# Swap the work directory to the "bin" folder
WORKDIR bin

# Documentation on which ports need to be published when the image is run
# The container must be executed with `-p 50015:50015/tcp` (game-port in game.yml).
# For example: docker run -it -p 50015:50015/tcp image
# Never publish 50017: the command server (shutdown/kick/teleport) is for the host only.
EXPOSE 50015/tcp

# Run the main entry point
ENTRYPOINT ./game