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
	var <> sendToOutput;
	var channels;

	var <> hasFX;

	*new{
		arg patternName, pattern, parent, channels = 2;
		^super.new.init(patternName, pattern, parent, channels);
	}

	init{
		arg patternName, pattern, parent, channels;
		channels = channels;

		channels.postln;
		name = patternName;

		bus = Bus.audio(Server.local,2);
		serverGroup = Group(parent.serverGroup, 'addToHead');//add to head in group, so it comes before groupFX
		rawPattern = Pbindf(pattern,
			\out, bus,//the bus for all pbind stuff
			\group, serverGroup,//group for pbinds
			\fx_group, serverGroup
		);

		parentGroup = parent;
		isPlaying = false;
		quant = 4;
		sendToOutput = true;
		hasFX = false;
	}

	patternOut{
		^In.ar(this.bus,2);
	}

	patternFX{
		arg function;
		var proxy, proxyName;
		proxyName = (name ++ "FX").asSymbol;

		//new proxy with audio input
		proxy = SCMProxy.new(proxyName, function, parentGroup, {this.patternOut()});

		//add SCMProxy after this pattern in server hierachy
		proxy.serverGroup = Group.new(serverGroup, 'addToTail');

		//disable output for this SCMpattern
		sendToOutput = false;

		//add proxy to parent group
		parentGroup.proxies = parentGroup.proxies.add(proxy);

		//confirm this has fx, to return correct output
		hasFX = true;

		^proxy;//return
	}

	play{
		// patternPlayer = rawPattern.collect({arg evt; collectToOsc.value(evt, pGroupName )}).play(clock: proxySpace.clock, quant:quant, doReset:true);
		patternPlayer = rawPattern.play(clock: SCM.proxySpace.clock, quant:quant, doReset:true);
		if(sendToOutput)
		{
			SCM.proxySpace.clock.play({busPlayer = bus.play; nil; },4);
			// SCM.proxySpace.clock.playNextBar({{ busPlayer = bus.play;}.defer(Server.local.latency); nil; });
		}

	}

	stop{
		patternPlayer.stop;
		if(sendToOutput)
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