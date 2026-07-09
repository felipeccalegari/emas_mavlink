//Examples for Ardupilot autopilot system.

/* Takeoff and RTL (Return to Launch) example - Ardupilot System. */
//Main difference versus PX4 is the "set mode" command that needs to be set first to GUIDED and then proceed
// to other commands.

/* !demo_takeoff_rtl.

+!demo_takeoff_rtl <-
    .print("Demo: GUIDED -> arm -> takeoff -> RTL.");
    .set_message_interval(32, 500000, 0, 0, 0, 0, 0); // LOCAL_POSITION_NED at 2 Hz
    .set_message_interval(33, 500000, 0, 0, 0, 0, 0); // GLOBAL_POSITION_INT at 2 Hz
    .wait(1000);
    .set_mode(1, 4, 0);      // custom-mode-enabled=1, ArduCopter GUIDED=4, submode/unused=0
    .wait(1000);
    .print("Mode set to Guided");
    .arming(1);
    .print("Arming");
    .wait(5000);
    .takeoff(0, 0, 0, 0, 0, 0, 4.0);
    .print("Taking off to 4m altitude...");
    .wait(20000);
    .rtl;
    .print("Returning to launch..."). */


/* End of Takeoff and RTL example for Ardupilot. */

/* Start of GUIDED mode - low-level for Ardupilot. */
/* 
gps_gap_ns(1000000000).
last_gps_ns(0).
+globalpositionint(_,Lat,Lon,Alt,RelAlt,_,_,_,_)
  : not home_gps(_,_) & last_gps_ns(Last) & gps_gap_ns(Gap)
  <-
    +home_gps(Lat,Lon);
    .nano_time(Now);
    -last_gps_ns(_);
    +last_gps_ns(Now);
    RelAltM = RelAlt / 1000;
    .print("[LOCAL_FROM_GPS] origin set. x=0 y=0 rel_alt_m=", RelAltM).

+globalpositionint(_,Lat,Lon,Alt,RelAlt,_,_,_,_)
  : home_gps(HomeLat,HomeLon) & last_gps_ns(Last) & gps_gap_ns(Gap)
  <-
    .nano_time(Now);
    if (Now - Last >= Gap) {
      -last_gps_ns(_);
      +last_gps_ns(Now);

      NorthM = (Lat - HomeLat) * 0.011132;
      EastM = (Lon - HomeLon) * 0.007535;
      RelAltM = RelAlt / 1000;

      .print("[LOCAL_FROM_GPS] x_north_m=", NorthM,
             " y_east_m=", EastM,
             " rel_alt_m=", RelAltM)
    }.

!demo_guided_local.

+!demo_guided_local <-
    .print("Demo: GUIDED -> arm -> takeoff -> move to local x=5.0, y=0.0, z=-3.0 -> RTL.");

    // Ask ArduPilot to stream position telemetry for the perception plans.
    //.set_message_interval(32, 500000, 0, 0, 0, 0, 0); // LOCAL_POSITION_NED at 2 Hz
    .set_message_interval(33, 500000, 0, 0, 0, 0, 0); // GLOBAL_POSITION_INT at 2 Hz

    .wait(1000);

    // ArduCopter GUIDED mode: custom-mode-enabled=1, mode=4 (GUIDED), unused=0
    .set_mode(1, 4, 0);
    .wait(1000);

    .arming(1);
    .wait(3000);

    // Takeoff to 3m. ArduPilot generally wants takeoff before local guided movement.
    .takeoff(0, 0, 0, 0, 0, 0, 3.0);
    .print("Taking off...");
    .wait(20000);

    .print("Moving to local NED x=5.0, y=0.0, z=-3.0.");

    // Frame 1 = MAV_FRAME_LOCAL_NED.
    // Type mask 3576 = position only.
    // NED z is negative upward, so z=-3.0 means 3m above origin/home.
    .sp_local(0, 1, 1, 1, 3576, 5.0, 0.0, -3.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    .wait(20000);

    .rtl;
    .print("Returning to launch..."). */
/* End of GUIDED mode - low-level for Ardupilot */

// Used nanoseconds to avoid perceptions spamming in the terminal and affect simulation behavior.
/* hb_gap_ns(5000000000).
lp_gap_ns(7000000000).
att_gap_ns(7000000000).
sys_gap_ns(3000000000).
gps_gap_ns(7000000000).

last_hb_ns(0).
last_lp_ns(0).
last_att_ns(0).
last_sys_ns(0).
last_gps_ns(0).

/* Mavlink HEARTBEAT perception example */
/*
+heartbeat(A,B,C,D,E,F)
  : last_hb_ns(Last) & hb_gap_ns(Gap)
  <-
    .nano_time(Now);
    if (Now - Last >= Gap) {
      -last_hb_ns(_);
      +last_hb_ns(Now);
      .print("1. Heartbeat: type=", A, ", autopilot=", B, ", base_mode=", C, ", custom_mode=", D,
             ", system_status=", E, ", mavlink_version=", F)
    }. */ 
/* End of Mavlink HEARTBEAT perception example */

/* High-level body-relative position example for ArduPilot GUIDED mode ending with LAND.
   Local MAVLink form:
     .setpoint_local(Forward, Right, Up)
   lets the MAVLink bridge compute a fresh absolute LOCAL_NED target from its
   cached LOCAL_POSITION_NED + ATTITUDE telemetry. It does not use global
   position.
*/
/* !demo_guided_relative_cross_land.

+!demo_guided_relative_cross_land
  <-
    .nano_time(T1);
    .print("Time1: ", T1);
    .request_data_stream(6, 10, 1); // MAV_DATA_STREAM_POSITION
    .set_message_interval(32, 100000, 0, 0, 0, 0, 0); // LOCAL_POSITION_NED at 10 Hz
    .set_message_interval(30, 100000, 0, 0, 0, 0, 0); // ATTITUDE at 10 Hz
    .print("Requested LOCAL_POSITION_NED and ATTITUDE streams.");
    .wait(3000);

    .print("ArduPilot GUIDED - using body-offset setpoint_local(Forward, Right, Up).");

    .set_mode(1, 4, 0); // ArduCopter GUIDED: custom-mode-enabled=1, mode=4, unused=0
    .wait(1000);

    .arming(1);
    .wait(3000);

    .takeoff_cmd(0.0, 0.0, 0.0, 0.0, 2.0);
    .print("Taking off to 2m and waiting for altitude to stabilize...");
    .reset_setpoint_local_reference;
    .print("Locked setpoint_local reference yaw after takeoff.");
    .wait(10000);

    .print("Command: move 2 m forward.");
    .setpoint_local(2.0, 0.0, 0.0);
    .wait(10000);
    .print("Command: move 2 m backward to return.");
    .setpoint_local(-2.0, 0.0, 0.0);
    .wait(10000);

    .print("Command: move 2 m right.");
    .setpoint_local(0.0, 2.0, 0.0);
    .wait(10000);
    .print("Command: move 2 m left to return.");
    .setpoint_local(0.0, -2.0, 0.0);
    .wait(10000);

    .print("Command: move 2 m left.");
    .setpoint_local(0.0, -2.0, 0.0);
    .wait(10000);
    .print("Command: move 2 m right to return.");
    .setpoint_local(0.0, 2.0, 0.0);
    .wait(10000);

    .print("Command: move 2 m backward.");
    .setpoint_local(-2.0, 0.0, 0.0);
    .wait(10000);
    .print("Command: move 2 m forward to return.");
    .setpoint_local(2.0, 0.0, 0.0);
    .wait(10000);

    .set_mode(1, 9, 0); // ArduCopter LAND: custom-mode-enabled=1, mode=9, unused=0
    .wait(200);
    .nano_time(T2);
    .print("Time2: ", T2);
    .print("Landing and finishing GUIDED and LAND demo."). */
/* End of high-level relative position example for ArduPilot GUIDED mode ending with LAND. */

/* LOCAL_POSITION_NED monitor: prints local position every 5 seconds. */
lp_print_gap_ns(5000000000).
last_lp_print_ns(0).

+localpositionned(_,X,Y,Zned,_,_,_)
  : lp_print_gap_ns(Gap) & last_lp_print_ns(Last)
  <-
    .nano_time(Now);
    if (Now - Last >= Gap) {
      -last_lp_print_ns(_);
      +last_lp_print_ns(Now);
      Alt = -Zned;
      .print("[LOCAL_POSITION_NED] x=", X, " y=", Y, " z_ned=", Zned, " alt=", Alt)
    }.

/* End of LOCAL_POSITION_NED monitor. */

/* Beginning of camera examples for ArduPilot. */

/* camera_capture_target_count(20).
pending_camera_capture_iteration(0).
pending_camera_capture_flight_iteration(0). */

/* ArduPilot example 4: ACK-driven camera capture counter. */
/* !demo_camera_capture.

+!demo_camera_capture <-
    -pending_camera_capture_iteration(_);
    +pending_camera_capture_iteration(1);
    .print("Starting MAVLink ACK-driven camera capture demo with 20 iterations.");
    .wait(3000);
    .camera_capture(0, 0, 1, 1, 0, 0, 0).

+command_ack(2000, 0, Stamp, SysId, CompId)
  : pending_camera_capture_iteration(Current) & camera_capture_target_count(Target)
  <-
    .print("Example 4 ACK accepted at stamp=", Stamp,
           " from sys=", SysId, " comp=", CompId,
           " for iteration ", Current, " of ", Target, ".");
    -command_ack(2000, 0, Stamp, SysId, CompId);
    if (Current < Target) {
      Next = Current + 1;
      -pending_camera_capture_iteration(_);
      +pending_camera_capture_iteration(Next);
      .camera_capture(0, 0, 1, Next, 0, 0, 0)
    } else {
      -pending_camera_capture_iteration(_);
      +pending_camera_capture_iteration(0);
      .print("Example 4 finished at stamp=", Stamp)
    }.

+command_ack(2000, Result, Stamp, SysId, CompId)
  : pending_camera_capture_iteration(Current) & Current > 0 & Result \== 0
  <-
    .print("Example 4 camera ACK not accepted: command=2000",
           ", result=", Result,
           ", stamp=", Stamp,
           ", sys=", SysId,
           ", comp=", CompId,
           ", stopping at iteration ", Current, ".");
    -pending_camera_capture_iteration(_);
    +pending_camera_capture_iteration(0);
    -command_ack(2000, Result, Stamp, SysId, CompId).
 */
/* ArduPilot example 5: GUIDED takeoff, 20 ACK-driven captures, then land. */
/* !demo_camera_capture_flight.

+!demo_camera_capture_flight <-
    .print("Starting MAVLink flight + ACK-driven camera capture demo.");
    .set_mode(1, 4, 0); // ArduCopter GUIDED
    .wait(1000);
    .arming(1);
    .wait(5000);
    .takeoff(0, 0, 0, 0, 0, 0, 3.0);
    .print("Takeoff command sent to 3m.");
    .wait(12000);
    -pending_camera_capture_flight_iteration(_);
    +pending_camera_capture_flight_iteration(1);
    .print("Beginning 20 ACK-driven captures.");
    .camera_capture(0, 0, 1, 1, 0, 0, 0).

+command_ack(2000, 0, Stamp, SysId, CompId)
  : pending_camera_capture_flight_iteration(Current) & camera_capture_target_count(Target)
  <-
    .print("Example 5 ACK accepted at stamp=", Stamp,
           " from sys=", SysId, " comp=", CompId,
           " for iteration ", Current, " of ", Target, ".");
    -command_ack(2000, 0, Stamp, SysId, CompId);
    if (Current < Target) {
      Next = Current + 1;
      -pending_camera_capture_flight_iteration(_);
      +pending_camera_capture_flight_iteration(Next);
      .camera_capture(0, 0, 1, Next, 0, 0, 0)
    } else {
      -pending_camera_capture_flight_iteration(_);
      +pending_camera_capture_flight_iteration(0);
      .print("Example 5 finished at stamp=", Stamp);
      .wait(3000);
      .land(0, 0, 0, 0, 0, 0, 0.0);
      .print("Landing after capture sequence.")
    }.

+command_ack(2000, Result, Stamp, SysId, CompId)
  : pending_camera_capture_flight_iteration(Current) & Current > 0 & Result \== 0
  <-
    .print("Example 5 camera ACK not accepted: command=2000",
           ", result=", Result,
           ", stamp=", Stamp,
           ", sys=", SysId,
           ", comp=", CompId,
           ", stopping at iteration ", Current, ".");
    -pending_camera_capture_flight_iteration(_);
    +pending_camera_capture_flight_iteration(0);
    -command_ack(2000, Result, Stamp, SysId, CompId). */

/* End of camera examples for ArduPilot. */
