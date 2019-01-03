SCM {
	classvar <proxySpace;
	classvar tempo_;
	classvar <groups;

	*init{
		//initialise proxy space
		proxySpace.clear;//clear if allready exists
		proxySpace = ProxySpace.new(Server.local);//make new proxy
		proxySpace.makeTempoClock(2);//setup tempoclock

		//reset database
		groups = [];

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
}









