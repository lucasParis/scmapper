
//sketching out


//ctrl

//type 1 2 3, polymorphic?

// group

SCM {
	classvar proxySpace;
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

SCMGroup {
	var <name;

	*new{
		arg groupName;
		^super.new.init(groupName);
		// ^this;
		// ^super.new;

	}

	init{
		arg groupName;
		name = groupName;

	}

	newCtrl{

	}

	linkPattern{

	}

	newIDOverlap{

	}

	newID{

	}

	newProxy{

	}


}

SCMPattern {
	var <bus;
	var <group;

	*new{
		// "new bus".postln
		^super.new.init;
	}

	init{
		bus = Bus.audio(Server.local,2);
	}

	patternOut{
		^In.ar(this.bus);
	}


	chainProxyFX{

	}

	play{

	}

	stop{

	}

}

SCMProxy {

}

SCMCtrl {

	busMap{

	}

	pfunc{

	}


}


