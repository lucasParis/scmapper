
(
"loading resources".postln;

~visualSync = 0; //for visual sync: delays the sending of OSC messages to delay visual synthesis

~touchIP = "127.0.0.1"; // ip of the computer running touchdesigner
// ~touchIP = "10.10.10.4";

// s.options.numOutputBusChannels = 9; // option for surround projects, copy to project directory, before executing this file

//options memory/max connections
s.options.memSize_(2.pow(20));
s.options.numWireBufs = 512;
s.options.maxSynthDefs  =2048;

//boot server
s.waitForBoot({

	//load resources
	"resourcesSC/synthlib.scd".loadRelative; // load synth library
	"resourcesSC/noteFX.scd".loadRelative;  // load custom fx events
	"resourcesSC/makeTwister.scd".loadRelative; // load midi twister stuff
	"resourcesSC/evtToPyDict.scd".loadRelative; // load note event to python dict converter


	/*
	mapperMaker
	a master thing that contains:
	- groups
	- each containing parameters and their various callbacks
	- simplification of mapping to a pattern
	- preset management
	- metactrl
	- automatic sending to touchdesigner
	... etc
	*/
	~mapperMaker = {
		var pMap, bMap, fMap, pgroups, newPGroup, makeCtrlUI, currentGroup, collectToOsc, touchdesignerDAT, ctrlrs, normalColor, shiftJumpColor, newID, replyIDCount, touchdesignerCHOP, linkEncoder, linkButton, sendBrowserNames, visualSync, allMap, proxySpace, newProxyGroup, newGroup, setupPreset, sendSignal, chainProxyFX, pbus, setTempo, newIDOverlap;

		//setup twister
		~makeTwister.value;

		//init a common proxyspace
		proxySpace.clear;
		proxySpace = ProxySpace.new(s);
		proxySpace.makeTempoClock(2);//create a tempoclock, for syncing server side and client side sequencing

		replyIDCount = 0;//counter for allocation of reply ids

		//colors for lemur, for changing widgets according to mode
		normalColor = 4868682;
		shiftJumpColor = 8336384;

		//setup ctrlrs (lemur/open-stage-control) osc outputs
		ctrlrs = [];
		ctrlrs = ctrlrs.add(NetAddr("127.0.0.1", 7000));//ipad 1
		ctrlrs = ctrlrs.add(NetAddr("127.0.0.1", 8000));//ipad 2

		//touchdesigner/visuals OSC outputs (for pbind events, synthdef replies, proxy signals, and group play/stop)
		touchdesignerDAT = NetAddr(~touchIP, 10000);//DAT for note events
		touchdesignerCHOP = NetAddr(~touchIP, 10001);// CHOP for signals

		//initial setup of ctrlr's loading/navigation menu
		//todo pass2: gather groups names in datastructure and send them automatically: .sendLoadData
		ctrlrs.do{
			arg ctrlr;
			var keysLight, keys =["shortIntroA", "darkSection",  "candy", "risers", "killRythm", "pattern1", "neural"];//keys can be gathered from data structure
			ctrlr.sendMsg("/browser/groupsSelector",'@labels', *keys);//send group names to loading menu
			keysLight = 0!(keys.size) ++ (-1!(28-keys.size));//disactivate light on empty group slots in loading buttons
			ctrlr.sendMsg("/browser/groupsSelector/light", *keysLight);//send lights
		};

		//simple rerouting to monitor touchdesigner's FPS on ipads
		OSCdef(\fpsReroute,
			{
				arg msg;
				//loop through controlers
				ctrlrs.do{
					arg ctrlr;
					ctrlr.sendMsg("/fps/value", msg[1]);//send fps
				};
			}, "/touch/fps"
		);


		//collect formating of event data for touchdesigner as python dicts
		collectToOsc = {
			arg event, adr = "instrumentName";
			var evt = event.copy, stringEvent, sendAddr;//copy event, leave the original event unmodified

			//add patternEvent tag and instrument name to OSC address
			sendAddr = ('/patternEvent/'++ adr ++ '/' ++ evt[\instrument].asString);

			//optional osc address append, to diferentiate the same instrument in multiple patterns of the same group
			(evt[\osc_append] != nil).if{
				sendAddr = sendAddr ++ '/' ++ (evt[\osc_append].asString);//add string to ending
			};

			//store rest in key for TD
			evt[\isRest] = evt.isRest;
			//convert dur from Rest to Int if Rest
			evt[\isRest].if{
				evt[\dur] = evt[\dur].value;
			};
			//convert dur from beat to seconds
			evt[\dur] = evt[\dur] / proxySpace.clock.tempo;

			//format event into a python dictionnary
			stringEvent = ~evtToPythonDictString.value(evt);

			//send to TD with a delay for visual sync
			{
				touchdesignerDAT.sendMsg(sendAddr , *["stringEvent", stringEvent.asSymbol]);
			}.defer(max(s.latency-(~visualSync), 0));

			//return original event for OSC
			event;
		};


		//database for dictionnaries of all groups, containing parameters, presets, and more
		pgroups = ();


		//function to set proxyspace clock tempo in BPM
		setTempo = {
			arg tempo;
			proxySpace.clock.tempo = tempo/60;
		};

		//create a new pattern group (add OSC play/stop, reset), calls parent newGroup
		newPGroup = {
			arg pGroupName, pattern, quant;
			newGroup.value(pGroupName);// parent method newGroup

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

		//create a new group
		newGroup = {
			arg pGroupName;
			var addrSave;//osc address of save preset button

			//double check that is symbol, better then string for identity comparison
			pGroupName = pGroupName.asSymbol;

			// create dict if doesn't exist'
			if(pgroups[pGroupName] == nil, {pgroups[pGroupName] = ()});

			//prepare group variables
			pgroups[pGroupName][\jumpShift] = 0;//jump shift mode state
			pgroups[pGroupName][\bus] = Bus.audio(s,2);//bus for pbind output
			pgroups[pGroupName][\pbindGroup] = Group(s);//group for pbind
			pgroups[pGroupName][\fxGroup] =  Group(pgroups[pGroupName][\pbindGroup] , \addToTail);//add fx group for chained proxy to tail of pbind group
			pgroups[pGroupName][\pbindListen] =  true;//used to hear only wet signal when used with proxy

			//osc address of save preset button
			addrSave =  ('/' ++ pGroupName ++ '/pctrl/save/x').asSymbol;

			//Next: various OSC callbacks for save preset, load preset, map encoder, jump/shift

			//Save OSC callback
			OSCdef((addrSave ++ pGroupName.asString).asSymbol,//unique symbol with group name
				{
					arg msg;
					if(msg[1]>0.5)//on button down
					{
						{//defered to other thread
							//create popup UI for save dialog
							w = Window.new("save", Rect(Window.availableBounds.center.x-100,Window.availableBounds.center.y , 200,50)).front;
							//textfield with callback
							TextField.new(w,Rect(10,10,180,30)).action_( {
								arg obj;
								{
									var file, dir, saveDict;

									//preset files on HD
									dir = "/Users/lucas/Dropbox/MyWork/00_evdSupercollider/presets/";//hardcoded
									dir = dir ++ pGroupName.asString ++"_"++ obj.value.asString;//new file name according to group and entered name

									//create writable file
									file = File.new(dir,"w");

									//datastructure to fill with save values
									saveDict = ();
									//get values for preset from ctrls in group's datastructure
									pgroups[pGroupName][\ctrls].keysValuesDo
									{
										arg key, value;
										saveDict[key] = value[\value];//add key/value from ctrls to dict
									};
									//write preset dict to file
									file.write(saveDict.asCompileString);
									file.close;
								}.defer;//defered to other thread

								//close window
								{w.close}.defer;

							});
						}.defer;//defered to other thread
					}
				}, addrSave //OSCdef address, for save
			);

			//4 preset loading buttons
			4.do{
				arg mapNumber;
				var addrPreset;
				//prepare OSC address of loadPreset button
				addrPreset =  ('/' ++ pGroupName ++ '/pctrl/preset'++(mapNumber+1).asString++'/x').asSymbol;

				//osc listener for preset load
				OSCdef((addrPreset ++ pGroupName.asString).asSymbol,
					{
						arg msg;
						(msg[1]>0.5).if{// on button down
							(pgroups[pGroupName][\presets][mapNumber] != nil).if{//if presets have been setup
								var presetName;
								presetName = pgroups[pGroupName][\presets][mapNumber][\name];// get the assigned preset name
								(presetName.asSymbol != \empty).if{// if a preset has been assigned to this button
									var file, path, dict;

									//make path
									path = "/Users/lucas/Dropbox/MyWork/00_evdSupercollider/presets/";//hardcoded preset path, change to configurable path
									path = path ++ pGroupName.asString ++"_"++ presetName;//append group/preset to get file name

									File.exists(path).if{
										file = File.open(path, "r");

										//read string and execute it to get the savec dict
										dict = file.readAllString.compile.value;

										//loop through dict and load values
										dict.keysValuesDo{
											arg key, value;

											//set value (for pbind)
											pgroups[pGroupName][\ctrls][key][\value] = value;

											//set bus value
											(pgroups[pGroupName][\ctrls][key][\type] == \bus).if{
												pgroups[pGroupName][\ctrls][key][\bus].set(*(value.linlin(0,1,pgroups[pGroupName][\ctrls][key][\minMap],pgroups[pGroupName][\ctrls][key][\maxMap])));
											};

											//set proxy value
											(pgroups[pGroupName][\ctrls][key][\type] == \nodeproxy).if{
												proxySpace[pGroupName].set(key, *(pgroups[pGroupName][\ctrls][key][\value]));
											};

											//send to lemurs
											ctrlrs.do{
												arg ctrlr;
												ctrlr.sendMsg(pgroups[pGroupName][\ctrls][key][\addr], *pgroups[pGroupName][\ctrls][key][\value]);
											};

											//send to touch CHOP \touchAddr
											touchdesignerCHOP.sendMsg(pgroups[pGroupName][\ctrls][key][\touchAddr], *pgroups[pGroupName][\ctrls][key][\value]);

											//if twister knob is mapped, update ctrlr visual feedback
											(pgroups[pGroupName][\ctrls][key][\twisterKnob] != nil).if{
												~twister[pgroups[pGroupName][\ctrls][key][\twisterKnob]][\set].value(pgroups[pGroupName][\ctrls][key][\value]);
											};
											//if twister button is mapped, update ctrlr visual feedback
											(pgroups[pGroupName][\ctrls][key][\twisterButton] != nil).if{
												~twister[pgroups[pGroupName][\ctrls][key][\twisterButton]][\setButton].value(pgroups[pGroupName][\ctrls][key][\value]);
											};
										};
									};
								};
							};
						};
					},addrPreset);//OSCdef address
			};

			//osc listener for mapping button to twister row
			4.do{//4 buttons
				arg mapNumber;
				var mapAddr;
				//prepare OSC address of map button, /group/pctrl/map#/x
				mapAddr = ('/' ++ pGroupName ++ '/pctrl/map'++(mapNumber+1).asString++'/x').asSymbol;
				//initialise button ctrlr value to 0
				ctrlrs.do{
					arg ctrlr;
					ctrlr.sendMsg(mapAddr, 0);
				};

				//osc listener for twister row map
				// OSCdef((mapAddr ++ pGroupName.asString).asSymbol,//groupname already in mapAddr
				OSCdef(mapAddr.asSymbol,//groupname already in mapAddr
					{
						arg msg, time, addr;

						//make sure group name is symbol
						pGroupName = pGroupName.asSymbol;
						//check if encoder mapping exists
						(pgroups[pGroupName][\encoderMappings] != nil).if{
							pgroups[pGroupName][\encoderMappings].do{//loop through and look for mapped value
								arg ctrlName, i;
								var knobIndex, hues, hue, ctrlType;
								knobIndex = (mapNumber*4) + i;

								(ctrlName != nil).if{//it it was linked
									(msg[1] > 0.5).if{// on button down
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
			pgroups[pGroupName][\ctrls][ctrlName][\touchAddr] = ('/' ++ 'controls' ++ '/'++ pGroupName ++ '/'++ ctrlName ++ postfix).asSymbol;
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
						touchdesignerCHOP.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\touchAddr], *pgroups[pGroupName][\ctrls][ctrlName][\value]);


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
			address = "/" + group.asString++ '/' ++ "proxy" ++ '/' ++ name;
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

		//assign a reply id and add a callback for rerouting value to TouchDesigner
		newID = {
			arg group, count, instrument;
			var assignedID;
			assignedID = [];

			//setup for group and assignedIDs in data structure
			if(pgroups[group] == nil, {pgroups[group] = ()});// create dict if doesn't exist'
			if(pgroups[group]['assignedIDs'] == nil, {pgroups[group]['assignedIDs'] = ()});// create dict if doesn't exist'
			if(pgroups[group]['assignedIDs'][instrument] == nil, {pgroups[group]['assignedIDs'][instrument] = []});// create dict if doesn't exist'

			//count is the number of IDs asked for and set in data structure
			count.do{
				replyIDCount = replyIDCount+1;//increment ID counter/allocator
				pgroups[group]['assignedIDs'][instrument] = pgroups[group]['assignedIDs'][instrument].add(replyIDCount);// append to array of group's ids
				assignedID = assignedID.add(replyIDCount);// add to local array
			};

			//if synthdef is setup to reroute OSC replies
			SynthDescLib.global[instrument].metadata.includesKey(\oscReplies).if
			{
				// loop through the synthdef's osc reply addresses
				SynthDescLib.global[instrument].metadata[\oscReplies].do
				{
					arg addr;

					//OSC callback for the replies (rerouting to touch), based on replyids stored in database
					OSCdef(
						(group ++ addr).asSymbol, //osccallback name with group
						{
							arg msg;
							var values, replyID, idIndex, addrOut;
							replyID = msg[2];//get the replyID in the osc message
							idIndex = pgroups[group]['assignedIDs'][instrument].find([replyID]);//search for the replyID in the database
							(idIndex != nil).if // if replyID is indexed in database
							{
								//get signal value(s) from reply
								values = msg[3..];
								//rename & prepare addr to touch
								addrOut = addr.replace("/" ++ instrument.asString, "");//remove instrument from address
								addrOut= "/" ++ group.asString ++ "/" ++ instrument.asString ++ "/" ++ idIndex.asString ++ addrOut;// format address with group/instrmnt/indx/par

								//send values
								touchdesignerCHOP.sendMsg(addrOut, *values);
							}
						},
						addr
					);
				};
			};

			assignedID;//return assigned id
		};

		// iterativeOverlap replyID
		newIDOverlap = {
			//poly is the number of voices in the pbind
			//overlaps is the number of visual overlaps for sustained synths
			arg group, poly, overlaps, instrument;
			var count, ids;
			//calculate needed ids for voices * overlap
			count  = poly * overlaps;

			//get ids from scmapper's newID method
			ids = newID.value(group, count, instrument);

			//return a Pseq with values clumped for overlap (iteration technique in TD) - iterativeOverlap
			Pseq(ids.clump(poly), inf);
		};


		newProxyGroup = {
			arg group, listen, targetGroup, fadeOut = 2, fadeIn = 0;

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
			newIDOverlap: {arg self, group, poly = 2, overlaps = 2, instrument; newIDOverlap.value(group, poly, overlaps, instrument)},
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