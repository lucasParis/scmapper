// "18_10_23_perfoResources_fxChain.scd".loadRelative;

(
/*~visualSync*/
"loading resources".postln;
// SynthDescLib.getLib(\lib1).browse;
// TempoClock.default.tempo = 120/60;
//check notes in old versions
// s.options.latency
~visualSync = 0;


~touchIP = "127.0.0.1";
// ~touchIP = "10.10.10.4";

// s.options.numOutputBusChannels = 9;
s.options.memSize_(2.pow(20));
// s.options.numWireBufs = 128;
s.options.numWireBufs = 512;

s.waitForBoot({


	// "/Users/lucas/Dropbox/MyWork/00_supercolliderResources/synthlib1.scd".load;
	"synthlib.scd".loadRelative;
	"noteFX.scd".loadRelative;




	~evtToPythonDictString =
	{
		arg evt;
		var result;
		evt.removeAt(\scale);
		result = "{";


		evt.keysValuesDo{
			arg key, value;
			// value.postln;
			result = result++"" ++'"' ++ key.asString ++ '"' ++ ": ";
			value.isKindOf(Array).if{
				value = value.collect{
					arg val;
					(val.isKindOf(Symbol).not).if{
						(val.isKindOf(String).not).if{
							(val.isKindOf(Boolean).not).if{
								(val.isKindOf(Float).not).if{
									(val.isKindOf(Integer).not).if{
										// "dont send".postln;
										// val.postln;
										val =  "empty";
									};
								};
							};
						};
					};
					val.isKindOf(String).if
					{
						val =  '"'++ val.asString ++  '"';
					};
					val.isKindOf(Symbol).if
					{
						val =  '"'++ val.asString ++  '"';
					};
					val.isKindOf(Boolean).if
					{
						val =  val.asString.asInteger;
					};
					val.isFloat.if{
						val.round(0.001);
					}
					{
						val;
					};


				};
			};
			value.isFloat.if{
				value = value.round(0.001);
			};

			value.isKindOf(Boolean).if
			{
				value =  value.asInteger;
			};
			value.isKindOf(String).if
			{
				value =  '"'++ value.asString ++  '"';
			};
			value.isKindOf(Symbol).if
			{
				value =  '"'++ value.asString ++  '"';
			};
			(value.isKindOf(Symbol).not).if{
				(value.isKindOf(String).not).if{
					(value.isKindOf(Boolean).not).if{
						(value.isKindOf(Float).not).if{
							(value.isKindOf(Integer).not).if{
								(value.isKindOf(Array).not).if{
									// "dont send".postln;
									// value.postln;
									value = '"'++ "empty" ++  '"';
								};
							};
						};
					};
				};
			};


			result = result ++ value.asString ++ ", ";
			// value.postln;
		};

		result = result ++ "}";
	};
	~makeTwister = {
		MIDIClient.init;
		MIDIIn.connectAll;
		// try
		~twisterHues =  [10, 70, 84, 24];
		m = nil;

		{m = MIDIOut.newByName("Midi Fighter Twister", "Midi Fighter Twister");}.try{"failed to connect to midiout".postln;};
		(m != nil).if
		{
			m.latency = 0;
			~twister = ( lastTime: SystemClock.seconds, lastValue:0, count:0, normValue:0, speed:1, button:0, toggle:1, knobCallback: nil, buttonCallback:nil, set:nil, setButton:nil, mapped:0)!16;

			~twister.do{
				arg item, i;
				var hue, hues;

				//setup colors
				hues = ~twisterHues;
				hue = hues[(i/4).floor];
				m.control(chan:1,ctlNum:i,val:hue); //ringvalue //10 24 70 84
				m.control(chan:2,ctlNum:i,val:item[\button].linlin(0,1,27,47));
				m.control(chan:2,ctlNum:i,val:18);//off
				//knob
				MIDIFunc.cc(
					{
						arg value;
						var time, speed;
						// calculate speed
						time = SystemClock.seconds;
						speed = (SystemClock.seconds-item[\lastTime]);
						item[\lastTime] = SystemClock.seconds;
						speed = speed.reciprocal.linexp(0,300,0.2,3);
						// speed.postln;
						value = (value-64);//convert to -1 > 1
						item[\count] = item[\count] + (value*speed);// set value
						item[\count] = item[\count].clip(0,127);
						item[\normValue] = item[\count].linlin(0,127,0,1);
						// item[\count].postln;

						//send led value
						(item[\knobCallback] != nil).if{
							m.control(chan:0,ctlNum:i,val:item[\count].wrap(0,128).round);
						};



						// (item[\mapped] > 0.5).if()
						//callback
						item[\knobCallback].isKindOf(Function).if{
							item[\knobCallback].value(item[\normValue]);
						};



				},i, 0);

				item[\set] = {
					arg value;
					item[\count] = value.linlin(0,1,0,127);
					item[\normValue] = item[\count].linlin(0,127,0,1);
					//send led value
					// value.postln;risers

					m.control(chan:0,ctlNum:i,val:item[\count].wrap(0,128).round);

				};

				m.control(chan:0,ctlNum:i,val:item[\count].wrap(0,127));

				//button
				MIDIFunc.cc(
					{
						arg value;
						(item[\toggle] == 1).if(
							{
								(value>1).if{item[\button] = 1-item[\button]};//toggle
							},
							{
								item[\button] = value.min(1);//momentary
							}
						);
						//send led value
						m.control(chan:2,ctlNum:i,val:item[\button].linlin(0,1,27,47));

						item[\buttonCallback].isKindOf(Function).if{
							item[\buttonCallback].value(item[\button]);
						};
					},i,1
				);
				item[\setButton] = {
					arg value;
					item[\button] = value;

					m.control(chan:2,ctlNum:i,val:item[\button].linlin(0,1,27,47));
				};
				// m.control(chan:2,ctlNum:i,val:item[\button].linlin(0,1,27,47));
			};
		};
	};

	// ~twister[15].knobCallback = {arg val; val.postln};

	//test twister callback

	~mapperMaker = {
		var pMap, bMap, fMap, pgroups, newPGroup, makeCtrlUI, currentGroup, collectToOsc, touchdesignerDAT, ctrlrs, normalColor, shiftJumpColor, newID, replyIDCount, touchdesignerCHOP, linkEncoder, linkButton, sendBrowserNames, visualSync, allMap, proxySpace, newProxyGroup, newGroup, setupPreset, sendSignal, chainProxyFX, pbus, setTempo;

		proxySpace.clear;
		proxySpace = ProxySpace.new(s);
		proxySpace.makeTempoClock(2);
		// proxySpace[\tempo]
		// proxySpace.clock;




		// visualSync = 0.04;//milliseconds
		~makeTwister.value;

		replyIDCount = 0;

		normalColor = 4868682;
		shiftJumpColor = 8336384;

		ctrlrs = [];
		ctrlrs = ctrlrs.add(NetAddr("127.0.0.1", 7000));
		ctrlrs = ctrlrs.add(NetAddr("127.0.0.1", 8000));

		/*{
		touchdesignerDAT = NetAddr("10.10.10.4", 10000);
		touchdesignerCHOP = NetAddr("10.10.10.4", 10001);
		"connecting to 10.10.10.4".postln;
		}.try{*/
		"failed, connecting to localHost".postln;

		touchdesignerDAT = NetAddr(~touchIP, 10000);
		touchdesignerCHOP = NetAddr(~touchIP, 10001);
		// };


		setTempo = {
			arg tempo;
			proxySpace.clock.tempo = tempo/60;
		};

		ctrlrs.do{
			arg ctrlr;
			var keysLight, keys =["shortIntroA", "darkSection",  "candy", "risers", "killRythm", "pattern1", "neural"];// ["pattern1", "neural"];//pgroups.keys
			ctrlr.sendMsg("/browser/groupsSelector",'@labels', *keys);
			keysLight = 0!(keys.size) ++ (-1!(28-keys.size));
			ctrlr.sendMsg("/browser/groupsSelector/light", *keysLight);

		};

		OSCdef(\fpsReroute,
			{
				arg msg;
				// msg[1].postln;

				ctrlrs.do{
					arg ctrlr;
					ctrlr.sendMsg("/fps/value", msg[1]);
				};

			}, "/touch/fps"
		);

		collectToOsc = {
			arg event, adr = "/default";
			var evt = event.copy, stringEvent, sendAddr;


			// evt.includes(Rest()).postln;
			// evt.postln;

			// " ".postln;
			/*if(evt.isRest.not, {*/

			sendAddr = ('/patternEvent/'++ adr +/+ evt[\instrument].asString);
			(evt[\osc_post] != nil).if{
				sendAddr = sendAddr +/+ (evt[\osc_post].asString);
			};
			(evt[\osc_post2] != nil).if{
				sendAddr = sendAddr +/+ (evt[\osc_post2].asString);
			};
			evt[\isRest] = evt.isRest;
			evt[\isRest].if{
				evt[\dur] = evt[\dur].value;
			};

			evt[\dur] = evt[\dur] / proxySpace.clock.tempo;
			/*(evt.isRest).if{
				evt[\isRest] = true;
			}
			{
				evt[\isRest] = false;
			};*/
			stringEvent = ~evtToPythonDictString.value(evt);
			// (adr +/+ evt[\instrument].asString).postln;
			{
				// sendAddr.postln;
				// stringEvent.asSymbol.postln;
				// touchdesignerDAT.postln;
				// touchdesignerDAT.sendMsg("/lalal", 1);
				touchdesignerDAT.sendMsg(sendAddr , *["stringEvent", stringEvent.asSymbol]);

			}.defer(max(s.latency-(~visualSync), 0));
			// }
			/*,{

			sendAddr = ('/restEvent/'++ adr +/+ evt[\instrument].asString);
			(evt[\osc_post] != nil).if{
			sendAddr = sendAddr +/+ (evt[\osc_post].asString);
			};
			(evt[\osc_post2] != nil).if{
			sendAddr = sendAddr +/+ (evt[\osc_post2].asString);
			};

			{touchdesignerDAT.sendMsg(sendAddr +/+ "rest", *["rest", 1]);}.defer(s.latency);
			}*///);

			event;
		};

		pgroups = ();

		/*	newProxyGroup = {
		arg pGroupName, pattern;
		newGroup.value(pGroupName, pattern);
		};*/

		newPGroup = {
			arg pGroupName, pattern, quant;
			newGroup.value(pGroupName);

			// pgroups[pGroupName][\bus] = Bus.audio(s,2);
			// pgroups[pGroupName][\pbindGroup] = Group(s);
			// pgroups[pGroupName][\fxGroup] =  Group(pgroups[pGroupName][\synthGroup] , \addToTail);
			pattern = Pbindf(pattern,
				\out, pgroups[pGroupName][\bus],
				\group, pgroups[pGroupName][\pbindGroup],
				\fx_group, pgroups[pGroupName][\pbindGroup],
			);

			pgroups[pGroupName][\pattern] = pattern;
			// pgroups[pGroupName][\pattern] = Pbindf(pattern, \rest: Pfunc{});//insert pause and mute use mapping for it
			pgroups[pGroupName][\patternPlayer] = nil;

			//RESET
			OSCdef(('/' ++ pGroupName ++ '/pctrl/reset/x').asSymbol,
				{
					arg msg, time, addr;
					if(msg[1] > 0.5, {
						// TempoClock.schedAbs(TempoClock.default.elapsedBeats.roundUp(4)-0.25, {pgroups[pGroupName][\patternPlayer].reset})});
						// TempoClock.default.playNextBar({pgroups[pGroupName][\patternPlayer].reset})});
						proxySpace.clock.playNextBar({pgroups[pGroupName][\patternPlayer].reset})});
					// use fork?


			} , ('/' ++ pGroupName ++ '/pctrl/reset/x').asSymbol);

			//playstop // add external access (second control place)
			OSCdef(('/' ++ pGroupName ++ '/pctrl/play/x').asSymbol,
				{
					arg msg, time, addr;
					// msg[1].postln;
					// (msg[1] > 0.5).postln;
					// pGroupName.postln;
					(msg[1] > 0.5).if{

						pgroups[pGroupName][\patternPlayer] = pgroups[pGroupName][\pattern].collect({arg evt; collectToOsc.value(evt, pGroupName )}).play(clock: proxySpace.clock, quant:quant, doReset:true);
						pgroups[pGroupName][\pbindListen].if{
							pgroups[pGroupName][\busPlayer] = pgroups[pGroupName][\bus].play;

						};//proxyFX

						touchdesignerCHOP.sendMsg('/' ++ pGroupName ++ '/pctrl/play/x', 1);


					}{
						pgroups[pGroupName][\patternPlayer].stop;
						(pgroups[pGroupName][\busPlayer] != nil).if{
							pgroups[pGroupName][\busPlayer].free;
						};


						// TempoClock.default.playNextBar({touchdesignerCHOP.sendMsg('/' ++ pGroupName ++ '/pctrl/play/x', 0); });
						proxySpace.clock.playNextBar({touchdesignerCHOP.sendMsg('/' ++ pGroupName ++ '/pctrl/play/x', 0); });
						// TempoClock

					};

					ctrlrs.do{
						arg ctrlr;
						ctrlr.sendMsg('/' ++ pGroupName ++ '/pctrl/play/x', msg[1] );
					};

					// );
			} , ('/' ++ pGroupName ++ '/pctrl/play/x').asSymbol);
			ctrlrs.do{
				arg ctrlr;
				ctrlr.sendMsg('/' ++ pGroupName ++ '/pctrl/play/x', 0);
			};

		};

		setupPreset = {
			arg pGroupName, presetName, index;
			var addrPreset;
			if(pgroups[pGroupName] == nil, {pgroups[pGroupName] = ()});// create dict if doesn't exist'
			if(pgroups[pGroupName][\presets] == nil, {pgroups[pGroupName][\presets] = (\name:\empty, values:())!4});// create dict if doesn't exist'
			pgroups[pGroupName][\presets][index] = (\name:presetName, values:());
			//get and setValues from file: /presets/groupName/presetName
			//send to button
			addrPreset =  ('/' ++ pGroupName ++ '/pctrl/preset'++(index+1).asString).asSymbol;
			ctrlrs.do{
				arg ctrlr;
				ctrlr.sendMsg(addrPreset,'@label_off', *presetName);
				ctrlr.sendMsg(addrPreset,'@label_on', *presetName);
			};
		};


		chainProxyFX = {
			arg group, graph;
			//set flag to not listen to pbind bus (on play/stop)
			pgroups[group][\pbindListen] =  false;//add it to play/stop

			// **** add group/bus pbindf to newgroup
			//map like proxygroup
			proxySpace[group] = graph;
			newProxyGroup.value(group, true, pgroups[group][\fxGroup]);

		};

		pbus = {
			arg group;
			group.postln;
			pgroups[group][\bus];
		};

		newGroup = {
			arg pGroupName;
			var addrSave;
			pGroupName = pGroupName.asSymbol;
			if(pgroups[pGroupName] == nil, {pgroups[pGroupName] = ()});// create dict if doesn't exist'

			pgroups[pGroupName][\jumpShift] = 0;
			pgroups[pGroupName][\bus] = Bus.audio(s,2);
			pgroups[pGroupName][\pbindGroup] = Group(s);
			pgroups[pGroupName][\fxGroup] =  Group(pgroups[pGroupName][\synthGroup] , \addToTail);
			pgroups[pGroupName][\pbindListen] =  true;



			addrSave =  ('/' ++ pGroupName ++ '/pctrl/save/x').asSymbol;
			// ctrlr.sendMsg("/browser/groupsSelector",'@label_off', *keys);
			OSCdef((addrSave ++ pGroupName.asString).asSymbol,
				{
					arg msg;
					msg[1].postln;
					if(msg[1]>0.5)
					{
						{
							w = Window.new("save", Rect(Window.availableBounds.center.x-100,Window.availableBounds.center.y , 200,50)).front;
							TextField.new(w,Rect(10,10,180,30)).action_( {
								arg obj;
								"save".postln;
								obj.value.postln;
								{
									var file, dir, saveDict;
									// s.getcwd
									dir = "/Users/lucas/Dropbox/MyWork/00_evdSupercollider/presets/";
									dir = dir ++ pGroupName.asString ++"_"++ obj.value.asString;
									// dir.postln;
									// File.getcwd
									file = File.new(dir,"w");
									// pgroups[pGroupName].postln;
									saveDict = ();
									pgroups[pGroupName][\ctrls].keysValuesDo
									{
										arg key, value;
										// key.postln;
										// value.postln;
										saveDict[key] = value[\value];

									};

									file.write(saveDict.asCompileString);
									file.close;

									// f
								}.defer;

								{w.close}.defer;});
						}.defer;
					}
				},
				addrSave
			);



			//presets:
			4.do{
				arg mapNumber;
				var addrPreset;
				addrPreset =  ('/' ++ pGroupName ++ '/pctrl/preset'++(mapNumber+1).asString++'/x').asSymbol;
				// ctrlr.sendMsg("/browser/groupsSelector",'@label_off', *keys);
				OSCdef((addrPreset ++ pGroupName.asString).asSymbol,
					{
						arg msg;
						msg[1].postln;
						(msg[1]>0.5).if
						{
							(pgroups[pGroupName][\presets][mapNumber] != nil).if{
								var presetName;
								presetName = pgroups[pGroupName][\presets][mapNumber][\name];
								(presetName.asSymbol != \empty).if
								{
									var file, path, dict;
									path = "/Users/lucas/Dropbox/MyWork/00_evdSupercollider/presets/";
									path = path ++ pGroupName.asString ++"_"++ presetName;
									path.postln;
									File.exists(path).if
									{
										file = File.open(path, "r");
										dict = file.readAllString.compile.value;
										dict.postln;
										dict.keysValuesDo{
											arg key, value;
											pgroups[pGroupName][\ctrls][key][\value] = value;
											//make a unocersal set function
											(pgroups[pGroupName][\ctrls][key][\type] == \nodeproxy).if
											{
												// proxySpace[pGroupName].set(key, *(pgroups[pGroupName][\ctrls][key][\value].linlin(0,1,pgroups[pGroupName][\ctrls][key][\minMap],pgroups[pGroupName][\ctrls][key][\maxMap])));
												proxySpace[pGroupName].set(key, *(pgroups[pGroupName][\ctrls][key][\value]));

												// pgroups[pGroupName][\ctrls][ctrlName][\bus] = Bus.control(s,defaultValue.size.max(1));
												// pgroups[pGroupName][\ctrls][ctrlName][\bus].set(defaultValue.linlin(0,1,minMap,maxMap));
											};


											//send to lemurs
											ctrlrs.do{
												arg ctrlr;
												ctrlr.sendMsg(pgroups[pGroupName][\ctrls][key][\addr], *pgroups[pGroupName][\ctrls][key][\value]);
											};
											touchdesignerCHOP.sendMsg(pgroups[pGroupName][\ctrls][key][\addr], *pgroups[pGroupName][\ctrls][key][\value]);

											(pgroups[pGroupName][\ctrls][key][\twisterKnob] != nil).if{
												~twister[pgroups[pGroupName][\ctrls][key][\twisterKnob]][\set].value(pgroups[pGroupName][\ctrls][key][\value]);
											};
											(pgroups[pGroupName][\ctrls][key][\twisterButton] != nil).if{
												~twister[pgroups[pGroupName][\ctrls][key][\twisterButton]][\setButton].value(pgroups[pGroupName][\ctrls][key][\value]);
											};

											//set manual

											// dict[\sinFB].postln;
											// file.readAllString.postln;
										};
									};
								};
							};

						};
					},
					addrPreset
				);

			};

			// map
			4.do{
				arg mapNumber;
				var mapAddr;
				mapAddr = ('/' ++ pGroupName ++ '/pctrl/map'++(mapNumber+1).asString++'/x').asSymbol;
				ctrlrs.do{
					arg ctrlr;
					ctrlr.sendMsg(mapAddr, 0);
				};
				// mapAddr.postln;
				OSCdef((mapAddr ++ pGroupName.asString).asSymbol,
					{
						arg msg, time, addr;

						pGroupName = pGroupName.asSymbol;
						(pgroups[pGroupName][\encoderMappings] != nil).if{//check if encoder mapping exists
							pgroups[pGroupName][\encoderMappings].do{//loop through and look for mapped value
								arg ctrlName, i;
								var knobIndex, hues, hue, ctrlType;
								knobIndex = (mapNumber*4) + i;

								(ctrlName != nil).if{//it it was linked
									(msg[1] > 0.5).if{
										// map
										ctrlName = ctrlName.asSymbol;
										pGroupName = pGroupName.asSymbol;

										hues = ~twisterHues;
										hue = hues[(knobIndex/4).floor];
										m.control(chan:1,ctlNum:knobIndex,val:hue); //ringvalue //10 24 70 84
										m.control(chan:2,ctlNum:knobIndex,val:~twister[knobIndex][\button].linlin(0,1,27,47));
										// ctrlType =

										//set current value to twister:
										~twister[knobIndex][\set].value(pgroups[pGroupName][\ctrls][ctrlName][\value]);
										pgroups[pGroupName.asSymbol][\ctrls][ctrlName.asSymbol][\twisterKnob] = knobIndex;


										~twister[knobIndex].knobCallback = {
											arg twisterValue;

											pgroups[pGroupName][\ctrls][ctrlName][\value] = twisterValue;
											(pgroups[pGroupName][\ctrls][ctrlName][\type] == \bus).if{
												pgroups[pGroupName][\ctrls][ctrlName][\bus].set(*(pgroups[pGroupName][\ctrls][ctrlName]['value'].linlin(0,1,pgroups[pGroupName][\ctrls][ctrlName][\minMap],pgroups[pGroupName][\ctrls][ctrlName][\maxMap])));
											};
											(pgroups[pGroupName][\ctrls][ctrlName][\type] == \nodeproxy).if{
												// proxySpace[pGroupName].set(ctrlName, *(pgroups[pGroupName][\ctrls][ctrlName][\value].linlin(0,1,pgroups[pGroupName][\ctrls][ctrlName][\minMap],pgroups[pGroupName][\ctrls][ctrlName][\maxMap])));
												proxySpace[pGroupName].set(ctrlName, *(pgroups[pGroupName][\ctrls][ctrlName][\value]));

												// pgroups[pGroupName][\ctrls][ctrlName][\bus] = Bus.control(s,defaultValue.size.max(1));
												// pgroups[pGroupName][\ctrls][ctrlName][\bus].set(defaultValue.linlin(0,1,minMap,maxMap));
											};


											(pgroups[pGroupName][\jumpShift] <0.5).if
											{
												//send to lemurs
												ctrlrs.do{
													arg ctrlr;
													ctrlr.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\addr], pgroups[pGroupName][\ctrls][ctrlName][\value]);
												};
												touchdesignerCHOP.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\addr], *pgroups[pGroupName][\ctrls][ctrlName][\value]);

											};
										};
									}{//unmap
										//check it was mapped or if it's been remapped
										~twister[knobIndex].knobCallback = nil;
										m.control(chan:2,ctlNum:knobIndex,val:18);//off
										pgroups[pGroupName][\ctrls][ctrlName][\twisterKnob] = nil; //culprate
										m.control(chan:0,ctlNum:knobIndex,val:0);


									};
									//check if mapped when incoming OSC from lemur
								};
							};
						};

						// SAME for buttons
						// pgroups[pGroupName][\buttonMappings].postln;
						(pgroups[pGroupName][\buttonMappings] != nil).if{//check if encoder mapping exists
							pgroups[pGroupName][\buttonMappings].do{//loop through and look for mapped value
								arg ctrlName, i;
								var knobIndex, hues, hue;
								// pGroupName = pGroupName.asSymbol;

								knobIndex = (mapNumber*4) + i;


								(ctrlName != nil).if{//if it was linked
									(msg[1] > 0.5).if{
										ctrlName = ctrlName.asSymbol;
										pGroupName = pGroupName.asSymbol;

										// map

										hues = ~twisterHues;
										hue = hues[(knobIndex/4).floor];
										m.control(chan:1,ctlNum:knobIndex,val:hue); //ringvalue //10 24 70 84
										m.control(chan:2,ctlNum:knobIndex,val:~twister[knobIndex][\button].linlin(0,1,27,47));
										//set current value to twister:
										// ctrlName.postln;
										// pgroups[pGroupName].postln;
										// pgroups[pGroupName][\ctrls][ctrlName].postln;
										~twister[knobIndex][\setButton].value(pgroups[pGroupName][\ctrls][ctrlName][\value]);


										~twister[knobIndex][\toggle] = pgroups[pGroupName][\buttonMappingsToggle][i];


										pgroups[pGroupName][\ctrls][ctrlName][\twisterButton] = knobIndex;//store for OSC

										~twister[knobIndex].buttonCallback = {
											arg twisterValue;
											pgroups[pGroupName][\ctrls][ctrlName][\value] = twisterValue;
											(pgroups[pGroupName][\ctrls][ctrlName][\type] == \bus).if{
												pgroups[pGroupName][\ctrls][ctrlName][\bus].set(*(pgroups[pGroupName][\ctrls][ctrlName]['value'].linlin(0,1,pgroups[pGroupName][\ctrls][ctrlName][\minMap],pgroups[pGroupName][\ctrls][ctrlName][\maxMap])));
											};

											//send to lemurs
											ctrlrs.do{
												arg ctrlr;
												ctrlr.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\addr], pgroups[pGroupName][\ctrls][ctrlName][\value]);
											};
											touchdesignerCHOP.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\addr], pgroups[pGroupName][\ctrls][ctrlName][\value]);

										};
									}{//unmap
										~twister[knobIndex].buttonCallback = nil;
										m.control(chan:2,ctlNum:knobIndex,val:18);//off
										// ~twister[knobIndex]
										// m.control(chan:1,ctlNum:knobIndex,val:hue); //ringvalue //10 24 70 84

										pgroups[pGroupName][\ctrls][ctrlName][\twisterButton] = nil;

									};
									//check if mapped when incoming OSC from lemur
								};
							};
						};
						// pgroups[pGroupName].postln;// pGroupName.postln;

				} , mapAddr);

			};



			//listerners/mecanisms for play, pause (Rests), reset, ctrlr... etc

			//////////// ----- JUMP

			OSCdef(('/' ++ pGroupName ++ '/jump').asSymbol,
				{
					arg msg, time, addr;
					(msg[1] > 0.5).if{
						//for pMaps:
						(pgroups[pGroupName][\ctrls] != nil).if
						{
							pgroups[pGroupName][\ctrls].keysValuesDo{
								arg ctrlname , ctrldict;
								//set value
								ctrldict['value'] = ctrldict['shiftValue'];
								(ctrldict[\type] == \bus).if
								{
									ctrldict[\bus].set(*(ctrldict['value'].linlin(0,1,ctrldict[\minMap],ctrldict[\maxMap])));
								};

								//send values
								ctrlrs.do{
									arg ctrlr;
									ctrlr.sendMsg(ctrldict['addr'], *ctrldict['value']);
								};

								(ctrldict[\twisterKnob] != nil).if{
									~twister[ctrldict[\twisterKnob]][\set].value(ctrldict[\value]);
								};
								(ctrldict[\twisterButton] != nil).if{
									~twister[ctrldict[\twisterButton]][\setButton].value(ctrldict[\value]);
								};
							};
						};
						/*(pgroups[pGroupName] != nil).if
						{
						pgroups[pGroupName].keysValuesDo{
						arg ctrlname , ctrldict;
						//set value
						ctrldict['value'] = ctrldict['shiftValue'];
						ctrldict[\bus].set(*(ctrldict['value'].linlin(0,1,ctrldict[\minMap],ctrldict[\maxMap])));

						//send values
						ctrlrs.do{
						arg ctrlr;
						ctrlr.sendMsg(ctrldict['addr'], *ctrldict['value']);

						};
						};
						};*/

					};

				}
				, '/jump/x'.asSymbol);


			/////////// --------------------     shiftMode
			OSCdef(('/' ++ pGroupName ++ '/jumpShift').asSymbol,
				{
					arg msg, time, addr;

					ctrlrs.do{
						arg ctrlr;
						(ctrlr != addr).if{
							ctrlr.sendMsg('/jumpShift/x', msg[1]);
						};
					};

					(msg[1] > 0.5).if{
						pgroups[pGroupName][\jumpShift]  = 1;

						/*pgroups[pGroupName][\ctrls].keysValuesDo{
						arg ctrlname , ctrldict;
						ctrlrs.do{
						arg ctrlr;
						ctrlr.sendMsg(ctrldict['addr'], *ctrldict['shiftValue']);
						ctrlr.sendMsg(ctrldict['addr'].asString.replace("/x", ""),'@color', shiftJumpColor);//send color orange
						};
						};*/
						(pgroups[pGroupName][\ctrls] != nil).if
						{
							pgroups[pGroupName][\ctrls].keysValuesDo{
								arg ctrlname , ctrldict;
								ctrlrs.do{
									arg ctrlr;
									ctrlr.sendMsg(ctrldict['addr'], *ctrldict['shiftValue']);
									ctrlr.sendMsg(ctrldict['addr'].asString.replace("/x", ""),'@color', shiftJumpColor);//send color orange
								};
							};
						};

					}{
						pgroups[pGroupName][\jumpShift]  = 0;
						/*pgroups[pGroupName][\ctrls].keysValuesDo{
						arg ctrlname , ctrldict;
						ctrlrs.do{
						arg ctrlr;
						ctrlr.sendMsg(ctrldict['addr'], *ctrldict['value']);
						ctrlr.sendMsg(ctrldict['addr'].asString.replace("/x", ""),'@color', normalColor);//send color white
						};
						};*/
						(pgroups[pGroupName][\ctrls] != nil).if
						{
							pgroups[pGroupName][\ctrls].keysValuesDo{
								arg ctrlname , ctrldict;
								ctrlrs.do{
									arg ctrlr;
									ctrlr.sendMsg(ctrldict['addr'], *ctrldict['value']);
									ctrlr.sendMsg(ctrldict['addr'].asString.replace("/x", ""),'@color', normalColor);//send color white
								};
							};
						};

					};
			} , '/jumpShift/x'.asSymbol);
		};

		fMap = {

		};

		linkEncoder = {
			arg pGroupName, ctrlName, encoder;

			//setup if doesn't exist
			(pgroups[pGroupName][\encoderMappings] == nil).if{pgroups[pGroupName][\encoderMappings] = nil!4;};
			pgroups[pGroupName][\encoderMappings][encoder] = ctrlName;
			// pgroups[pGroupName][\encoderMappings].postln;

		};

		linkButton = {
			arg pGroupName, ctrlName, button, toggle;

			//setup if doesn't exist
			(pgroups[pGroupName][\buttonMappings] == nil).if{pgroups[pGroupName][\buttonMappings] = nil!4;};
			pgroups[pGroupName][\buttonMappings][button] = ctrlName;


			(pgroups[pGroupName][\buttonMappingsToggle] == nil).if{pgroups[pGroupName][\buttonMappingsToggle] = nil!4;};
			pgroups[pGroupName][\buttonMappingsToggle][button] = toggle;

		};
		allMap = {
			arg pGroupName, ctrlName, defaultValue,minMap, maxMap, type = \bus, function = nil, postfix; //\pfunc \function ... \nodeproxy
			var return;
			if(pgroups[pGroupName] == nil, {pgroups[pGroupName] = ()});// create dict if doesn't exist'
			if(pgroups[pGroupName][\ctrls] == nil, {pgroups[pGroupName][\ctrls] = ()});// create dict if doesn't exist'

			// ctrlName = ctrlName + postfix;
			// (encMap != -1).if{ encMap.postln;};

			//init ctrl
			pgroups[pGroupName][\ctrls][ctrlName] = ();
			pgroups[pGroupName][\ctrls][ctrlName][\type] = type;

			pgroups[pGroupName][\ctrls][ctrlName][\value] = defaultValue;
			pgroups[pGroupName][\ctrls][ctrlName][\presets] = defaultValue!4;
			pgroups[pGroupName][\ctrls][ctrlName][\shiftValue] = defaultValue;
			pgroups[pGroupName][\ctrls][ctrlName][\addr] = ('/'++ pGroupName ++ '/'++ ctrlName ++ postfix).asSymbol;
			// pgroups[pGroupName][\ctrls][ctrlName][\addr].postln;
			(type == \bus).if{
				pgroups[pGroupName][\ctrls][ctrlName][\minMap] = minMap;
				pgroups[pGroupName][\ctrls][ctrlName][\maxMap] = maxMap;
				pgroups[pGroupName][\ctrls][ctrlName][\bus] = Bus.control(s,defaultValue.size.max(1));
				pgroups[pGroupName][\ctrls][ctrlName][\bus].set(defaultValue.linlin(0,1,minMap,maxMap));
			};

			(type == \nodeproxy).if{
				pgroups[pGroupName][\ctrls][ctrlName][\minMap] = minMap;
				pgroups[pGroupName][\ctrls][ctrlName][\maxMap] = maxMap;
				// pgroups[pGroupName][\ctrls][ctrlName][\bus] = Bus.control(s,defaultValue.size.max(1));
				// pgroups[pGroupName][\ctrls][ctrlName][\bus].set(defaultValue.linlin(0,1,minMap,maxMap));
			};




			// pgroups[pGroupName][\ctrls][ctrlName][\setFunction] = {
			//
			// };

			// pgroups[pGroupName][\ctrls][ctrlName][\encMap] = encMap;
			// pgroups[pGroupName][\ctrls][ctrlName][\addr].postln;
			OSCdef(pgroups[pGroupName][\ctrls][ctrlName][\addr],
				{
					arg msg, time, addr;
					var value;
					// value.postln;
					value = msg[1..];
					(value.size == 1).if{value = value[0]};
					// [1].size
					(pgroups[pGroupName][\jumpShift]>0.5).if{
						pgroups[pGroupName][\ctrls][ctrlName][\shiftValue] = value;
						//send all the shift values

					}{
						// msg.postln;
						// pgroups[pGroupName][\ctrls][ctrlName].postln;
						pgroups[pGroupName][\ctrls][ctrlName][\value] = value;
						(type == \bus).if{
							pgroups[pGroupName][\ctrls][ctrlName][\bus].set(*(value.linlin(0,1,minMap,maxMap)));
						};
						// ctrlName.postln;
						// value.postln;
						(type == \nodeproxy).if{
							// value.postln;
							// proxySpace[pGroupName].set(ctrlName, value.linlin(0,1,minMap,maxMap));
							proxySpace[pGroupName].set(ctrlName, *value);
							//NodeProxy
							// pgroups[pGroupName][\ctrls][ctrlName][\bus] = Bus.control(s,defaultValue.size.max(1));
							// pgroups[pGroupName][\ctrls][ctrlName][\bus].set(defaultValue.linlin(0,1,minMap,maxMap));
						};



						//OSC feedback
						ctrlrs.do{
							arg ctrlr;
							(ctrlr != addr).if{
								ctrlr.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\addr], *pgroups[pGroupName][\ctrls][ctrlName][\value]);
							};
						};
						touchdesignerCHOP.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\addr], *pgroups[pGroupName][\ctrls][ctrlName][\value]);


						(pgroups[pGroupName][\ctrls][ctrlName][\twisterKnob] != nil).if{
							~twister[pgroups[pGroupName][\ctrls][ctrlName][\twisterKnob]][\set].value(pgroups[pGroupName][\ctrls][ctrlName][\value]);
						};
						(pgroups[pGroupName][\ctrls][ctrlName][\twisterButton] != nil).if{
							~twister[pgroups[pGroupName][\ctrls][ctrlName][\twisterButton]][\setButton].value(pgroups[pGroupName][\ctrls][ctrlName][\value]);
						};


					}
			} , pgroups[pGroupName][\ctrls][ctrlName][\addr]);

			//send initial value
			ctrlrs.do{
				arg ctrlr;
				ctrlr.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\addr], *defaultValue);
			};
			touchdesignerCHOP.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\addr], *pgroups[pGroupName][\ctrls][ctrlName][\value]);


			(type == \pfunc).if{
				return = Pfunc({pgroups[pGroupName][\ctrls][ctrlName][\value]}); // return Pfunc
			};
			(type == \bus).if{
				// "returningbus".postln;
				(pgroups[pGroupName][\ctrls][ctrlName][\bus].numChannels == 1).if{
					return = pgroups[pGroupName][\ctrls][ctrlName][\bus].asMap; //return bus
				}
				{
					return = pgroups[pGroupName][\ctrls][ctrlName][\bus].numChannels.collect{arg i; ("c" ++ (pgroups[pGroupName][\ctrls][ctrlName][\bus].index + i).asString).asSymbol}; //return bus
				};
			};

			return;
		};


		makeCtrlUI = {
			arg pGroupName;

			// pgroups[pGroupName];
			// pgroups[pGroupName];
			// makeFader...
			// copy to paste buffer
		};

		sendSignal = {
			arg group, name, signal;
			var address;
			address = "/" + group.asString+/+ "proxy" +/+ name;
			address = address.replace(" ", "");
			address.postln;
			OSCdef(
				address,//here is the problem, using the same OSC address for multiple instruments, overrides the last one...
				{
					arg msg;
					var values;
					values = msg[3..];
					// values.postln;
					{touchdesignerCHOP.sendMsg(address, *values)}.defer(~visualSync);


				}, address
			);
			SendReply.kr(Impulse.kr(60), address, signal, -1);

		};

		newID = {
			arg group, count, instrument;
			var assignedID;
			assignedID = [];

			if(pgroups[group] == nil, {pgroups[group] = ()});// create dict if doesn't exist'
			if(pgroups[group]['assignedIDs'] == nil, {pgroups[group]['assignedIDs'] = ()});// create dict if doesn't exist'
			if(pgroups[group]['assignedIDs'][instrument] == nil, {pgroups[group]['assignedIDs'][instrument] = []});// create dict if doesn't exist'

			count.do{
				replyIDCount = replyIDCount+1;
				pgroups[group]['assignedIDs'][instrument] = pgroups[group]['assignedIDs'][instrument].add(replyIDCount);
				assignedID = assignedID.add(replyIDCount);
			};
			// (SynthDescLib.global[instrument].metadata != nil).if
			// {
			// instrument.postln;
			// SynthDescLib.global[instrument].metadata[\oscReplies].postln;
			SynthDescLib.global[instrument].metadata.includesKey(\oscReplies).if
			{
				SynthDescLib.global[instrument].metadata[\oscReplies].do
				{
					arg addr;
					OSCdef(
						addr,//here is the problem, using the same OSC address for multiple instruments, overrides the last one...
						{
							arg msg;
							var values, id, idIndex, addrOut;
							id = msg[2];
							idIndex = pgroups[group]['assignedIDs'][instrument].find([id]);
							(idIndex != nil).if // corresponding id
							{
								values = msg[3..];
								addrOut = addr.replace("/" ++ instrument.asString, "");
								addrOut= "/" ++ group.asString ++ "/" ++ instrument.asString ++ "/" ++ idIndex.asString ++ addrOut;

								touchdesignerCHOP.sendMsg(addrOut, *values);
							}

						},
						addr
					);
				};
			};
			// };
			assignedID;
		};


		newProxyGroup = {
			arg group, listen, targetGroup, fadeOut = 2, fadeIn = 2;

			(pgroups[group] == nil).if{
				"creating new group".postln;
				newGroup.value(group);
			};
			//create default group structure
			pgroups[group][\listen] = listen;//store init variable

			{proxySpace[group].pause; proxySpace[group].stop;}.defer(1);//stops all on initialisation

			//if is a nodeproxy, gather key values to map
			(proxySpace[group].isKindOf(NodeProxy)).if{
				var pairs;
				if(proxySpace[group].source.isKindOf(Function),
					{
						pairs = proxySpace[group].getKeysValues;
					}
				);

				pairs.isNil.not.if{
					pairs.do{
						arg item, i;
						allMap.value(group, item[0], item[1], 0, 1, \nodeproxy, nil, '/x');
					};
				};

				OSCdef(('/' ++ group ++ '/pctrl/play/x/proxy').asSymbol, // add?
					{
						arg msg, time, addr;
						(msg[1] > 0.5).if
						{

							proxySpace[group].resume;
							pgroups[group][\listen].if{
								(targetGroup != nil).if
								{
									proxySpace[group].play(group:targetGroup, addAction: 'addToTail', fadeTime: fadeIn);//if output//NodeProxy
								}
								{
									proxySpace[group].play(fadeTime: fadeIn);//if output//NodeProxy

								};
							};
							touchdesignerCHOP.sendMsg('/' ++ group ++ '/pctrl/play/x', 1);

						}
						{
							// "stopping".postln;
							//pro
							// proxySpace[group].stop;
							proxySpace[group].stop(fadeOut);
							{proxySpace[group].pause;}.defer(fadeOut);
							// proxySpace[group].pause;
							proxySpace.clock.playNextBar({touchdesignerCHOP.sendMsg('/' ++ group ++ '/pctrl/play/x', 0); });
						};
						ctrlrs.do{
							arg ctrlr;
							ctrlr.sendMsg(('/' ++ group ++ '/pctrl/play/x').asSymbol, msg[1]);
						};

				} , ('/' ++ group ++ '/pctrl/play/x').asSymbol);
				ctrlrs.do{
					arg ctrlr;
					ctrlr.sendMsg(('/' ++ group ++ '/pctrl/play/x').asSymbol, 0);
				};

			};
		};

		(
			// pMap: {arg self, pGroupName, ctrlName, defaultValue = 0, encMap = -1;  pMap.value(pGroupName, ctrlName, defaultValue, encMap)},
			// bMap: {arg self, pGroupName, ctrlName, defaultValue = 0, minMap = 0, maxMap = 1, encMap = -1; bMap.value(pGroupName, ctrlName, defaultValue, minMap, maxMap, encMap)},
			pMap: {arg self, pGroupName, ctrlName, defaultValue = 0, postfix = '/x';  allMap.value(pGroupName, ctrlName, defaultValue, 0,1,\pfunc, nil, postfix)},
			bMap: {arg self, pGroupName, ctrlName, defaultValue = 0, minMap = 0, maxMap = 1, postfix = '/x'; allMap.value(pGroupName, ctrlName, defaultValue, minMap, maxMap, \bus,nil, postfix)},
			proxyMap: {arg self, pGroupName, ctrlName, defaultValue = 0, minMap = 0, maxMap = 1; allMap.value(pGroupName, ctrlName, defaultValue, minMap, maxMap, \nodeproxy)},

			newPGroup: {arg self, pGroupName, pattern, quant = 4; newPGroup.value(pGroupName, pattern, quant)},
			makeCtrlUI: {arg self, pGroupName; makeCtrlUI.value(pGroupName)},
			newID: {arg self, group, count=1, instrument; newID.value(group, count, instrument)},
			linkEncoder: {arg self, pGroupName, ctrlName, encoder; linkEncoder.value(pGroupName, ctrlName, encoder)},
			linkButton: {arg self, pGroupName, ctrlName, button, toggle = 1; linkButton.value(pGroupName, ctrlName, button, toggle)},
			proxySpace: {arg self; proxySpace;},
			newProxyGroup: {arg self, group, listen = false, targetGroup = nil; newProxyGroup.value(group, listen, targetGroup );},
			setupPreset: {arg self, pGroupName, presetName, index = 0; setupPreset.value(pGroupName, presetName, index)},
			sendSignal: {arg self, group, name, sig; sendSignal.value(group, name, sig);},
			chainProxyFX: {arg self, group, graph; chainProxyFX.value(group, graph)},
			pbus: {arg self, group; pbus.value(group)},
			setTempo: {arg self, tempo; setTempo.value(tempo)},

			//
		);
	};
	s.sync;

});
)

//
// (
// ~mapper = ~mapperMaker.value;
//
// ~mapper.proxySpace[\softHighs] = {
// 	arg fb, detuneFB, volume, fasterSines, subSines, noise, sinFB, t60, modVerb;
// 	var sines, notes, amps, fbSig, verb, lowBeating;
// 	notes = [60,72,79].midicps;
//
//
// 	sines = notes.collect({ arg item; SinOscFB.ar((item * LFNoise1.ar(0.001!4).range(-0.12,0.12).midiratio )+(WhiteNoise.ar(noise.lag(2)*100)),sinFB.lag.linlin(0,1,0.04,0.2), mul:0.1*SinOsc.ar(0.01+ fasterSines.linlin(0,1,0,10),2pi.rand).range(0,1));});
// 	sines  = Splay.ar(sines.sum);
//
// 	lowBeating = SinOsc.ar(36.midicps +[0,0.5,0.1], mul:0.08*subSines.pow(1.5).lag(0.5));
// 	sines = sines + (lowBeating.sum!2);
// 	// sines;
// 	fbSig = LocalIn.ar(2);
// 	fbSig  = PitchShift.ar(fbSig, 1,(5+detuneFB.linlin(0,1,-0.5,0.5)).midiratio,mul:fb.linlin(0,1,0,0.6).lag)+sines;
// 	fbSig = LPF.ar(fbSig,4000);
// 	LocalOut.ar(fbSig);
//
//
// 	verb = JPverb.ar(fbSig* volume,t60:t60.linlin(0,1,1,10),damp:0.1,size:20,earlyDiff:0.2,modDepth:modVerb.lag(1),modFreq:0.2,low:1,mid:1,high:1, lowcut:100, highcut:6000);
// 	verb ;
// 	// Limiter.ar(fb, 0.1);
//
//
// };
//
// ~mapper.proxySpace[\softHighs].play;
// ~mapper.newProxyGroup(\softHighs);
//
// // ~mapper.proxySpace[\softHighs].set(\volume_x, 0.2);
//
// )
//
