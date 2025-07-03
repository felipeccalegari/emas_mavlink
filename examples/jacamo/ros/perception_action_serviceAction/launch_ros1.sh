(docker ps -q --filter "name=novnc" | grep -q . && docker stop novnc || true) &&\
(docker ps -q --filter "name=roscore" | grep -q . && docker stop roscore || true) &&\
(docker ps -q --filter "name=embedded-mas-example" | grep -q . && docker stop embedded-mas-example || true) &&\
 docker run -d --rm --net=ros --env="DISPLAY_WIDTH=3000" --env="DISPLAY_HEIGHT=1800" --env="RUN_XTERM=no" --name=novnc -p=8080:8080 theasp/novnc:latest && \
 docker run -d --net=ros --name roscore --rm osrf/ros:noetic-desktop-full roscore && \
 docker run -d --net=ros --env="DISPLAY=novnc:0.0" --env="ROS_MASTER_URI=http://roscore:11311" \
    --rm --name embedded-mas-example -p9090:9090 maiquelb/embedded-mas-ros:latest /bin/bash -c "source /opt/ros/noetic/setup.bash && rosrun turtlesim turtlesim_node" && \
until [ "$(docker inspect -f '{{.State.Running}}' embedded-mas-example 2>/dev/null)" = "true" ]; do
    echo "waiting for ROS container to start..."
    sleep 1
done  && \
sleep 1 && \
 docker exec  embedded-mas-example /bin/bash -c "source /opt/ros/noetic/setup.bash && echo && echo && echo 'The ROS container is ready.' && echo 'Rosbridge is being launched' && echo 'The Multi-Agent System can be started. ' && echo && echo && roslaunch rosbridge_server rosbridge_websocket.launch "
