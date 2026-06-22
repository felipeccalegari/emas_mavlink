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

/* gps_gap_ns(1000000000).
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

/* "High-level" body-relative position example for ArduPilot GUIDED mode. */
/* !demo_offboard_body_relative_position.

+!demo_offboard_body_relative_position
  : not nav_pose_local(_,_,_,_) & not guided_pose_stream_requested
  <-
    +guided_pose_stream_requested;
    .set_message_interval(30, 500000, 0, 0, 0, 0, 0); // ATTITUDE at 2 Hz
    .set_message_interval(32, 500000, 0, 0, 0, 0, 0); // LOCAL_POSITION_NED at 2 Hz
    .print("Waiting for local pose before GUIDED body-relative demo...");
    .wait(500);
    !demo_offboard_body_relative_position.

+!demo_offboard_body_relative_position
  : not nav_pose_local(_,_,_,_) & guided_pose_stream_requested
  <-
    .wait(500);
    !demo_offboard_body_relative_position.

+!demo_offboard_body_relative_position
  : nav_pose_local(_,_,_,_)
  <-
    .print("Demo: GUIDED mode using high-level sp_local(Forward, Right, Up).");

    // ArduCopter GUIDED mode: custom-mode-enabled=1, mode=4 (GUIDED), unused=0
    .set_mode(1, 4, 0);
    .wait(1000);

    .arming(1);
    .wait(3000);

    .takeoff(0, 0, 0, 0, 0, 0, 3.0);
    .print("Taking off to 3m and waiting for altitude to stabilize...");
    .wait(20000);

    .sp_local(0.0, -3.0, 0.0); // move 3 m to the drone's current left
    .wait(5000);
    .sp_local(2.0, 0.0, 0.0); // then move 2 m forward from the drone's current heading
    .wait(5000);
    .sp_local(2.0, 2.0, 0.0); // forward-right
    .wait(5000);
    .sp_local(0.0, 3.0, 0.0); // move 3 m to the drone's current right
    .wait(5000);
    .sp_local(-2.0, 0.0, 0.0); // move 2 m backward from the drone's current heading
    .wait(5000);
    .rtl;
    .wait(200);
    .print("Returning to launch and finishing GUIDED body-relative demo."). */
/* End of high-level body-relative position example for ArduPilot GUIDED mode. */

/* High-level relative position example for ArduPilot GUIDED mode ending with LAND. */
!demo_guided_relative_cross_land.

+!demo_guided_relative_cross_land
  : not nav_pose_local(_,_,_,_) & not guided_pose_stream_requested
  <-
    +guided_pose_stream_requested;
    .set_message_interval(30, 500000, 0, 0, 0, 0, 0); // ATTITUDE at 2 Hz
    .set_message_interval(32, 500000, 0, 0, 0, 0, 0); // LOCAL_POSITION_NED at 2 Hz
    .print("Waiting for local pose before GUIDED relative LAND demo...");
    .wait(500);
    !demo_guided_relative_cross_land.

+!demo_guided_relative_cross_land
  : not nav_pose_local(_,_,_,_) & guided_pose_stream_requested
  <-
    .wait(500);
    !demo_guided_relative_cross_land.

+!demo_guided_relative_cross_land
  : nav_pose_local(_,_,_,_)
  <-
    .print("Demo: take off 2m, move with high-level relative offsets, then land.");

    .set_mode(1, 4, 0); // custom-mode-enabled=1, mode=4 (GUIDED), unused=0
    .wait(1000);

    .arming(1);
    .wait(3000);

    .takeoff(0, 0, 0, 0, 0, 0, 2.0);
    .print("Taking off to 2m and waiting for altitude to stabilize...");
    .wait(20000);

    .reset_sp_local_reference;
    .print("Locked sp_local reference yaw after takeoff.");
    .wait(3000);

    .print("Command: move 2m forward.");
    .sp_local(2.0, 0.0, 0.0); // 2 m forward
    .wait(10000);
    .print("Command: move 2m backward to return.");
    .sp_local(-2.0, 0.0, 0.0); // back 2 m
    .wait(10000);

    .print("Command: move 2m right.");
    .sp_local(0.0, 2.0, 0.0); // 2 m right
    .wait(10000);
    .print("Command: move 2m left to return.");
    .sp_local(0.0, -2.0, 0.0); // back 2 m
    .wait(10000);

    .print("Command: move 2m left.");
    .sp_local(0.0, -2.0, 0.0); // 2 m left
    .wait(10000);
    .print("Command: move 2m right to return.");
    .sp_local(0.0, 2.0, 0.0); // back 2 m
    .wait(10000);

    .print("Command: move 2m backward.");
    .sp_local(-2.0, 0.0, 0.0); // 2 m backward
    .wait(10000);
    .print("Command: move 2m forward to return.");
    .sp_local(2.0, 0.0, 0.0); // back 2 m
    .wait(10000);

    .print("Command: land now.");
    .land(0, 0, 0, 0, 0, 0, 0.0);
    .wait(200);
    .print("Landing and finishing GUIDED high-level LAND demo.").
/* End of high-level relative position example for ArduPilot GUIDED mode ending with LAND. */
