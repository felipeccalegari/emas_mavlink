//!start.
/* +!start
   <- .print("Sending message from Pi 5 to Pi 4...");
      .arming(1,0);
      .wait(1000);
      .print("Arming command sent.");
      .wait(1000);
      .set_mode(1,4,2);
      .wait(1000);
      .print("Set mode command sent and will takeoff to default altitude.");
      .wait(5000).
      //.print("Sending takeoff command...");
      //.takeoff(0, 0, 0, 0, 0, 0, 10);
      //.wait(1000);
      //.print("Takeoff command sent.");
      //!start.
!start.
 */
/* !start.
+!start
   <- .print("Sending message from Pi 5 to Pi 4...");
      .arming(1,0);
      .wait(1000);
      .print("Arming command sent.");
      .set_mode(1,4,2);  // AUTO.TAKEOFF on PX4
      .wait(10000);
      .print("AUTO.TAKEOFF requested.");
      .waypoint(-1,1,0,0,1,2,3); // DO_REPOSITION, z=3
      .wait(2000);
      .waypoint(-1,1,0,0,2,2,3); // DO_REPOSITION, z=3
      .wait(2000);
      .waypoint(-1,1,0,0,5,2,3); // DO_REPOSITION, z=3
      .wait(2000);
      .waypoint(-1,1,0,0,0,0,3); // DO_REPOSITION, z=3
      .wait(2000);
      .rtl(0,0,0,0,0,0,0);
      .wait(5000);
      .land(0,0,0,0,0,0,0);
      .print("Landing command sent."). */

/* !setpoint.
+!setpoint : true
   <- .sp_local(0, 1, 1,1,2040,0, 0,-10, 0, 0, 0,0, 0, 0, 0,0);
      !setpoint.


!arm.
+!arm : true
   <- .arming(1,0).
 */


/* !start.
+!start <-

    .print("ARMING...");
    .arming(1,0);
    .wait(1200);

    .print("SET MODE → AUTO.MISSION...");
    .set_mode(1,4,4);   // custom_mode=4 = AUTO.MISSION
    .wait(800);

    .print("MISSION: TAKEOFF item seq=0...");
    .mission_item(47.3977419, 8.5455938, 7);

    .print("MISSION: adding waypoints...");
    .mission_item(47.3977569, 8.5456338, 7);
    .mission_item(47.3977919, 8.5456438, 7);
    .mission_item(47.3977869, 8.5456538, 7);
    .mission_item(47.3978959, 8.5457348, 7);
    .mission_item(47.3977869, 8.5456538, 7);
    .wait(500);

    .print("MISSION: upload + start...");
    .mission_start(0,-1);
    .wait(50000);
    .print("Landing...");
    .land(0,0,0,0,0,0,0);
    .print("Landed.").

 */


!start.

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
    -step_transitioning(_).