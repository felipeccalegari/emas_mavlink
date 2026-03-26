/**
 * This class represents an interface with a microcontroller whose sensor data follow the JSON format. 
 JSONWatcherDevice only receive data/beliefs when it sends a request to the microcontroller*/

package embedded.mas.bridges.jacamo;
import embedded.mas.bridges.javard.MicrocontrollerMonitor;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import embedded.mas.exception.PerceivingException;
import jason.asSemantics.Unifier;
import jason.asSyntax.Atom;
import jason.asSyntax.Literal;

public class JSONWatcherDevice extends SerialDevice implements IDevice {
	
	// Changed from the original synchronized list to a single latest snapshot.
	// This avoids queue growth and fits the "use the newest telemetry" behavior.
	private volatile Collection<Literal> latestBeliefs = Collections.emptyList();
	
	public JSONWatcherDevice(Atom id, IPhysicalInterface microcontroller) {
		super(id, microcontroller);	
		// Changed to use the new direct-update constructor instead of sharing a belief list.
		MicrocontrollerMonitor microcontrollerMonitor = new MicrocontrollerMonitor(this, this.getMicrocontroller());
		microcontrollerMonitor.start();
	}
	
	@Override
	public Collection<Literal> getPercepts() throws PerceivingException{
		// Changed from "take last item and clear the whole list" to a simple atomic snapshot read.
		Collection<Literal> percepts = latestBeliefs;
		latestBeliefs = Collections.emptyList();
		return percepts;
	}

	// New helper used by MicrocontrollerMonitor to publish the newest parsed beliefs directly.
	public void updateLatestBeliefs(Collection<Literal> percepts) {
		if (percepts == null || percepts.isEmpty()) {
			return;
		}
		latestBeliefs = percepts;
	}

	@Override
	public boolean execEmbeddedAction(String actionName, Object[] args, Unifier un) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public IPhysicalInterface getMicrocontroller() {
		return (IPhysicalInterface) this.microcontroller;
	}
	
	
}
