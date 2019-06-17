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

	var splitMixing;
	var splitMixBusses;
	var splitBusPlayers;



	*new{
		arg patternName, pattern, parent, channels = 2, manualMode = false, independentPlay = false, trigBus = false, manualGrouping =1, splitMixing = false;
		^super.new.init(patternName, pattern, parent, channels, manualMode, independentPlay, trigBus, manualGrouping, splitMixing);
	}

	init{
		arg patternName, pattern, parent, channelCount, manualMode_, independentPlay_, trigBus_, manualGrouping_, splitMixing_;

		//set variables
		parentGroup = parent;
		name = patternName;
		channels = channelCount;
		manualMode = manualMode_;
		independentPlay = independentPlay_;
		splitMixing = splitMixing_;

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

			\group, serverGroup,//group for pbinds
			\fx_group, serverGroup,

		);

		if(splitMixing == true)
		{
			//if is a splitting scenario:
			var streamForNameExtraction, stream;

			//findout all the names and create a bus for each
			splitMixBusses = ();
			streamForNameExtraction = Pbindf(rawPattern, \findThatStuffYo,
				Pfunc{
					arg e;
					var mixName =  e[\mixingName];
					if(mixName != nil)
					{
						if(splitMixBusses.includesKey(mixName) != true){
							splitMixBusses[mixName] = Bus.audio(Server.local, 2);
						};
					};
					0;
				};
			);

			//do the extraction
			streamForNameExtraction = streamForNameExtraction.asStream;
			{streamForNameExtraction.next(())}!100;


			//add pfunc that maps from mixing name to bus
			rawPattern = Pbindf(rawPattern,
				\out, Pfunc{
					arg e;
					var mixName =  e[\mixingName];
					splitMixBusses[mixName];
				}
			);
		}
		{
			//otherwise set to basic output
			rawPattern = Pbindf(rawPattern,
				\out, bus,//the bus for all pbind stuff
			);


		};



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
			rawPattern = Pfset(nil, rawPattern, {	independantIsPlaying  = false;	independentPlayCtrl.hardSet(0,toFunction:false);});

		};


		rawBusPlayer = Pmono(\SCMbusPlayer_stereo,
			\in, bus,
			\out, outputBus,
			\addAction, 3 ,
			\group, busPlayerGroup,
			\dur, 1
		);

		if(splitMixing == true)
		{
			splitBusPlayers = splitMixBusses.values.collect{
				arg splitBus;
				Pmono(\SCMbusPlayer_stereo,
					\in, splitBus,
					\out, outputBus,
					\addAction, 3 ,
					\group, busPlayerGroup,
					\dur, 1
				);
			};
		};


	}

	copySplitMixNames{
		var str;
		// SCM.quickStringToPaste.


		str = "";
		splitMixBusses.keys.do{
			arg key, i;
			str = str ++ key;
			if(i != (splitMixBusses.keys.size-1))
			{
				str = str ++ ", ";
			}
		};

		SCM.quickStringToPaste(str, "~/_SCcopySplitString");
	}

	copySplitMixVariables{
		var str;
		// SCM.quickStringToPaste.


		str = "\tvar splitMix, ";
		splitMixBusses.keys.do{
			arg key, i;
			str = str ++ key;
			if(i != (splitMixBusses.keys.size-1))
			{
				str = str ++ "SM, ";
			}
			{
				str = str ++ "SM; \n";
			};
		};
		str = str ++ "\tsplitMix =  ?.getSplitMix();//change to pattern here\n";
		splitMixBusses.keys.do{
			arg key;
			str =  str ++  "\t" ++ key ++ "SM = splitMix[\\" ++ key ++ "];\n" ;
			// name1SM = splitmix[name1];

		};
		str =  str ++  "\t";

		splitMixBusses.keys.do{
			arg key, i;
			str =  str ++key;
			if(i != (splitMixBusses.keys.size-1))
			{
				str = str ++ "SM + ";
			}
			{
				str = str ++ "SM;\n";
			};
			// name1SM = splitmix[name1];

		};

		SCM.quickStringToPaste(str, "~/_SCcopySplitString");


	}

	getSplitMix{
		^splitMixBusses.collect{
			arg bus;
			In.ar(bus,2);
		};
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

		if(splitMixing == true)
		{
			splitBusPlayers.do{arg busplay; busplay.play(clock: SCM.proxySpace.clock, quant:quant);};
		}
		{
			busPlayer = rawBusPlayer.play(clock: SCM.proxySpace.clock, quant:quant);//, doReset:true
		};

	}

	stop{
		isPlaying = false;

		this.stopPattern();


		if(splitMixing == true)
		{
			splitBusPlayers.do{arg busplay; busplay.stop;};
		}
		{
			busPlayer.stop;
		};
	}

	printOn { | stream |
		stream << "SCMPattern (" << name << ")";
	}

	listen{
		this.outputBus.play;
	}



}