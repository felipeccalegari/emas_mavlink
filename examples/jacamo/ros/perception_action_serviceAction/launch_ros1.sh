#!/bin/bash

# Encerrar e limpar containers se estiverem rodando
for cname in novnc roscore embedded-mas-example; do
    if docker ps -q --filter "name=${cname}" | grep -q .; then
        echo "Stopping existing container: ${cname}"
        docker stop "${cname}"
    fi
done

# Iniciar noVNC
echo "Starting noVNC..."
docker run -d --rm --net=ros \
    --env="DISPLAY_WIDTH=3000" \
    --env="DISPLAY_HEIGHT=1800" \
    --env="RUN_XTERM=no" \
    --name=novnc -p=8080:8080 theasp/novnc:latest

# Iniciar roscore
echo "Starting roscore..."
docker run -d --rm --net=ros \
    --name roscore osrf/ros:noetic-desktop-full roscore

# Aguardar o roscore estar pronto
echo "Waiting for roscore to be ready..."
until docker exec roscore bash -c "source /opt/ros/noetic/setup.bash && rostopic list" >/dev/null 2>&1; do
    sleep 1
done
echo "roscore is ready."

# Iniciar o container principal SEM --rm para depuração
echo "Starting embedded-mas-example container..."
docker run --rm -d --net=ros \
    --env="DISPLAY=novnc:0.0" \
    --env="ROS_MASTER_URI=http://roscore:11311" \
    --name embedded-mas-example \
    -p 9090:9090 \
    maiquelb/embedded-mas-ros:latest \
    /bin/bash -c "source /opt/ros/noetic/setup.bash && rosrun turtlesim turtlesim_node"

# Aguardar o container iniciar
echo "Waiting for embedded-mas-example to start..."
until [ "$(docker inspect -f '{{.State.Running}}' embedded-mas-example 2>/dev/null)" = "true" ]; do
    sleep 1
done
sleep 1

# Verifica se ainda está rodando antes de executar comandos nele
if docker ps -q --filter "name=embedded-mas-example" | grep -q .; then
    docker exec embedded-mas-example /bin/bash -c "
        source /opt/ros/noetic/setup.bash && \
        echo && echo 'The ROS container is ready.' && \
        echo 'Rosbridge is being launched' && \
        echo 'The Multi-Agent System can be started.' && \
        roslaunch rosbridge_server rosbridge_websocket.launch"
else
    echo "❌ The embedded-mas-example container exited before exec could run. Check logs with:"
    echo "    docker logs embedded-mas-example"
fi

