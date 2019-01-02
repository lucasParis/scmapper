SCMPattern {
	var <bus;
	var <name;
	var rawPattern;
	var patternPlayer;
	var <group;
	var <>parentGroup;


	*new{
		arg patternName, pattern;

		// "new bus".postln
		^super.new.init(patternName, pattern);
	}

	init{
		arg patternName, pattern;
		name = patternName;
		rawPattern = pattern;
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

	printOn { | stream |
		stream << "SCMPattern (" << name << ")";
    }

}