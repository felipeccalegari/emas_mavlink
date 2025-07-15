# Encerrar e limpar containers se estiverem rodando
for cname in noetic; do
    if docker ps -q --filter "name=${cname}" | grep -q .; then
        echo "Stopping existing container: ${cname}"
        docker stop "${cname}"
    fi
done

docker run -it -p9090:9090 --rm --net=ros --name noetic maiquelb/embedded-mas-ros:latest \
/bin/bash -c " ((source /opt/ros/noetic/setup.bash &&roslaunch rosbridge_server rosbridge_websocket.launch) & \
             (echo -e '\e[1;33m**** Launching the Docker container. Wait a few seconds...****\e[0m]'  && \
              sleep 5 && \
              source /opt/ros/noetic/setup.bash && \
              (rostopic pub /value1 std_msgs/Int32 0  > /dev/null 2>&1 & \
               rostopic pub /current_time std_msgs/String 'unknown'  > /dev/null 2>&1 &)&&\
              echo -e '\e[1;33m**** Docker container is ready. Start the JaCaMo application****\e[0m]' && \
              tail -f /dev/null  ))"
           
