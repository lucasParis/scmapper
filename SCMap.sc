
SCM {
	classvar <proxySpace;
	classvar tempo_;
	classvar <groups;
	classvar <ctrlrs;

	*init{
		//initialise proxy space
		proxySpace.clear;//clear if allready exists
		proxySpace = ProxySpace.new(Server.local);//make new proxy
		proxySpace.makeTempoClock(2);//setup tempoclock

		//reset database
		groups = [];
		// "resourcesSC/synthlib.scd".loadRelative; // load synth library
		// "resourcesSC/noteFX.scd".loadRelative;  // load custom fx events

	}

	*newGroup{
		arg name;
		var group;
		group = SCMGroup.new(name);
		groups.postln;
		groups = groups.add(group);
		^group;
	}

	*setTempo{
		arg tempo;
		tempo_ = tempo;
		tempo_.postln;
		proxySpace.clock.tempo = tempo_/60;

	}

	*newLemurCtrlr{
		arg ip, port, name = \notNamed;
		var return;
		return = SCMLemurCtrlr.new(ip, port, name);
		ctrlrs = ctrlrs.add(return);
		^return;
	}
}



SCMLemurCtrlr{
	var < netAddr;
	var name;

	*new{
		arg ip, port, name;
		^super.new.init(ip, port, name);
	}

	init{
		arg ip, port, name_;
		name = name_;
		netAddr = NetAddr(ip, port);
	}
	set{
		arg path, value;
		netAddr.sendMsg(path, *value);
	}
}

SCMMidiCtrlr{

}







