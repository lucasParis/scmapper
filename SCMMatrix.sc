SCMMatrix {
	//variables
	var editMode;
	var oscout;
	var globalConnections;
	var modules, allConnections;
	var selectedInModule, selectedOutModule, selectedKey;
	var rndNames, moduleData;

	*new{
		// arg groupName, channels = 2, quant = 4, scmGroupIndex = nil;
		^super.new.init();
	}

	init{
		// arg groupName, channels_, quant_, scmGroupIndex_;
		editMode = \modify;

		rndNames = ["Low", "Hi", "Mid", "Dirty", "Spacy", "Synth", "Freq", "Tone", "Delay", "Crunch", "Smear", "Lfo", "Env", "Punch"];
		modules = 10.collect{arg i; ("m" ++ i).asSymbol};
		selectedInModule = \m1;
		selectedOutModule = \m2;
		//generate random names for in/outs for tests // get from SCM
		moduleData = modules.collect({arg name; [name, ()]}).flatten.asDict;
		moduleData.keysValuesChange{(\inputs:6.collect{ rndNames.choose ++rndNames.choose ++rndNames.choose } , \outputs: 6.collect{ rndNames.choose ++rndNames.choose ++rndNames.choose } )};


		selectedKey = (selectedInModule ++ "_" ++ selectedOutModule).asSymbol;

		allConnections = (modules!2).allTuples.collect{arg array; (array[0] ++ "_" ++ array[1]).asSymbol}.collect({arg name; [name, ()]}).flatten.asDict;

		this.sendInputNames();
		this.sendOutputNames();

		//init controllers
		SCM.ctrlrs.do{
			arg ctrlr;
			ctrlr.set("/matrix/routingVisualiser/inConnections", [\none]);
			ctrlr.set("/matrix/routingVisualiser/outConnections", [\none]);
			ctrlr.set("/matrix/routingVisualiser/rangesMax", [1]);
			ctrlr.set("/matrix/routingVisualiser/rangesMin", [0]);
			ctrlr.set("/matrix/routingVisualiser4/setModules", modules);
			ctrlr.set("/matrix/moduleRoutingSelect/setLabels", modules);
			ctrlr.set("/matrix/matrixEditMode/x", [0,1]);
			ctrlr.set("/matrix/outputName/value", selectedOutModule);
			ctrlr.set("/matrix/inputName/value", selectedInModule);
		};

		this.setupOscListeners();

	}


	sendGlobalConnections {
		globalConnections = allConnections.collect({arg  value, key; var returnVal; if(value.isEmpty == true){returnVal = nil}{returnVal = key}; returnVal;}).keys.asArray.collect{arg st; st.asString.split($_)};
		globalConnections = globalConnections.deepCollect(2, {arg v; modules.indexOf(v.asSymbol)});

		//update osc outputs
		SCM.ctrlrs.do{
			arg ctrlr;
			ctrlr.set("/matrix/routingVisualiser4/connections", globalConnections.flatten);
		};
	}

	sendInputNames {
		var names = moduleData[selectedInModule].inputs;
		SCM.ctrlrs.do{
			arg ctrlr;
			ctrlr.set("/matrix/dragButton/labels", names);
		};
	}

	sendOutputNames {
		var names = moduleData[selectedOutModule].outputs;
		SCM.ctrlrs.do{
			arg ctrlr;
			ctrlr.set("/matrix/outButtons/setLabels", names);
		};
	}


	sendConnections {
		var ins, outs, mins, maxs;
		ins = allConnections[selectedKey].collectAs({arg val; val[\in]},Array);
		outs = allConnections[selectedKey].collectAs({arg val; val[\out]}, Array);
		mins = allConnections[selectedKey].collectAs({arg val; val[\min]}, Array);
		maxs = allConnections[selectedKey].collectAs({arg val; val[\max]}, Array);

		SCM.ctrlrs.do{
			arg ctrlr;

			if(allConnections[selectedKey].isEmpty)
			{
				ctrlr.set("/matrix/routingVisualiser/inConnections", [\none]);
				ctrlr.set("/matrix/routingVisualiser/outConnections", [\none]);
				ctrlr.set("/matrix/routingVisualiser/rangesMax", [1]);
				ctrlr.set("/matrix/routingVisualiser/rangesMin", [0]);
			}
			{
				ctrlr.set("/matrix/routingVisualiser/inConnections", ins);
				ctrlr.set("/matrix/routingVisualiser/outConnections", outs);
				ctrlr.set("/matrix/routingVisualiser/rangesMin", mins);
				ctrlr.set("/matrix/routingVisualiser/rangesMax", maxs);
			};
		};
	}

	setupOscListeners {
		//input buttons
		OSCFunc(
			{
				arg msg;
				var inputs, output;
				msg.postln;
				output = msg[1];
				inputs = msg[3..];
				if(inputs[0].asSymbol != \none)
				{
					inputs.do{
						arg inputIndex;
						//create connection if doesn't exist yet
						var connectionName;
						connectionName = ("c" ++ inputIndex.asString ++ "_" ++ output.asString).asSymbol;

						if(	editMode == \modify)
						{
							if(allConnections[selectedKey].at(connectionName) == nil)
							{
								allConnections[selectedKey][connectionName] = (in:inputIndex, out:output, min:0,max:1);
								this.sendGlobalConnections();
							}
						};

						if(	editMode == \delete)
						{
							if(allConnections[selectedKey].at(connectionName) != nil)
							{
								allConnections[selectedKey].removeAt(connectionName);
								this.sendGlobalConnections();

							}
						};
						this.sendConnections();
					}
				};

			},"/matrix/dragButton/down"
		);

		//drag button touch mode
		OSCFunc(
			{
				arg msg;
				var min, max;
				var inputs, output;
				output = msg[1];
				inputs = msg[5..];
				min =  msg[2];
				max =  msg[3];
				if(inputs[0].asSymbol != \none)
				{
					inputs.do{
						arg inputIndex;
						var connectionName;
						connectionName = ("c" ++ inputIndex.asString ++ "_" ++ output.asString).asSymbol;
						if(allConnections[selectedKey].at(connectionName) != nil)
						{
							allConnections[selectedKey][connectionName].min = min;
							allConnections[selectedKey][connectionName].max = max;
							this.sendConnections();
						}
					}
				}
			},"/matrix/dragButton/minmax"
		);

		OSCFunc(
			{
				arg msg;
				var moduleIndex;
				moduleIndex = msg[1];

				selectedInModule = modules[moduleIndex];
				selectedKey = (selectedInModule ++ "_" ++ selectedOutModule).asSymbol;

				SCM.ctrlrs.do{
					arg ctrlr;
					ctrlr.set("/matrix/outputName/value", selectedInModule);
				};
				this.sendConnections();
				this.sendInputNames();
				this.sendOutputNames();
			},"/matrix/moduleRoutingSelect/selectedinput"
		);
		OSCFunc(
			{
				arg msg;
				var moduleIndex;
				moduleIndex = msg[1];
				selectedOutModule = modules[moduleIndex];
				selectedKey = (selectedInModule ++ "_" ++ selectedOutModule).asSymbol;
				this.sendConnections();
				SCM.ctrlrs.do{
					arg ctrlr;
					ctrlr.set("/matrix/inputName/value", selectedOutModule);
				};
				this.sendInputNames();
				this.sendOutputNames();
			},"/matrix/moduleRoutingSelect/selectedoutput"
		);


		OSCFunc(
			{
				arg msg;
				var index;
				index = msg[1..].asInt.indexOf(1);
				if(index == 0)
				{
					editMode = \delete;
				};
				if(index == 1)
				{
					editMode = \modify;
				};
			},"/matrix/matrixEditMode/x"
		);
	}
}