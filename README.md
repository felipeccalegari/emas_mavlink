# emas_mavlink
Customized NRJ4EmbeddedMas and SerialDevice classes from Embedded-Mas framework to work with MAVLink protocol using DroneFleet 1.11.1 library.

### Environment:
- Ubuntu 24.04 machine running PX4 v1.17 with Gazebo
- Raspberry Pi 5 (Jason Agents) <-Serial-> Raspberry Pi 4 (Send/Receive messages) <-UDP-> Ubuntu/PX4 Simulation
- Simulation startup scripts were adapted to connect with Raspberry Pi 4 IP and then started with: `MAV_BROADCAST=1 make px4_sitl gz_x500`

### Adapted Classes:
- embedded-mas/src/main/java/embedded/mas/bridges/javard/NRJ4EmbeddedMas.java
- embedded-mas/src/main/java/embedded/mas/bridges/jacamo/SerialDevice.java

### Agent side:
**Example**: embedded-mas/examples/jacamo/serial_device/perception_action/sample_agent.asl

- Agent can:
  - Arm.
  - Set modes (Tested: AUTO.TAKEOFF and AUTO.MISSION).
  - Add waypoints to missions (Mission mode).
  - Start mission.
  - Works with most commands that uses _MAV_CMD_*_ dialects from MAVLink _common.xml_.

- Agent can't:
  - Receive messages from serial/PX4 (yet) - perceptions.
  - Set mode with OFFBOARD option.
  - Commands from MAVLink dialect that don't start with "MAV_CMD_*" need to be tested.

- After PX4 v1.15, they stopped using MAV_CMD_DO_SET_MODE to be replaced by STANDARD, but can't be found on _common.xml_ dialects, therefore, i'm using SET_MODE (which is deprecated) to work properly with different modes.

- Waypoints are set up via Missions (internal action "_.mission_item_") only using Lat (Degrees), Lon (Degrees) and Alt (Meters) parameters, which by default uses its own standards but the NRJ class will automatically multiply the Lat and Lon coordinates by multiplier so Agent Programmer only needs to insert normal degrees (eg. 45.273333) instead of a large number. Also NRJ class automatically converts the "Missions" into _Waypoint_ MAVLink command.
