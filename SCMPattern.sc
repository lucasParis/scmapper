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

	var triggerBus;

	var < outputBus;

	var < fxProxy;

	var manualMode;
	var manualStream;
	var <> manualGrouping;

	var independentPlay;
	var independentPlayCtrl;

	var independantIsPlaying;



	*new{
		arg patternName, pattern, parent, channels = 2, manualMode = false, independentPlay = false, trigBus = false, manualGrouping =1 ;
		^super.new.init(patternName, pattern, parent, channels, manualMode, independentPlay, trigBus, manualGrouping);
	}

	init{
		arg patternName, pattern, parent, channelCount, manualMode_, independentPlay_, trigBus_, manualGrouping_;

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
		manualGrouping = manualGrouping_;
		if(trigBus_)
		{
			manualGrouping = manualGrouping*2;
		};

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

		//trigger bus
		if(trigBus_)
		{
			var trigPat;

			triggerBus = Bus.audio(Server.local,1);

			trigPat = Pbindf(rawPattern,
				\instrument, 'l1_trigToBus',
				\trigOut, triggerBus,
			);

			rawPattern = Ppar([rawPattern,trigPat]);
		};


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
						manualGrouping.postln;
						if(quant == nil)
						{
							manualGrouping.do{manualStream.next(()).play;};
						}
						{

							SCM.proxySpace.clock.play({ manualGrouping.do{ manualStream.next(()).play;};  nil;}, quant);
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

		independantIsPlaying = false;
		//independent play, can react to a stopping pattern
		if(independentPlay)
		{

			//osc controller for play
			independentPlayCtrl = parent.newCtrl((name.asString ++ 'Play').asSymbol).functionSet = {
				arg value;
				if(value > 0.5)
				{
					if(independantIsPlaying.not)
					{
						independantIsPlaying  = true;
						this.startPattern();
					}

				}
				{
					if(independantIsPlaying)
					{
						independantIsPlaying  = false;
						this.stopPattern();
					}
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

	getTrigger{
		var result;
		result = nil;

		if(triggerBus != nil)
		{
			result = In.ar(triggerBus);
		};
		^result;
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