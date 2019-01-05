SCMPattern {
	//different playoptions for bus player:
	// - quantize play, quantize stop
	// - fadein, fadeout

	var <name;
	var <>parentGroup;

	var <bus;
	var busPlayer;
	var <group;

	var rawPattern;
	var patternPlayer;

	var oscAddrPrefix;

	var isPlaying;
	var quant;
	var outputPbind;

	*new{
		arg patternName, pattern, parent;
		^super.new.init(patternName, pattern, parent);
	}

	init{
		arg patternName, pattern, parent;
		name = patternName;
		// rawPattern = pattern;

		bus = Bus.audio(Server.local,2);
		group = Group(Server.local);
		rawPattern = Pbindf(pattern,
			\out, bus,//the bus for all pbind stuff
			\group, group,//group for pbinds
			\fx_group, group
		);

		parentGroup = parent;
		isPlaying = false;
		quant = 4;
		outputPbind = true;
	}

	patternOut{
		^In.ar(this.bus);
	}

	chainProxyFX{

	}

	play{
		// patternPlayer = rawPattern.collect({arg evt; collectToOsc.value(evt, pGroupName )}).play(clock: proxySpace.clock, quant:quant, doReset:true);
		patternPlayer = rawPattern.play(clock: SCM.proxySpace.clock, quant:quant, doReset:true);
		if(outputPbind)
		{
			SCM.proxySpace.clock.play({busPlayer = bus.play; nil; },4);
			// SCM.proxySpace.clock.playNextBar({{ busPlayer = bus.play;}.defer(Server.local.latency); nil; });
		}

	}

	stop{
		patternPlayer.stop;
		if(outputPbind)
		{
			// busPlayer.set(\gate, 0)
			busPlayer.free;

			// bus.play;
		}
	}

	printOn { | stream |
		stream << "SCMPattern (" << name << ")";
	}

}