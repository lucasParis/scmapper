SCMPattern {
	//different playoptions for bus player:
	// - quantize play, quantize stop
	// - fadein, fadeout

	var <name;
	var <>parentGroup;

	var <bus;
	var busPlayer;
	var <serverGroup;

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

		bus = Bus.audio(Server.local,2);
		serverGroup = Group(Server.local);
		rawPattern = Pbindf(pattern,
			\out, bus,//the bus for all pbind stuff
			\group, serverGroup,//group for pbinds
			\fx_group, serverGroup
		);

		parentGroup = parent;
		isPlaying = false;
		quant = 4;
		outputPbind = true;
	}

	patternOut{
		^In.ar(this.bus,2);
	}

	chainProxy{
		arg function;
		var proxy, proxyName;
		proxyName = (name ++ "FX").asSymbol;

		//new proxy with audio input
		proxy = SCMProxy.new(proxyName, function, parentGroup, {this.patternOut()});

		//add SCMProxy after this pattern in server hierachy
		proxy.serverGroup = Group.new(serverGroup, 'addToTail');

		//disable output for this SCMpattern
		outputPbind = false;

		//add proxy to parent group
		parentGroup.proxies = parentGroup.proxies.add(proxy);
		^proxy;//return
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