SCMPattern {
	var <bus;
	var <name;
	var rawPattern;
	var patternPlayer;
	var <group;
	var <>parentGroup;
	var oscAddrPrefix;

	*new{
		arg patternName, pattern;
		^super.new.init(patternName, pattern);
	}

	init{
		arg patternName, pattern;
		name = patternName;
		rawPattern = pattern;
		bus = Bus.audio(Server.local,2);

	}

	setupOscListeners{
		oscAddrPrefix = "/" ++ parentGroup.name;
		//add osc listeners
		// OSCdef(
		// 	(oscAddrPrefix ++ "/pctrl/play").asSymbol,
		// 	{
		//
		// 	}
		// )
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