/*
   This example has two possible behaviours: proactive and reactive.
   Uncomment the desired version and comment the other one to test the different approaches.  
*/
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
    .print("ARMING...");
    .arming(1,0);
    .wait(1200);

    .print("SET MODE - AUTO.MISSION...");
    .set_mode(1,4,4);
    .wait(800);

    .print("MISSION: TAKEOFF...");
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


+globalpositionint(_,Lat,Lon,Alt,RelAlt,_,_,_,_)
   <-
    .print("[GPS] lat=",Lat," lon=",Lon," alt=",Alt," relAlt=",RelAlt).
