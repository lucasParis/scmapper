
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
		var pMap, bMap, pgroups, newPGroup, currentGroup, collectToOsc, touchdesignerDAT, ctrlrs, normalColor, shiftJumpColor, newID, replyIDCount, touchdesignerCHOP, linkEncoder, linkButton, sendBrowserNames, visualSync, allMap, proxySpace, newProxyGroup, newGroup, setupPreset, sendSignal, chainProxyFX, pbus, setTempo, newIDOverlap;

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
		ctrlrs = ctrlrs.add(NetAddr("127.0.0.1", 8000));//ipad 1
		ctrlrs = ctrlrs.add(NetAddr("127.0.0.1", 7000));//ipad 2

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

			//pattern specific stuff

			//setup pattern with common bus, and groups
			pattern = Pbindf(pattern,
				\out, pgroups[pGroupName][\bus],//the bus for all pbind stuff
				\group, pgroups[pGroupName][\pbindGroup],//group for pbinds
				\fx_group, pgroups[pGroupName][\pbindGroup],//group for proxy after pbinds
			);

			//store the pattern for future use
			pgroups[pGroupName][\pattern] = pattern;

			// pgroups[pGroupName][\pattern] = Pbindf(pattern, \rest: Pfunc{});//insert pause and mute use mapping for it

			//pattern player to store pattern once it's been started
			pgroups[pGroupName][\patternPlayer] = nil;

			//reset button callback
			OSCdef(('/' ++ pGroupName ++ '/pctrl/reset/x').asSymbol,
				{
					arg msg, time, addr;
					if(msg[1] > 0.5, {//on button down
						proxySpace.clock.playNextBar({pgroups[pGroupName][\patternPlayer].reset})//call reset on next bar
					});
			} , ('/' ++ pGroupName ++ '/pctrl/reset/x').asSymbol);//reset button OSCaddr

			//play/stop button callback
			OSCdef(('/' ++ pGroupName ++ '/pctrl/play/x').asSymbol,
				{
					arg msg, time, addr;
					(msg[1] > 0.5).if{//on button down
						//call play on pattern, with OSC event collect for touch, and store into group database
						pgroups[pGroupName][\patternPlayer] = pgroups[pGroupName][\pattern].collect({arg evt; collectToOsc.value(evt, pGroupName )}).play(clock: proxySpace.clock, quant:quant, doReset:true);
						//if this group should be output to soundcard
						pgroups[pGroupName][\pbindListen].if{
							//play the bus then and store busPlayer into a variable
							pgroups[pGroupName][\busPlayer] = pgroups[pGroupName][\bus].play;
						};

						// forward to touchdesigner
						touchdesignerCHOP.sendMsg('/' ++ pGroupName ++ '/pctrl/play/x', 1);
					}{
						//else if stopping
						pgroups[pGroupName][\patternPlayer].stop;//stop pattern
						(pgroups[pGroupName][\busPlayer] != nil).if{//stop bus (wait for the end of the sound, add a delay?)
							pgroups[pGroupName][\busPlayer].free;
						};
						//forward to touchdesigner, quantized to next bar (next beat?)
						proxySpace.clock.playNextBar({touchdesignerCHOP.sendMsg('/' ++ pGroupName ++ '/pctrl/play/x', 0); });
					};

					//update other ctrlrs
					ctrlrs.do{
						arg ctrlr;
						ctrlr.sendMsg('/' ++ pGroupName ++ '/pctrl/play/x', msg[1] );
					};
			} , ('/' ++ pGroupName ++ '/pctrl/play/x').asSymbol);//OSC addr of play button

			//send initial value for play button
			ctrlrs.do{
				arg ctrlr;
				ctrlr.sendMsg('/' ++ pGroupName ++ '/pctrl/play/x', 0);
			};

		};

		//link and setup preset name to load preset button
		setupPreset = {
			arg pGroupName, presetName, index;
			var addrPreset;

			//prepare osc address of this button
			addrPreset =  ('/' ++ pGroupName ++ '/pctrl/preset'++(index+1).asString).asSymbol;

			//check if group/preset/index not empty and setup
			(pgroups[pGroupName] == nil).if{pgroups[pGroupName] = ()};// create dict if doesn't exist'
			(pgroups[pGroupName][\presets] == nil).if{pgroups[pGroupName][\presets] = (\name:\empty, values:())! 4 };// create dict of preset name + ctrlr values
			pgroups[pGroupName][\presets][index] = (\name:presetName, values:());//setup this index

			//send name of the preset to ctrlr buttons
			ctrlrs.do{
				arg ctrlr;
				ctrlr.sendMsg(addrPreset,'@label_off', *presetName);
				ctrlr.sendMsg(addrPreset,'@label_on', *presetName);
			};
		};

		//link a NodeProxy to a group's pattern
		chainProxyFX = {
			arg group, graph;
			//set flag to not listen to pbind bus (on play/stop), the Nodeproxy is supposed to route the audio from the bus (automatically add audio in to argument?)
			pgroups[group][\pbindListen] =  false;

			//set NodeProxy in proxyspace
			proxySpace[group] = graph;

			//apply setup and mappings like a proxygroup
			newProxyGroup.value(group, true, pgroups[group][\fxGroup]);
		};

		//get the pattern bus for this group
		pbus = {
			arg group;
			pgroups[group][\bus];//return saved bus
		};

		//common group creation, for proxy and pbind
		//- setup of datastructure
		// OSC listeners:
		//- save
		//- load
		//- twister mapping
		//- jump
		//- shift
		newGroup = {
			arg pGroupName;
			var addrSave;//osc address of save preset button

			//double check that is a symbol, better then string for identity comparison
			pGroupName = pGroupName.asSymbol;

			// create dict if doesn't exist for this group
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
			//group datastructure has a key \encoderMappings which holds a array of names that are mapped the ctrlrs assigned to the knobs
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
				OSCdef(mapAddr.asSymbol,
					{
						arg msg, time, addr;

						//make sure group name is symbol
						pGroupName = pGroupName.asSymbol;

						//check if encoder mapping exists
						(pgroups[pGroupName][\encoderMappings] != nil).if{
							pgroups[pGroupName][\encoderMappings].do{//loop through each 4 knobs and look for mapped value
								arg ctrlName, i;
								var knobIndex, hues, hue, ctrlType;

								//calculate knobindex according to map button and current iteration
								knobIndex = (mapNumber*4) + i;

								//it it was linked
								(ctrlName != nil).if{
									(msg[1] > 0.5).if{// on button down

										//make sure everything is a symbol
										ctrlName = ctrlName.asSymbol;
										pGroupName = pGroupName.asSymbol;

										//get color for this row
										hues = ~twisterHues;
										hue = hues[mapNumber];//each map button has it's own color

										// send color (already sent on ctrlr initialisation)
										m.control(chan:1,ctlNum:knobIndex,val:hue);
										//send button state
										m.control(chan:2,ctlNum:knobIndex,val:~twister[knobIndex][\button].linlin(0,1,27,47));

										//set twister feedback and data structure to the mapped ctrlr
										~twister[knobIndex][\set].value(pgroups[pGroupName][\ctrls][ctrlName][\value]);
										//save this mapping in the group datastructure, for unmapping, osc in, preset, etc... (used as condition to update twister on changing parameter)
										pgroups[pGroupName.asSymbol][\ctrls][ctrlName.asSymbol][\twisterKnob] = knobIndex;

										//set in twister's datastructure the function that will be called when midi comes in
										~twister[knobIndex].knobCallback = {
											arg twisterValue;

											//set value (for pbind)
											pgroups[pGroupName][\ctrls][ctrlName][\value] = twisterValue;

											//set bus value
											(pgroups[pGroupName][\ctrls][ctrlName][\type] == \bus).if{
												pgroups[pGroupName][\ctrls][ctrlName][\bus].set(*(twisterValue.linlin(0,1,pgroups[pGroupName][\ctrls][ctrlName][\minMap],pgroups[pGroupName][\ctrls][ctrlName][\maxMap])));
											};

											//set proxy value
											(pgroups[pGroupName][\ctrls][ctrlName][\type] == \nodeproxy).if{
												proxySpace[pGroupName].set(ctrlName, *(pgroups[pGroupName][\ctrls][ctrlName][\value]));
											};

											//if other controllers are not in jump shift mode, send values to them
											(pgroups[pGroupName][\jumpShift] <0.5).if
											{
												//send to lemurs
												ctrlrs.do{
													arg ctrlr;
													ctrlr.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\addr], pgroups[pGroupName][\ctrls][ctrlName][\value]);
												};
												//send to touch chop
												touchdesignerCHOP.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\touchAddr], *pgroups[pGroupName][\ctrls][ctrlName][\value]);

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
						(pgroups[pGroupName][\buttonMappings] != nil).if{//check if encoder mapping exists
							pgroups[pGroupName][\buttonMappings].do{//loop through and look for mapped value
								arg ctrlName, i;
								var knobIndex, hues, hue;

								knobIndex = (mapNumber*4) + i;

								//if it was linked
								(ctrlName != nil).if{
									(msg[1] > 0.5).if{//on button down
										ctrlName = ctrlName.asSymbol;
										pGroupName = pGroupName.asSymbol;

										//get color for this row
										hues = ~twisterHues;
										hue = hues[mapNumber];//each map button has it's own color

										// send color (already sent on ctrlr initialisation)
										m.control(chan:1,ctlNum:knobIndex,val:hue);
										//send button state
										m.control(chan:2,ctlNum:knobIndex,val:~twister[knobIndex][\button].linlin(0,1,27,47));

										//set twister feedback and data structure to the mapped ctrlr
										~twister[knobIndex][\setButton].value(pgroups[pGroupName][\ctrls][ctrlName][\value]);

										//set button mode on twister, from the argument when button was mapped
										~twister[knobIndex][\toggle] = pgroups[pGroupName][\buttonMappingsToggle][i];

										//save this mapping in the group datastructure, for unmapping, osc in, preset, etc... (used as condition to update twister on changing parameter)
										pgroups[pGroupName][\ctrls][ctrlName][\twisterButton] = knobIndex;

										//set in twister's datastructure the function that will be called when midi comes in
										~twister[knobIndex].buttonCallback = {
											arg twisterValue;

											//set value (for pbind)
											pgroups[pGroupName][\ctrls][ctrlName][\value] = twisterValue;

											//set bus value
											(pgroups[pGroupName][\ctrls][ctrlName][\type] == \bus).if{
												pgroups[pGroupName][\ctrls][ctrlName][\bus].set(*(twisterValue.linlin(0,1,pgroups[pGroupName][\ctrls][ctrlName][\minMap],pgroups[pGroupName][\ctrls][ctrlName][\maxMap])));
											};

											//send to lemurs
											ctrlrs.do{
												arg ctrlr;
												ctrlr.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\addr], pgroups[pGroupName][\ctrls][ctrlName][\value]);
											};

											//send to touch chop
											touchdesignerCHOP.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\touchAddr], pgroups[pGroupName][\ctrls][ctrlName][\value]);

										};
									}{//unmap
										//remove callback
										~twister[knobIndex].buttonCallback = nil;

										//disable the lights on this knob
										m.control(chan:2,ctlNum:knobIndex,val:18);

										//remove this mapping in the group datastructure, for unmapping, osc in, preset, etc... (used as condition to update twister on changing parameter)
										pgroups[pGroupName][\ctrls][ctrlName][\twisterButton] = nil;
									};
								};
							};
						};
				} , mapAddr);//OSCdef addr of load button
			};//end of loop for 4 map buttons


			//listener for jump button
			//sets all controls to their prepared jump states
			OSCdef(('/' ++ pGroupName ++ '/jump').asSymbol,
				{
					arg msg, time, addr;
					(msg[1] > 0.5).if{//on button down
						//make sure some ctrls exist (ie: are mapped)
						(pgroups[pGroupName][\ctrls] != nil).if
						{
							//loop through controls
							pgroups[pGroupName][\ctrls].keysValuesDo{
								arg ctrlname , ctrldict;

								//set value  (for pbind)
								ctrldict['value'] = ctrldict['shiftValue'];

								//set bus value
								(ctrldict[\type] == \bus).if
								{
									ctrldict[\bus].set(*(ctrldict['value'].linlin(0,1,ctrldict[\minMap],ctrldict[\maxMap])));
								};

								//set proxy value
								(ctrldict[\type] == \nodeproxy).if{
									proxySpace[pGroupName].set(ctrlname, *(ctrldict[\value]));
								};

								//send values to lemurs
								ctrlrs.do{
									arg ctrlr;
									ctrlr.sendMsg(ctrldict['addr'], *ctrldict['value']);
								};

								//if mapped to twister knob send value
								(ctrldict[\twisterKnob] != nil).if{
									~twister[ctrldict[\twisterKnob]][\set].value(ctrldict[\value]);
								};
								//if mapped to twister button send value
								(ctrldict[\twisterButton] != nil).if{
									~twister[ctrldict[\twisterButton]][\setButton].value(ctrldict[\value]);
								};

								//send to touch chop
								touchdesignerCHOP.sendMsg(ctrldict[\touchAddr], ctrldict[\value]);
							};
						};
					};
				}
				, '/jump/x'.asSymbol);// OSCdef addr of jump button

			//listener for jump shift button
			OSCdef(('/' ++ pGroupName ++ '/jumpShift').asSymbol,
				{
					arg msg, time, addr;

					//update shift to other controllers
					ctrlrs.do{
						arg ctrlr;
						(ctrlr != addr).if{
							ctrlr.sendMsg('/jumpShift/x', msg[1]);
						};
					};

					(msg[1] > 0.5).if{//on button down - Set controls to their shift state and to orange

						//set the shift variable in data structure
						pgroups[pGroupName][\jumpShift]  = 1;

						//make sure there are exiting controls
						(pgroups[pGroupName][\ctrls] != nil).if{
							//loop through controls
							pgroups[pGroupName][\ctrls].keysValuesDo{
								arg ctrlname , ctrldict;
								//send the shift values to lemurs
								ctrlrs.do{
									arg ctrlr;
									ctrlr.sendMsg(ctrldict['addr'], *ctrldict['shiftValue']);
									ctrlr.sendMsg(ctrldict['addr'].asString.replace("/x", ""),'@color', shiftJumpColor);//send color orange
								};
							};
						};
					}{ //on button up - Set controls to their standard state and back to white

						//set the shift variable in data structure
						pgroups[pGroupName][\jumpShift]  = 0;

						//make sure there are exiting controls
						(pgroups[pGroupName][\ctrls] != nil).if
						{
							//loop through controls
							pgroups[pGroupName][\ctrls].keysValuesDo{
								arg ctrlname , ctrldict;
								//send the standard values to lemurs
								ctrlrs.do{
									arg ctrlr;
									ctrlr.sendMsg(ctrldict['addr'], *ctrldict['value']);
									ctrlr.sendMsg(ctrldict['addr'].asString.replace("/x", ""),'@color', normalColor);//send color white
								};
							};
						};
					};
			} , '/jumpShift/x'.asSymbol);//oscdef addr for jumpshift
		};

		//link an encoder to a group and parameter
		linkEncoder = {
			arg pGroupName, ctrlName, encoder;

			//setup mapping array if doesn't exist
			(pgroups[pGroupName][\encoderMappings] == nil).if{pgroups[pGroupName][\encoderMappings] = nil!4;};
			//replace nil with ctrlName in array
			pgroups[pGroupName][\encoderMappings][encoder] = ctrlName;
		};

		//link a button to a group and parameter
		linkButton = {
			arg pGroupName, ctrlName, button, toggle;

			//setup mapping array if doesn't exist
			(pgroups[pGroupName][\buttonMappings] == nil).if{pgroups[pGroupName][\buttonMappings] = nil!4;};
			//replace nil with ctrlName in array
			pgroups[pGroupName][\buttonMappings][button] = ctrlName;

			//setup toggle values array if doesn't exist
			(pgroups[pGroupName][\buttonMappingsToggle] == nil).if{pgroups[pGroupName][\buttonMappingsToggle] = nil!4;};
			//replace nil with toggle value in array
			pgroups[pGroupName][\buttonMappingsToggle][button] = toggle;
		};

		//generic mapping things, called by all of the 3 mapping types (pfunc, proxy and bus)
		//defines OSC listener for value
		//send default values
		allMap = {
			arg pGroupName, ctrlName, defaultValue,minMap, maxMap, type = \bus, function = nil, postfix;
			var return;

			//prepare group's database if inexistant
			(pgroups[pGroupName] == nil).if{pgroups[pGroupName] = ()};
			(pgroups[pGroupName][\ctrls] == nil).if{pgroups[pGroupName][\ctrls] = ()};// create ctrlrs dict if doesn't exist

			// ctrlName = (ctrlName + postfix).asSymbol;

			//init the control's datastructure and store settings
			pgroups[pGroupName][\ctrls][ctrlName] = ();//empty dict
			pgroups[pGroupName][\ctrls][ctrlName][\type] = type;
			pgroups[pGroupName][\ctrls][ctrlName][\value] = defaultValue;
			pgroups[pGroupName][\ctrls][ctrlName][\presets] = defaultValue!4;//values of each internal preset
			pgroups[pGroupName][\ctrls][ctrlName][\shiftValue] = defaultValue;
			pgroups[pGroupName][\ctrls][ctrlName][\addr] = ('/'++ pGroupName ++ '/'++ ctrlName ++ postfix).asSymbol;
			pgroups[pGroupName][\ctrls][ctrlName][\touchAddr] = ('/controls/'++ pGroupName ++ '/'++ ctrlName ++ postfix).asSymbol;

			//specific settings and setup to type 'bus'
			(type == \bus).if{
				pgroups[pGroupName][\ctrls][ctrlName][\minMap] = minMap;
				pgroups[pGroupName][\ctrls][ctrlName][\maxMap] = maxMap;
				pgroups[pGroupName][\ctrls][ctrlName][\bus] = Bus.control(s,defaultValue.size.max(1));//create bus of minimum 1 channel
				pgroups[pGroupName][\ctrls][ctrlName][\bus].set(defaultValue.linlin(0,1,minMap,maxMap));//set default value, can it be array?
			};

			//specific settings and setup to type 'nodeproxy'
			(type == \nodeproxy).if{
				pgroups[pGroupName][\ctrls][ctrlName][\minMap] = minMap;
				pgroups[pGroupName][\ctrls][ctrlName][\maxMap] = maxMap;
			};

			// OSC listener for osc value
			// - check if in shift mode
			// - sets value
			OSCdef(pgroups[pGroupName][\ctrls][ctrlName][\addr],
				{
					arg msg, time, addr;
					var value;

					//get value(s)
					value = msg[1..];
					(value.size == 1).if{value = value[0]};//if it's an array of 1 element convert from array to single value

					(pgroups[pGroupName][\jumpShift]>0.5).if{
						//if in shift mode
						//set shift value
						pgroups[pGroupName][\ctrls][ctrlName][\shiftValue] = value;
					}{
						// standard setting mode

						//set value  (for pbind)
						pgroups[pGroupName][\ctrls][ctrlName][\value] = value;

						//set bus value
						(type == \bus).if{
							pgroups[pGroupName][\ctrls][ctrlName][\bus].set(*(value.linlin(0,1,minMap,maxMap)));
						};

						//set proxy value
						(type == \nodeproxy).if{
							// value.postln;
							// proxySpace[pGroupName].set(ctrlName, value.linlin(0,1,minMap,maxMap));
							proxySpace[pGroupName].set(ctrlName, *value);
							//NodeProxy
							// pgroups[pGroupName][\ctrls][ctrlName][\bus] = Bus.control(s,defaultValue.size.max(1));
							// pgroups[pGroupName][\ctrls][ctrlName][\bus].set(defaultValue.linlin(0,1,minMap,maxMap));
						};

						//send values to lemurs
						ctrlrs.do{
							arg ctrlr;
							//make sure you're not sending to the ctrlr who sent the message (optional?)
							(ctrlr != addr).if{
								//send value
								ctrlr.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\addr], *pgroups[pGroupName][\ctrls][ctrlName][\value]);
							};

						};
						//send to touch chop controls
						touchdesignerCHOP.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\touchAddr], *pgroups[pGroupName][\ctrls][ctrlName][\value]);

						//if mapped to twister knob send value
						(pgroups[pGroupName][\ctrls][ctrlName][\twisterKnob] != nil).if{
							~twister[pgroups[pGroupName][\ctrls][ctrlName][\twisterKnob]][\set].value(pgroups[pGroupName][\ctrls][ctrlName][\value]);
						};

						//if mapped to twister button send value
						(pgroups[pGroupName][\ctrls][ctrlName][\twisterButton] != nil).if{
							~twister[pgroups[pGroupName][\ctrls][ctrlName][\twisterButton]][\setButton].value(pgroups[pGroupName][\ctrls][ctrlName][\value]);
						};
					}
			} , pgroups[pGroupName][\ctrls][ctrlName][\addr]);//osc address for controller value

			//send initial value
			ctrlrs.do{
				arg ctrlr;
				ctrlr.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\addr], *defaultValue);
			};

			//send to touch chop controls
			touchdesignerCHOP.sendMsg(pgroups[pGroupName][\ctrls][ctrlName][\touchAddr], *pgroups[pGroupName][\ctrls][ctrlName][\value]);


			//return according to types:

			//- pfunc   with ref to value in datastructure
			(type == \pfunc).if{
				return = Pfunc({pgroups[pGroupName][\ctrls][ctrlName][\value]}); // return Pfunc
			};

			//- bus   or array of busses
			(type == \bus).if{
				//if single channel
				(pgroups[pGroupName][\ctrls][ctrlName][\bus].numChannels == 1).if{
					return = pgroups[pGroupName][\ctrls][ctrlName][\bus].asMap; //return bus map
				}
				{
					//if multichannel bus mapping, return an array of sc bus map strings ["c1", "c2", ...]
					return = pgroups[pGroupName][\ctrls][ctrlName][\bus].numChannels.collect{arg i; ("c" ++ (pgroups[pGroupName][\ctrls][ctrlName][\bus].index + i).asString).asSymbol};
				};
			};

			//- proxy  map doesn't have to return anything...

			return;
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