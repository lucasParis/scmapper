SCMVisualGroup{
	var <name;
	//? var <controls;
	//? var midiMappings;// = [nil,nil,nil];

	//menuFocusers
	var < allControlsDataStructure;
	var < menuControlsDataStructure;

	//matrix routing stuff //looked at by matrix
	var < matrixOutputs;
	var < matrixInputs;
	var < matrixBusses;


	createInput{

	}

	createOutput{

	}

	// *new{
	// 	arg groupName, quant = 4;
	// 	^super.new.init(groupName, quant);
	// }

}


SCMGroup {
	var <name;

	var <controls;

	var <patterns;
	var <>proxies;

	var <> serverGroup;

	var < isPlaying;

	var <> fxSCMProxy;

	var < channels;

	var < assignedIDs;

	var midiMappings;// = [nil,nil,nil];

	var quant;

	var sharedSignals;
	var < activePresets;

	// var < scmGroupIndex;
	var < filePresetNames;



	//menuFocusers
	var < allControlsDataStructure;
	var < menuControlsDataStructure;

	//matrix routing stuff //looked at by matrix
	var < matrixOutputs;
	var < matrixInputs;
	var < matrixBusses;

	//for preset morph
	// var < presetSlotsDict;
	var < allPresets;

	//dict of busses from reply
	var replyBusses;


	createInputkr{
		arg name;
		var value;
		matrixInputs = matrixInputs.add(name);
		matrixBusses[name] = Bus.control(Server.local);

		value = In.kr(matrixBusses[name]) * this.getCtrl(\matrixIns).asSignal;
		^value;
	}

	createOutputkr{
		arg name, sig;
		var value;
		matrixOutputs = matrixOutputs.add(name);
		matrixBusses[name] = Bus.control(Server.local);
		// matrixBusses[name].postln;

		value = Out.kr(matrixBusses[name], sig * this.getCtrl(\matrixOuts).asSignal);
		^value;
	}

	createInputar{
		arg name;
		var channels = 2, value;

		matrixInputs = matrixInputs.add(name);
		// channels = 4;

		matrixBusses[name] = Bus.audio(Server.local, channels);

		value = InFeedback.ar(matrixBusses[name],channels) * this.getCtrl(\matrixIns).asSignal;
		^value;
	}

	createOutputar{
		arg name, sig;
		var channels = 2, value;

		matrixOutputs = matrixOutputs.add(name);
		//check signal size
		// channels = 4;
		matrixBusses[name] = Bus.audio(Server.local, channels);

		value = OffsetOut.ar(matrixBusses[name], sig * this.getCtrl(\matrixOuts).asSignal);
		^value;

	}



	*new{
		arg groupName, channels = 2, quant = 4;
		^super.new.init(groupName, channels, quant);
	}

	init{
		arg groupName, channels_, quant_;
		name = groupName;

		//for preset morph
		// presetSlotsDict = \empty!4;
		allPresets = [];


		//setup array to hold controls, arrays and proxies
		controls = [];
		patterns = [];
		proxies = [];

		sharedSignals = ();

		isPlaying = false;

		//setupOscAddresses
		// oscAddrPrefix = ("/" ++ name).asSymbol;
		// oscAddrMenu = (oscAddrPrefix ++ "/menu").asSymbol;

		//setup OSC mappings
		// this.setupOscListeners();

		//send default values
		// this.updateMenuFeedback('/play/x', 0);

		//setup group
		serverGroup = Group.new(SCM.masterServerGroup, 'addBefore');

		channels = channels_;
		quant = quant_;

		midiMappings = ();

		activePresets = 0!5;

		// scmGroupIndex = scmGroupIndex_;
		this.initPresetNames;

		//control/data structure menu
		menuControlsDataStructure = SCMControlDataStructure();
		allControlsDataStructure = SCMControlDataStructure();

		//matrix routing stuff //looked at by matrix
		matrixOutputs = [];
		matrixInputs = [];
		matrixBusses = ();


		replyBusses = ();



		this.newCtrl(\matrixIns, 1);
		this.newCtrl(\matrixOuts, 1);

		this.newCtrl(\play, 0).functionSet_{
			arg val;
			if(val > 0.5)
			{

				this.play;
			}
			{
				this.stop;
			};

			SCM.updatePlayStates;
		};

		this.loadAllPresets;

		4.do{
			arg i;
			var presetInit;
			presetInit = i+1;
			presetInit = presetInit.min(allPresets.size-1);
			// if(presetInit > allPresets.size-1/)
			// {
			//
			// }
			this.newCtrl(("presetMenu" ++ i).asSymbol, presetInit,"/selection");
		};


	}

	// setPresetSlot{
	// arg slotNumber, presetIndex;
	// presetSlotsDict[slotNumber] = presetIndex;
	// allPresets = [];

	// }

	loadAllPresets{
		var presetFolder;
		presetFolder = PathName.new(SCM.presetFolder);
		allPresets = presetFolder.entries.select{arg entry; entry.fileName.contains(name.asString)};

		allPresets = allPresets.collect{
			arg filePath;
			var file, path, dict, returnDict;

			returnDict = ();
			// make path
			// path = SCM.presetFolder;
			// path = path ++"/"++ name.asString ++"_"++ presetNumber;//append group/preset to get file name
			// path.postln;

			File.exists(filePath.fullPath).if{
				file = File.open(filePath.fullPath, "r");

				//read string and execute it to get the savec dict
				dict = file.readAllString.compile.value;

				//loop through dict and load values
				returnDict[\name] = dict[\presetName];

				dict.removeAt(\presetName);
				returnDict[\values] = dict;

				/*dict.keysValuesDo{
				arg key, value;
				var ctrl;
				ctrl = this.getCtrl(key.asSymbol);

				if(ctrl != nil)
				{
				ctrl.setPrepValue(value);
				};

				};*/
				returnDict;
			};
		};


		allPresets = [(name:\empty, \values:())] ++ 	allPresets;
		allPresets;

	}

	savePreset{
		arg presetName;
		var dir, file, saveDict;
		dir = SCM.presetFolder ++ "/" ++ name ++ "_" ++ presetName;//hardcoded
		// dir = dir ++ name.asString;

		//create writable file
		file = File.new(dir,"w");

		//datastructure to fill with save values
		saveDict = ();

		//get values for preset from ctrls in group's datastructure
		controls.do
		{
			arg ctrl;

			saveDict[(ctrl.name ++ ctrl.postFix).asSymbol] = ctrl.value;//add key/value from ctrls to dict
		};
		saveDict[\presetName] = presetName;
		//write preset dict to file
		file.write(saveDict.asCompileString);
		file.close;

	}

	/*savePresetToFile{
	arg presetNumber = 0;
	if(SCM.enablePresetSave)
	{
	{//defered to other thread
	var win;
	//create popup UI for save dialog
	win = Window.new("save", Rect(Window.availableBounds.center.x-100,Window.availableBounds.center.y , 200,50)).front;
	//textfield with callback
	TextField.new(win,Rect(10,10,180,30)).action_( {
	arg obj;
	{
	var file, dir, saveDict, presetName;

	presetName = obj.value.asString;
	filePresetNames[presetNumber] = presetName;
	//preset files on HD
	dir = SCM.presetFolder ++ "/";//hardcoded
	dir = dir ++ name.asString++"_"++ presetNumber.asString;//new file name according to group and entered name

	//create writable file
	file = File.new(dir,"w");

	//datastructure to fill with save values
	saveDict = ();

	//get values for preset from ctrls in group's datastructure
	controls.do
	{
	arg ctrl;

	saveDict[ctrl.name] = ctrl.value;//add key/value from ctrls to dict
	};
	saveDict[\presetName] = presetName;
	//write preset dict to file
	file.write(saveDict.asCompileString);
	file.close;

	}.defer;//defered to other thread

	//close window
	{win.close}.defer;

	});
	}.defer;

	};
	//reload names
	// this.initPresetNames;

	// SCM.presetFolder
	}*/

	loadPresetFromFile{
		// SCM.presetFolder
		arg presetNumber;
		var file, path, dict;

		//make path
		path = SCM.presetFolder;
		path = path ++"/"++ name.asString ++"_"++ presetNumber;//append group/preset to get file name

		File.exists(path).if{
			file = File.open(path, "r");

			//read string and execute it to get the savec dict
			dict = file.readAllString.compile.value;

			//loop through dict and load values
			dict[\presetName];
			dict.removeAt(\presetName);

			dict.keysValuesDo{
				arg key, value;
				var ctrl;
				ctrl = this.getCtrl(key.asSymbol);

				if(ctrl != nil)
				{
					ctrl.setPrepValue(value);
				};

			};
		};
	}

	initPresetNames{
		filePresetNames = 5.collect{
			arg i;
			var file, path, dict, result;
			result = '_';

			//make path
			path = SCM.presetFolder;
			path = path ++"/"++ name.asString ++"_"++ i.asString;//append group/preset to get file name

			File.exists(path).if{
				file = File.open(path, "r");

				//read string and execute it to get the savec dict
				dict = file.readAllString.compile.value;

				//loop through dict and load values
				result = dict[\presetName];
			};
			result;
		};
	}

	updatePresetStatus{
		arg activatedPreset, status = 1;
		activePresets[activatedPreset] = status;

	}

	getPresetStatus{
		^activePresets;
	}

	shareSignal{
		arg signal, name;
		var bus;
		bus = Bus.audio(Server.local, signal.size);
		sharedSignals[name] = bus;
		OffsetOut.ar(bus,signal);
	}

	getSignal{
		arg name;
		var result;
		result = nil;
		if(sharedSignals[name] != nil)
		{
			result = In.ar(sharedSignals[name],sharedSignals[name].numChannels);
		};
		^result;
	}

	newCtrl{
		arg ctrlName, defaultValue = 0, postFix = "/x"; // subGroup = \pattern1 / nil
		var ctrl;
		var menuControls;

		//new ctrl
		ctrl = SCMMetaCtrl.new(ctrlName, defaultValue, postFix);
		// add control to this group
		controls = controls.add(ctrl);



		//if this is a menu control add it to that datastructure
		menuControls = [\volume, \play, \ledsOn, \matrixOuts, \matrixIns, \presetMenu0, \presetMenu1, \presetMenu2, \presetMenu3];
		if(menuControls.includes(ctrlName) == true)
		{
			menuControlsDataStructure.addControl(ctrl);
		}
		{
			allControlsDataStructure.addControl(ctrl);
		};


		^ctrl;//return
	}

	getCtrl{
		arg name, postFix = "/x";
		var result;
		//loop through controls and find the one with this name
		result = controls.select{ arg control; (control.name == name.asSymbol) && (control.postFix == postFix.asSymbol); };
		if(result.size > 0){result = result[0]} {result = nil};
		^result;
	}

	//add a pattern to this group
	linkPattern{
		arg patternName, pattern, manualMode = false, independentPlay = false, trigBus = false, manualGrouping =1, splitMixing = false, manualMuteLast = false;
		var pat;
		//new pattern
		pat = SCMPattern.new(patternName, pattern, this, channels, manualMode, independentPlay, trigBus, manualGrouping, splitMixing, manualMuteLast);
		// add pattern to this group
		patterns = patterns.add(pat);
		^pat;//return
	}

	//add a proxy to this group
	linkProxy{
		arg proxyName, function, audioIn = nil;
		var proxy;
		//new proxy
		proxy = SCMProxy.new(proxyName, function, this, audioIn,  channels: channels);
		// proxy.stop;
		//add proxy to this group
		proxies = proxies.add(proxy);
		^proxy;//return
	}

	groupFX{
		arg function = {arg in; in;};
		var proxy, proxyName, input;
		proxyName = (name ++ "groupFX").asSymbol;

		//new proxy with audio input
		input = {
			(patterns.reject(_.hasFX).collect(_.getOutput()).sum) + (proxies.collect(_.getOutput()).sum)

		};
		proxy = SCMProxy.new(proxyName, function, this, input, channels);
		// proxy.stop;

		//add SCMProxy after every generator of this group in server hierachy
		proxy.serverGroup = Group.new(serverGroup, 'addToTail');

		//add proxy to this group
		proxies = proxies.add(proxy);
		fxSCMProxy = proxies.last();
		^proxy;//return
	}

	getOutput{
		// channels.postln;
		^fxSCMProxy.getOutput;
	}

	newIDOverlap{
		arg  poly, overlaps, instrument;
		var count, ids;
		//calculate needed ids for voices * overlap
		count  = poly * overlaps;

		//get ids from scmapper's newID method
		ids = this.newID(count, instrument);

		//return a Pseq with values clumped for overlap (iteration technique in TD) - iterativeOverlap
		^Pseq(ids.clump(poly), inf);
	}

	getIDBus
	{
		arg idName, parameterName, countIndex = 0;
		^replyBusses[idName.asSymbol][parameterName.asSymbol][countIndex];
	}

	newID{
		arg count, instrument, idName = \none;
		var assignedID;

		assignedID = [];

		//count is the number of IDs asked for and set in data structure
		count.do{
			SCM.replyIDCount = SCM.replyIDCount+1;//increment ID counter/allocator
			assignedIDs = assignedIDs.add(SCM.replyIDCount);// append to array of group's ids

			assignedID = assignedID.add(SCM.replyIDCount);// add to local array
		};

		if(idName != \none)
		{
			replyBusses[idName.asSymbol] = ();
		};

		//if synthdef is setup to reroute OSC replies
		SynthDescLib.global[instrument].metadata.includesKey(\oscReplies).if
		{
			// loop through the synthdef's osc reply addresses
			SynthDescLib.global[instrument].metadata[\oscReplies].do
			{
				arg addr;
				var signalSize;

				if(idName != \none)
				{
					signalSize = SynthDescLib.global[instrument].metadata['oscRepliesWithSize'][addr.asSymbol];

					replyBusses[idName.asSymbol][addr.split($/).last.asSymbol] = ();
					//bus stored as [givenname][parameterName][countnumber]
					assignedID.do{
						arg id, i;

						replyBusses[idName.asSymbol][addr.split($/).last.asSymbol][i] = Bus.control(Server.local, signalSize);
					};
				};




				//OSC callback for the replies (rerouting to touch), based on replyids stored in database
				OSCdef(
					(name ++ addr).asSymbol, //osccallback name with group
					{
						arg msg;
						var values, replyID, idIndex, addrOut;
						replyID = msg[2];//get the replyID in the osc message
						idIndex = assignedIDs.find([replyID]);//search for the replyID in the database
						(idIndex != nil).if // if replyID is indexed in database
						{
							//get signal value(s) from reply
							values = msg[3..];
							//rename & prepare addr to touch
							addrOut = addr.replace("/" ++ instrument.asString, "");//remove instrument from address
							addrOut= "/" ++ name.asString ++ "/" ++ instrument.asString ++ "/" ++ idIndex.asString ++ addrOut;// format address with group/instrmnt/indx/par

							//write values to bus
							if(idName != \none)
							{
								var index;
								index = assignedID.find([replyID]);//search for the replyID in the localdatabase
								if(index != nil)
								{

									replyBusses[idName.asSymbol][addr.split($/).last.asSymbol][index].set(*values);
								};
							};

							//send values
							// touchdesignerCHOP.sendMsg(addrOut, *values);

							SCM.dataOutputs.do{
								arg tdOut;
								tdOut.chop.sendMsg(addrOut, *values);
							};
						}
					},
					addr;
				);
			};
		};


		^assignedID;//return assigned id
	}

	printOn { | stream |
		stream << "SCMGroup (" << name << ")";
	}


	play{
		if(isPlaying.not)
		{
			isPlaying = true;
			//play patterns
			patterns.do{arg pattern; pattern.play; };
			//play proxies
			proxies.do{arg proxy; proxy.play; };
			//play controls (busMappers)
			controls.do{arg control; control.play; };
		}

	}

	stop{
		if(isPlaying)
		{
			isPlaying = false;
			//stop patterns
			patterns.do{arg pattern; pattern.stop; };
			//stop proxies
			proxies.do{arg proxy; proxy.stop; };
			//stop controls (busMappers)
			controls.do{arg control; control.stop; };

			// if(scmGroupIndex != nil)
			// {
			// SCM.setGroupPlayStates(scmGroupIndex, 0);
			// };
		}
	}

	midiIn{
		arg midiNumber, value;
		if(midiMappings[midiNumber] != nil)
		{
			this.getCtrl(midiMappings[midiNumber][\ctrlname], midiMappings[midiNumber][\postfix]).set(value);
		};
	}

	setupMidiListener{
		arg ctrlname, midiNumber, postfix = '/x';

		midiMappings[midiNumber] = (\ctrlname:ctrlname, \postfix:postfix);

	}

	/*updateMenuFeedback{
	arg menuPath, value, quantize = false;
	var path;
	path = (oscAddrMenu ++ menuPath).asSymbol;
	//update osc outputs
	SCM.ctrlrs.do{
	arg ctrlr;
	ctrlr.sendMsg(path, value);//for midi if a param is mapped, store relation path->encoder/button
	};
	//update touchdesigner outputs
	SCM.dataOutputs.do{
	arg tdOut;
	if(quantize == true,
	{
	SCM.proxySpace.clock.playNextBar({ tdOut.chop.sendMsg(("/menu" ++ path).asSymbol, *value) })//call reset on next bar
	},
	{
	tdOut.chop.sendMsg(("/menu" ++ path).asSymbol, *value);//append /controls
	}
	);
	};

	}*/

	sendTrigger{
		arg nameSig, signal;
		var address;

		//prepare osc output address
		address = "/trigger/" + name.asString++ '/' ++ nameSig;
		address = address.replace(" ", "");//remove empty spaces

		// osc listener for sendReply
		OSCdef(
			address.asSymbol,
			{
				arg msg;
				var values;

				// values = msg[3..];//get the signal values
				values = [nameSig.asString, 1];//get the signal values
				//send to touch, with sync delay
				SCM.dataOutputs.do{
					arg tdOut;
					{tdOut.dat.sendMsg(address, *values)}.defer(SCM.visualLatency);
				}

		}, address);//oscdef addr for signal reply

		//create sendreply
		SendReply.ar(signal, address, 1);

	}

	sendSignal{
		arg nameSig, signal;

		var address;

		//prepare osc output address
		address = "/" + name.asString++ '/' ++ "proxy" ++ '/' ++ nameSig;
		address = address.replace(" ", "");//remove empty spaces

		// osc listener for sendReply
		OSCdef(
			address.asSymbol,
			{
				arg msg;
				var values;
				values = msg[3..];//get the signal values
				//send to touch, with sync delay
				SCM.dataOutputs.do{
					arg tdOut;
					{tdOut.chop.sendMsg(address, *values)}.defer(SCM.visualLatency);
				}

		}, address);//oscdef addr for signal reply

		//create sendreply
		SendReply.kr(Impulse.kr(SCM.dataOutRate), address, signal, -1);

	}

	listen{
		this.fxSCMProxy.outputBus.play;
	}
}