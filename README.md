# emas_mavlink
This project consists of an extension of [Embedded-Mas](github.com/embedded-mas/embedded-mas) framework so [Jason](https://github.com/jomifred/jason) agents can work with MAVLink protocol using a customized class called "Mavlink4EmbeddedMas" using DroneFleet 1.1.11 library.

### Class Overview:

In general, the Mavlink4EmbeddedMas class:
1) Opens a serial connection to the autopilot with hard-coded *SystemId* and *ComponentId* parameters (1 and 1, respectively);
2) Sends MAVLink *Heartbeat* messages continuously to make sure the connection with the autopilot stays alive; 
3) Requests default telemetry streams; 
4) Decodes incoming MAVLink messages from the autopilot into JSON format beliefs for the Jason agent;
5) Caches latest telemetry by message type;
6) Sends common command messages through *COMMAND_LONG* or *COMMAND_INT*;

The class also has special support for most common MAVLink messages used, such as:
- Local NED (North-East-Down) setpoints with *SET_POSITION_LOCAL_NED*;
- Mission buffering, upload and start;
- MAVLink parameter set and read.

### Environment:
- Ubuntu 24.04 machine running PX4 v1.16 with Gazebo
- Java JDK version = 21
- Raspberry Pi 5 (Jason Agents) <-Serial-> Raspberry Pi 4 (Send/Receive messages) <-UDP-> Ubuntu/PX4 Simulation
- Simulation startup scripts were adapted to connect with Raspberry Pi 4 IP and then started with: `MAV_BROADCAST=1 make px4_sitl gz_x500`

- Users can run via a "virtual serial" with *Socat* tool (`sudo apt install socat`) with the following commands:
`socat -d -d pty,raw,echo=0,link=/dev/ttyV1,wait-slave udp:127.0.0.1:14560,sourceport=14561`

`make px4_sitl gz_x500`

*After PX4 loads and the 'Ready for Takeoff' messages shows up, run in PX4's terminal:*
`mavlink start -u 14560 -o 14561 -t 127.0.0.1 -m onboard -r 40000`

### Agent side:
**Examples**: ./examples/jacamo/serial_device/perception_action/src/agt
- Running the agent:

`cd examples/jacamo/serial_device/perception_action/`

`./gradlew run`

**Actions** tested:

| Action | MAVLink code | MAVLink parameters |
|---|---|---|
| Arming | `.arming(1)` | `1`: arm; `21196`: force arm |
| Set Mode* | `.set_mode(1,6,0)` | `1`: custom; `6`: PX4 Offboard; `0`: no submode |
| Takeoff | `.takeoff(0,0,0,0,lat,lon,alt)` | param1, param2, param3, yaw, latitude, longitude, altitude |
| Land | `.land(0,0,0,0,lat,lon,alt)` | abort altitude, precision land mode, unused param, yaw, latitude, longitude, altitude |
| Return to Launch | `.rtl` | No parameters |
| Reposition | `.reposition(-1,1,0,0,lat,lon,alt)` | ground speed, bitmask, reserved, yaw, latitude, longitude, altitude |
| Mission Clear | `.mission_clear` | No parameters |
| Mission Upload/Item | `.mission_item(lat,lon,alt)` | latitude, longitude, altitude |
| Mission Start | `.mission_start(0,-1)` | first item, last item |
| Local setpoint | `.sp_local(Forward,Right,Up)` | body-relative forward, right, up in meters |
| Parameter Set | `.param_set("MPC_Z_VEL_MAX_UP",value)` | parameter name, value |

* *A list of custom modes for PX4 can be found in the [PX4 repository](https://github.com/PX4/PX4-Autopilot/blob/main/src/modules/commander/px4_custom_mode.h) or in the [forum](https://discuss.px4.io/t/mav-cmd-do-set-mode-all-possible-modes/8495/7).

- The extension works with many commands that uses _MAV_CMD_*_ dialects from MAVLink _common.xml_ as long as they're present in the MavCmd enum from DroneFleet.

- Waypoints are set up via Missions (internal action "_.mission_item_") only using Lat (Degrees), Lon (Degrees) and Alt (Meters) parameters, which by default uses its own standards but the Mavlink4EmbeddedMas class will automatically multiply the Lat and Lon coordinates by multiplier so Agent Programmer only needs to insert normal degrees (eg. 45.273333) instead of a large number. Also the code uploads mission items as *MISSION_ITEM_INT*, where a *Takeoff* is the first item, and the rest are considered _Waypoints_.

- Adding new agent actions: in the sample_agent.yaml, user can add "*actionName*", which will be the action used in the .asl file and the respective Mavlink code in "*actuationName*".

### Perceptions summary:
Incoming MAVLink telemetry is also converted automatically into Jason perceptions. The perception name is derived from the MAVLink message type by removing `_`, joining the words, and converting the result to lowercase. For example, `GLOBAL_POSITION_INT` becomes `globalpositionint(...)` and `LOCAL_POSITION_NED` becomes `localpositionned(...)`. To discover the exact argument order for a perception, the easiest approach is to temporarily add a debug plan in the `.asl` file with variables for all arguments, print them, and then refine the final perception handler with only the fields you need.



| Perception | MAVLink syntax - Jason Perception| MAVLink parameters |
|---|---|---|
| State / heartbeat | `heartbeat(Type,Autopilot,BaseMode,CustomMode,SystemStatus,Version)` | vehicle type, autopilot type, base mode, custom mode, system status, MAVLink version |
| Local position | `localpositionned(X,Y,Zned,Vx,Vy,Vz)` | `X,Y,Zned`: local NED position; `Vx,Vy,Vz`: local NED velocity |
| Attitude / IMU | `attitude(Roll,Pitch,Yaw,_,_,_,_)` | `Roll`: roll; `Pitch`: pitch; `Yaw`: yaw; remaining fields ignored |
| Battery | `battery(PNorm,PRaw,VoltageV,CurrentA)` | `PNorm`: normalized percentage; `PRaw`: raw percentage; `VoltageV`: voltage; `CurrentA`: current |
| System status | `sysstatus(...,BatteryRemaining,...)` | `BatteryRemaining`: battery percentage; other fields ignored |
| Status text | `statustext(Severity,Text,_,_)` | `Severity`: message severity; `Text`: PX4/autopilot message; remaining fields ignored |
| Global position | `globalpositionint(_,Lat,Lon,Alt,RelAlt,_,_,_,_)` | `Lat`: latitude; `Lon`: longitude; `Alt`: altitude; `RelAlt`: relative altitude |
| Relative altitude | Included in `globalpositionint(...,RelAlt,...)` | `RelAlt`: relative altitude |
| Parameter update | `paramvalue(ID,Value,Type,Count,Index)` | `ID`: parameter name; `Value`: value; `Type`: type; `Count`: total count; `Index`: parameter index |
| Navigation pose | `nav_pose_local(X,Y,Z,Yaw)` | `X,Y,Z`: local position; `Yaw`: heading |


### Limitations
1) **Hard coded parameters**: important parameters such as *SystemID* and *ComponentID* are currently hard-coded in the .java class file. In future works, the idea would be to implement a custom parameter feature in the agent's .yaml configuration file.
2) **Beliefs dependencies based on DroneFleet internals**: even though DroneFleet base their message parameters in the MAVLink's *common.xml* dialect, the message names are internally defined in the library's internal classes. In future work, this would be changed to allow the developer to customize the agent's **perceptions** in the .yaml configuration file, similarly to how it's done with the agent's actions.
3) **No internal *COMMAND_ACK* handling**: currently, even if the class succesfully parses a message being sent to the autopilot, it does not necessarily mean that the autopilot would accept. The class does not track a *COMMAND_ACK* message from the autopilot and expose as an agent belief and the agent wouldn't know if the requested command was accepted. In some cases this could be worked around in the agent's .asl file with a *Status* plan, for example, or *Local Position Ned/Global Position Int* for coordinates-based messages to be treated as perceptions. They do not fully replace a *COMMAND_ACK*, though. A better solution would be to build a proper *COMMAND_ACK* message as belief internally in the *Mavlink4EmbeddedMas* class to also match the ACK messages to the commands sent and publish cleaner beliefs in the format, for example, "*command_ack(Command, Result)*".
4) **System modularity**: the current version assumes that any MAVLink message that is defined in DroneFleet's internals would be decoded/encoded. However, many of these messages are not considered "high-level", requiring parameters that may not be trivial for the agent's developer. For example, when the agent wishes to send setpoint of coordinates, in MAVLink it would have to include not only X, Y, Z coordinates, but also velocity, accelaration (both in x, y and z directions) and other flags, which are not trivial. Even though certain common messages have been already customized inside the *Mavlink4EmbeddedMas* class, it would be ideal to be able to handle this type of customization externally. In the ROS/MAVROS approach, the Embedded-MAS framework allows the developer to customize a .java class to request customized services and publish in topics with custom parameters. For future works, an idea would be to apply a similar idea for MAVLink approach as well. This way, the agent's .asl file would be cleaner with a "high-level" code, while this extra .java class would deal with non-trivial parameters.
