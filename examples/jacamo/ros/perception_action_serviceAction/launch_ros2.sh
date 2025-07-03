(docker ps -q --filter "name=novnc" | grep -q . && docker stop novnc || true) &&\
(docker ps -q --filter "name=roscore" | grep -q . && docker stop roscore || true) &&\
(docker ps -q --filter "name=embedded-mas-example" | grep -q . && docker stop embedded-mas-example || true) &&\
sudo docker run -d --rm --net=ros --env="DISPLAY_WIDTH=3000" --env="DISPLAY_HEIGHT=1800" --env="RUN_XTERM=no" --name=novnc -p=8080:8080 theasp/novnc:latest  && \
sudo docker run -d --net=ros --name roscore --rm osrf/ros:noetic-desktop-full roscore && \
sudo docker run -d --net=ros --env="DISPLAY=novnc:0.0" --env="ROS_MASTER_URI=http://roscore:11311" --rm --name embedded-mas-example -p9090:9090 maiquelb/embedded-mas-ros2:latest /bin/bash -c "source /opt/ros/humble/setup.bash && ros2 run turtlesim turtlesim_node" & \
(until sudo docker exec embedded-mas-example /bin/bash -c "echo '***** ROS container is ready *****'" 2>/dev/null; do echo "waiting for ROS container to start..."; sleep 1; done  && \
sudo docker exec  embedded-mas-example /bin/bash -c "source /opt/ros/humble/setup.bash && ros2 launch rosbridge_server rosbridge_websocket_launch.xml")
