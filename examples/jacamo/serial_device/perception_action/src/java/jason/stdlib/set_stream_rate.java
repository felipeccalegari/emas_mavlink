package jason.stdlib;

import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.ListTermImpl;
import jason.asSyntax.Term;

import static jason.asSyntax.ASSyntax.createAtom;
import static jason.asSyntax.ASSyntax.createNumber;

public class set_stream_rate extends embedded.mas.bridges.jacamo.defaultEmbeddedInternalAction {

        @Override
        public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
            ListTermImpl parameters = new ListTermImpl();
            for (Term t : args) {
                if ("true".equals(t.toString())) {
                    parameters.add(createNumber(1));
                } else if ("false".equals(t.toString())) {
                    parameters.add(createNumber(0));
                } else {
                    parameters.add(t);
                }
            }

            Term[] arguments = new Term[3];
            arguments[0] = createAtom("arduino1");
            arguments[1] = createAtom(this.getClass().getSimpleName());
            arguments[2] = parameters;
            return super.execute(ts, un, arguments);
        }
}
