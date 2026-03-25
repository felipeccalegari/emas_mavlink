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