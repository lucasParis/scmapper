SCMPattern {
	//different playoptions for bus player:
	// - quantize play, quantize stop
	// - fadein, fadeout

	var <name;
	var <>parentGroup;

	var <bus;
	var <serverGroup;

	var rawPattern;
	var patternPlayer;

	var oscAddrPrefix;

	var isPlaying;
	var quant;
	// var <> sendToOutput;
	var channels;

	var <> hasFX;

	var rawBusPlayer;
	var busPlayer;
	var busPlayerGroup;

	var < outputBus;

	var < fxProxy;

	*new{
		arg patternName, pattern, parent, channels = 2;
		^super.new.init(patternName, pattern, parent, channels);
	}

	init{
		arg patternName, pattern, parent, channelCount;

		//set variables
		parentGroup = parent;
		name = patternName;
		channels = channelCount;

		//create busses
		bus = Bus.audio(Server.local, channels);
		outputBus = Bus.audio(Server.local, channels);

		//create groups
		serverGroup = Group(parent.serverGroup, 'addToHead');//add to head in group, so it comes before groupFX
		busPlayerGroup = Group(serverGroup, 'addAfter');//add busPlayer after all

		rawPattern = Pbindf(pattern,
			\out, bus,//the bus for all pbind stuff
			\group, serverGroup,//group for pbinds
			\fx_group, serverGroup
		);

		rawBusPlayer = Pmono(\SCMbusPlayer_stereo,
			\in, bus,
			\out, outputBus,
			\addAction, 3 ,
			\group, busPlayerGroup,
			\dur, 1
		);

		//set default values
		isPlaying = false;
		quant = 4;
		// sendToOutput = true;
		hasFX = false;


	}

	getOutput{
		var return;
		(hasFX).if{
			return = fxProxy.getOutput();
		}
		{
			return = In.ar(this.outputBus, channels);
		}
		^return;
	}

	patternFX{
		arg function;
		var proxy, proxyName;
		proxyName = (name ++ "FX").asSymbol;

		//new proxy with audio input
		proxy = SCMProxy.new(proxyName, function, parentGroup, {this.getOutput()});

		//add SCMProxy after this pattern in server hierachy
		proxy.serverGroup = Group.new(serverGroup, 'addToTail');

		//add proxy to parent group
		parentGroup.proxies = parentGroup.proxies.add(proxy);
		fxProxy = parentGroup.proxies.last();

		//confirm this has fx, to return correct output
		hasFX = true;

		^proxy;//return
	}

	play{
		patternPlayer = rawPattern.collect({arg evt; SCM.eventToTD(evt, parentGroup.name, name); }).play(clock: SCM.proxySpace.clock, quant:quant);//, doReset:true
		// patternPlayer = rawPattern.play(clock: SCM.proxySpace.clock, quant:quant, doReset:true);
		busPlayer = rawBusPlayer.play(clock: SCM.proxySpace.clock, quant:quant);//, doReset:true
	}

	stop{
		patternPlayer.stop;
		// busPlayer.set(\gate,0);
		busPlayer.stop;
	}

	printOn { | stream |
		stream << "SCMPattern (" << name << ")";
	}



}