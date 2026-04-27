package jason.stdlib; 

import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.Literal;
import jason.asSyntax.ListTermImpl;
import jason.asSyntax.NumberTerm;
import jason.asSyntax.Term;
import static jason.asSyntax.ASSyntax.createAtom;
import static jason.asSyntax.ASSyntax.createNumber;

public class sp_local extends embedded.mas.bridges.jacamo.defaultEmbeddedInternalAction {

        private static final int TARGET_SYSTEM = 1;
        private static final int TARGET_COMPONENT = 1;
        private static final int MAV_FRAME_LOCAL_NED = 1;
        private static final int POSITION_ONLY_TYPE_MASK = 3576;

        private RelativeSetpoint lastRelativeSetpoint;

        @Override
        public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
            ListTermImpl parameters = new ListTermImpl();
            if (args.length == 3) {
                double forward = numberArg(args[0], "forward");
                double right = numberArg(args[1], "right");
                double up = numberArg(args[2], "up");
                RelativeSetpoint setpoint = relativeSetpoint(ts, forward, right, up);

                parameters.add(createNumber(0));
                parameters.add(createNumber(TARGET_SYSTEM));
                parameters.add(createNumber(TARGET_COMPONENT));
                parameters.add(createNumber(MAV_FRAME_LOCAL_NED));
                parameters.add(createNumber(POSITION_ONLY_TYPE_MASK));
                parameters.add(createNumber(setpoint.targetX));
                parameters.add(createNumber(setpoint.targetY));
                parameters.add(createNumber(setpoint.targetZned));
                parameters.add(createNumber(0.0));
                parameters.add(createNumber(0.0));
                parameters.add(createNumber(0.0));
                parameters.add(createNumber(0.0));
                parameters.add(createNumber(0.0));
                parameters.add(createNumber(0.0));
                parameters.add(createNumber(0.0));
                parameters.add(createNumber(0.0));
            } else {
                for(Term t:args) parameters.add(t);
            }
            Term[] arguments = new Term[3];
            arguments[0] =  createAtom("arduino1"); 
            arguments[1] =  createAtom( this.getClass().getSimpleName());
            arguments[2] = parameters;
            return super.execute(ts, un,  arguments);            
        }

        private RelativeSetpoint relativeSetpoint(TransitionSystem ts, double forward, double right, double up)
                throws Exception {
            if (lastRelativeSetpoint != null && lastRelativeSetpoint.matches(forward, right, up)) {
                return lastRelativeSetpoint;
            }

            LocalPose pose = currentLocalPose(ts);
            double deltaX = (forward * Math.cos(pose.yaw)) - (right * Math.sin(pose.yaw));
            double deltaY = (forward * Math.sin(pose.yaw)) + (right * Math.cos(pose.yaw));
            double deltaZned = -up;

            lastRelativeSetpoint = new RelativeSetpoint(
                forward,
                right,
                up,
                pose.x + deltaX,
                pose.y + deltaY,
                pose.zned + deltaZned);
            return lastRelativeSetpoint;
        }

        private static double numberArg(Term arg, String name) throws Exception {
            if (!(arg instanceof NumberTerm)) {
                throw new IllegalArgumentException("sp_local requires numeric " + name + " argument");
            }
            return ((NumberTerm) arg).solve();
        }

        private static LocalPose currentLocalPose(TransitionSystem ts) throws Exception {
            for (Literal belief : ts.getAg().getBB()) {
                if ("nav_pose_local".equals(belief.getFunctor()) && belief.getArity() >= 4) {
                    return new LocalPose(
                        numberTerm(belief, 0),
                        numberTerm(belief, 1),
                        numberTerm(belief, 2),
                        numberTerm(belief, 3));
                }
            }
            throw new IllegalStateException("sp_local(Forward, Right, Up) requires nav_pose_local(X, Y, Zned, Yaw)");
        }

        private static double numberTerm(Literal belief, int index) throws Exception {
            Term term = belief.getTerm(index);
            if (!(term instanceof NumberTerm)) {
                throw new IllegalStateException(
                    "Expected numeric term " + index + " in " + belief.getFunctor() + " belief");
            }
            return ((NumberTerm) term).solve();
        }

        private static class LocalPose {
            final double x;
            final double y;
            final double zned;
            final double yaw;

            LocalPose(double x, double y, double zned, double yaw) {
                this.x = x;
                this.y = y;
                this.zned = zned;
                this.yaw = yaw;
            }
        }

        private static class RelativeSetpoint {
            final double forward;
            final double right;
            final double up;
            final double targetX;
            final double targetY;
            final double targetZned;

            RelativeSetpoint(double forward, double right, double up, double targetX, double targetY, double targetZned) {
                this.forward = forward;
                this.right = right;
                this.up = up;
                this.targetX = targetX;
                this.targetY = targetY;
                this.targetZned = targetZned;
            }

            boolean matches(double forward, double right, double up) {
                return Double.compare(this.forward, forward) == 0
                    && Double.compare(this.right, right) == 0
                    && Double.compare(this.up, up) == 0;
            }
        }
}
