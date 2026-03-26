/* !start.

+!start <-
    -awaiting_z(_);
    -step_transitioning(_);
    .print("Starting TAKEOFF + REPOSITION Z counter...");
    .arming(1,21196);
    .wait(300);
    -+awaiting_z(1.0);
    .takeoff(0, 0, 0, 0, 47.3979710, 8.5461637, 1.0).

+localpositionned(_,_,_,Zned,_,_,_)
  : awaiting_z(Target)
    & not step_transitioning(true)
  <-
    -+step_transitioning(true);
    Z = -Zned; //Needed because of the NED (North-East-Down) frame, so need to adjust Z value; In Mavros its not necessary, already done under the hood.
    if (Z >= (Target - 0.35) & Z <= (Target + 0.35)) {
        -awaiting_z(_);
        .nano_time(T);
        .print(T,";",Z);
        if (Target < 10.0) {
            Next = Target + 1.0;
            -+awaiting_z(Next);
            .reposition(-1, 1, 0, 0, 47.3979710, 8.5461637, Next);
        } else {
            .print("Reposition Z counter finished.");
        };
    };
    -step_transitioning(_). */

/* Visible MAVLink examples for PX4 SITL. */

/* !demo_takeoff_land.
+!demo_takeoff_land <-
    .print("Demo: arm -> takeoff -> land.");
    .arming(1);
    .wait(500);
    .takeoff(0, 0, 0, 0, 47.3979710, 8.5461637, 3.0);
    .wait(8000);
    .land(0, 0, 0, 0, 47.3979710, 8.5461637, 0.0). */

/* !demo_takeoff_rtl.
+!demo_takeoff_rtl <-
    .print("Demo: arm -> takeoff -> RTL.");
    .arming(1);
    .wait(500);
    .takeoff(0, 0, 0, 0, 47.3979710, 8.5461637, 4.0);
    .wait(8000);
    .rtl. */

/* !demo_square.
+!demo_square <-
    .print("Demo: arm -> takeoff -> 3 reposition steps -> land.");
    .arming(1);
    .wait(500);
    .takeoff(0, 0, 0, 0, 47.3979710, 8.5461637, 4.0);
    .wait(7000);
    .reposition(-1, 1, 0, 0, 47.3979710, 8.5461637, 4.0);
    .wait(4000);
    .reposition(-1, 1, 0, 0, 47.3980210, 8.5461637, 4.0);
    .wait(4000);
    .reposition(-1, 1, 0, 0, 47.3980210, 8.5462237, 4.0);
    .wait(4000);
    .land(0, 0, 0, 0, 47.3980210, 8.5462237, 0.0). */

!demo_mission.
+!demo_mission <-
    .print("Demo: upload a short mission and start AUTO mission.");
    .mission_clear;
    .wait(500);
    .mission_item(47.3979710, 8.5461637, 4.0); //mission 0
    .mission_item(47.3980210, 8.5461637, 4.0); //mission 1
    .mission_item(47.3980210, 8.5462237, 4.0); //mission 2
    .wait(500);
    .arming(1);
    .wait(500);
    .mission_start(0, 2); //(first item (0), last item (2)) - if 2nd parameter is -1, it'll run until the last added item (treated on class-side).
    .wait(20000);
    .rtl.

/* Common MAVLink perception examples.*/

// Used nanoseconds to avoid perceptions spamming in the terminal and affect simulation behavior.
hb_gap_ns(5000000000).
lp_gap_ns(7000000000).
att_gap_ns(7000000000).
sys_gap_ns(3000000000).
gps_gap_ns(7000000000).

last_hb_ns(0).
last_lp_ns(0).
last_att_ns(0).
last_sys_ns(0).
last_gps_ns(0).

+heartbeat(A,B,C,D,E,F)
  : last_hb_ns(Last) & hb_gap_ns(Gap)
  <-
    .nano_time(Now);
    if (Now - Last >= Gap) {
      -last_hb_ns(_);
      +last_hb_ns(Now);
      .print("1. Heartbeat: type=", A, ", autopilot=", B, ", base_mode=", C, ", custom_mode=", D,
             ", system_status=", E, ", mavlink_version=", F)
    }.

+localpositionned(X,Y,Zned,Vx,Vy,Vz)
  : last_lp_ns(Last) & lp_gap_ns(Gap)
  <-
    .nano_time(Now);
    if (Now - Last >= Gap) {
      -last_lp_ns(_);
      +last_lp_ns(Now);
      Alt = -Zned;
      .print("Local position NED: x=", X, ", y=", Y, ", alt=", Alt,
             ", vx=", Vx, ", vy=", Vy, ", vz=", Vz)
    }.

+attitude(Roll,Pitch,Yaw,_,_,_,_)
  : last_att_ns(Last) & att_gap_ns(Gap)
  <-
    .nano_time(Now);
    if (Now - Last >= Gap) {
      -last_att_ns(_);
      +last_att_ns(Now);
      .print("Attitude: roll=", Roll, ", pitch=", Pitch, ", yaw=", Yaw)
    }.

+sysstatus(_,_,_,_,_,_,_,BatteryRemaining,_,_,_,_,_)
  : last_sys_ns(Last) & sys_gap_ns(Gap)
  <-
    .nano_time(Now);
    if (Now - Last >= Gap) {
      -last_sys_ns(_);
      +last_sys_ns(Now);
      .print("Battery remaining: ", BatteryRemaining)
    }.

+statustext(Severity,Text,_,_)
  <-
    .print("PX4 status [", Severity, "]: ", Text).

+globalpositionint(_,Lat,Lon,Alt,RelAlt,_,_,_,_)
  : last_gps_ns(Last) & gps_gap_ns(Gap)
  <-
    .nano_time(Now);
    if (Now - Last >= Gap) {
      -last_gps_ns(_);
      +last_gps_ns(Now);
      .print("[GPS] lat=",Lat," lon=",Lon," alt=",Alt," relAlt=",RelAlt)
    }.
