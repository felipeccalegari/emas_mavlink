#!/bin/bash

CONTAINER_NAME="noetic"
IMAGE_NAME="maiquelb/embedded-mas-ros2:latest"

# Para o container se estiver em execução
if docker ps -q --filter "name=${CONTAINER_NAME}" | grep -q .; then
    echo "Stopping existing container: ${CONTAINER_NAME}"
    docker stop "${CONTAINER_NAME}"
fi

# Executa o container e inicia o ROS 2 + lógica personalizada
docker run -it --rm --net=ros -p9090:9090 --name "${CONTAINER_NAME}" "${IMAGE_NAME}" \
/bin/bash -c "
# Inicia rosbridge em background
ros2 launch rosbridge_server rosbridge_websocket_launch.xml &

# Aguarda inicialização
sleep 3

# Mensagem amarela no terminal
echo -e '\033[1;33m**** Docker container is ready. Start the JaCaMo application****\033[0m'

# Aguarda até haver pelo menos um assinante no tópico /value1
while true; do
    count=\$(ros2 topic info /value1 | grep 'Publisher count:' | awk '{print \$3}')
    if [ \"\$count\" != \"0\" ]; then
        echo 'Subscriber detected. Publishing initial messages...'
        break
    fi
    sleep 1
done

# Publica as mensagens com segurança, já que há assinante
ros2 topic pub --once /value1 std_msgs/Int32 '{\"data\": 0}'
ros2 topic pub --once /current_time std_msgs/String '{\"data\": \"unknown\"}'

# Mantém o terminal ativo
exec bash
"

