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
	var <> quant;
	// var <> sendToOutput;
	var channels;

	var <> hasFX;

	var rawBusPlayer;
	var busPlayer;
	var busPlayerGroup;

	var < outputBus;

	var < fxProxy;

	var manualMode;
	var manualStream;
	var <> manualGrouping;

	var independentPlay;
	var independentPlayCtrl;

	*new{
		arg patternName, pattern, parent, channels = 2, manualMode = false, independentPlay = false;
		^super.new.init(patternName, pattern, parent, channels, manualMode, independentPlay);
	}

	init{
		arg patternName, pattern, parent, channelCount, manualMode_, independentPlay_;

		//set variables
		parentGroup = parent;
		name = patternName;
		channels = channelCount;
		manualMode = manualMode_;
		independentPlay = independentPlay_;

		//set default values
		isPlaying = false;
		quant = 4;
		hasFX = false;
		manualGrouping = 1;

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

		//osc rerouting
		rawPattern =  rawPattern.collect({arg evt; SCM.eventToTD(evt, parentGroup.name, name);});


		//if in manual mode
		if(manualMode)
		{
			quant = nil;

			manualStream = rawPattern.asStream;

			//add listeners/ctrls for play
			parent.newCtrl((name.asString ++ 'Play').asSymbol).functionSet = {
				arg value;
				if(isPlaying)
				{
					if(value > 0.5)
					{
						if(quant == nil)
						{
							manualGrouping.do{manualStream.next(()).play;};
						}
						{
							SCM.proxySpace.clock.play({ manualGrouping.do{manualStream.next(()).play;}; }, quant);
						};
					};
				};
			};

			//add listeners/ctrls for reset
			parent.newCtrl((name.asString ++ 'Reset').asSymbol).functionSet = {
				arg value;
				if(value > 0.5)
				{
					manualStream.reset;
				};
			};
		};

		//independent play, can react to a stopping pattern
		if(independentPlay)
		{

			//osc controller for play
			independentPlayCtrl = parent.newCtrl((name.asString ++ 'Play').asSymbol).functionSet = {
				arg value;
				if(value > 0.5)
				{
					this.startPattern();

				}
				{
					this.stopPattern();
				};
			};

			//callback for pattern's end
			rawPattern = Pfset(nil, rawPattern, { "hello".postln; independentPlayCtrl.set(0,toFunction:false); "hello".postln;});

		};


		rawBusPlayer = Pmono(\SCMbusPlayer_stereo,
			\in, bus,
			\out, outputBus,
			\addAction, 3 ,
			\group, busPlayerGroup,
			\dur, 1
		);

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

	startPattern{
		patternPlayer = rawPattern.play(clock: SCM.proxySpace.clock, quant:quant);//, doReset:true
	}

	stopPattern{

		patternPlayer.stop;

	}

	play{
		isPlaying = true;

		if(manualMode.not)
		{
			if(independentPlay.not)
			{
				this.startPattern();
			};
		};

		busPlayer = rawBusPlayer.play(clock: SCM.proxySpace.clock, quant:quant);//, doReset:true
	}

	stop{
		isPlaying = false;

		this.stopPattern();

		busPlayer.stop;
	}

	printOn { | stream |
		stream << "SCMPattern (" << name << ")";
	}

	listen{
		this.outputBus.play;
	}



}