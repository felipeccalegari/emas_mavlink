package jason.stdlib; 

import embedded.mas.bridges.jacamo.defaultEmbeddedInternalAction;
import jason.asSemantics.DefaultInternalAction;
import jason.asSemantics.TransitionSystem;
import jason.asSemantics.Unifier;
import jason.asSyntax.ListTermImpl;
import jason.asSyntax.Term;
import static jason.asSyntax.ASSyntax.createAtom;

public class mission_start extends embedded.mas.bridges.jacamo.defaultEmbeddedInternalAction {

        @Override
        public Object execute(TransitionSystem ts, Unifier un, Term[] args) throws Exception {
            ListTermImpl parameters = new ListTermImpl();
            for(Term t:args) parameters.add(t);
            Term[] arguments = new Term[3];
            arguments[0] =  createAtom("arduino1"); 
            arguments[1] =  createAtom( this.getClass().getSimpleName());
            arguments[2] = parameters;
            System.out.println("[DEBUG arming.java] Sending to bridge -> " + arguments[1] + parameters.toString());
            return super.execute(ts, un,  arguments);            
        }
}