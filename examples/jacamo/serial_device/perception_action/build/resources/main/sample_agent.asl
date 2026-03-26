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
    .mission_item(47.3979710, 8.5461637, 4.0);
    .mission_item(47.3980210, 8.5461637, 4.0);
    .mission_item(47.3980210, 8.5462237, 4.0);
    .wait(500);
    .arming(1);
    .wait(500);
    .mission_start(0, 2);
    .wait(20000);
    .rtl.

