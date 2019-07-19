
SCMOSCMatrixMenu {
	var netAddr;
	var name;


	var < matrixCtrls;
	var < matrixCtrlsController;
	var < matrixCtrlsDataStructure;

	var < matrixController;

	//reference to main menu to know what is the currently selected module
	var <> refToMainMenu;


	var < scmMatrix;

	//menu variables/conditions
	var selectedOutModule;
	var selectedInModule;
	var selectedKey;

	//behavior variables
	var editMode;

	*new{
		arg netAddr, name;
		^super.newCopyArgs(netAddr, name).init();
	}

	scmMatrix_ {
		//called in SCM.setupMatrix
		arg matrix;
		scmMatrix = matrix;

		selectedInModule = scmMatrix.modulesNames[0];
		selectedOutModule = scmMatrix.modulesNames[1];
		selectedKey = (selectedInModule ++ "_" ++ selectedOutModule).asSymbol;

		//initialise/send data
		this.setSelectedModulesAndSendToOSC(selectedInModule, selectedOutModule);
	}

	updatePlayStates{

	}


	convertConnectionNameToIndexes{
		arg name;
		var indexes;
		indexes = name.asString.split($_);
		^indexes.collect{arg chars; chars.replace("c").asInteger};
	}

	convertIndexesToConnectionName{
		arg indexes;
		var name;
		^name = ("c" ++ indexes[0].asString ++ "_" ++"c"++ indexes[1].asString).asSymbol;
	}

	sendConnections {
		var ins, outs, mins, maxs;
		var connections;

		connections = matrixController.focus.controls.collect{
			arg scmCtrl;
			var inout;
			inout = this.convertConnectionNameToIndexes(scmCtrl.name);
			(\in: inout[0], \out: inout[1], \min: scmCtrl.value[0], \max: scmCtrl.value[1])
		};
		ins = connections.collectAs({arg val; val[\in]},Array);
		outs = connections.collectAs({arg val; val[\out]}, Array);
		mins = connections.collectAs({arg val; val[\min]}, Array);
		maxs = connections.collectAs({arg val; val[\max]}, Array);

		if(connections.isEmpty)
		{
			netAddr.sendMsg("/matrix/routingVisualiser/inConnections", \none);
			netAddr.sendMsg("/matrix/routingVisualiser/outConnections", \none);
			netAddr.sendMsg("/matrix/routingVisualiser/rangesMax", 1);
			netAddr.sendMsg("/matrix/routingVisualiser/rangesMin", 0);
		}
		{
			netAddr.sendMsg("/matrix/routingVisualiser/inConnections", *ins);
			netAddr.sendMsg("/matrix/routingVisualiser/outConnections", *outs);
			netAddr.sendMsg("/matrix/routingVisualiser/rangesMin", *mins);
			netAddr.sendMsg("/matrix/routingVisualiser/rangesMax", *maxs);
		};
	}

	sendInputNames {
		//get names from matrix
		var names = scmMatrix.moduleData[selectedInModule].inputs;
		if(names.isEmpty)
		{
			names = ['empty'];
		};
		//send to this controller
		netAddr.sendMsg("/matrix/dragButton/setLabels", *names);
	}

	sendOutputNames{
		//get names from matrix
		var names = scmMatrix.moduleData[selectedOutModule].outputs;
		if(names.isEmpty)
		{
			names = ['empty'];
		};
		//send to this controller
		netAddr.sendMsg("/matrix/outButtons/setLabels", *names);

	}


	setSelectedModulesAndSendToOSC{
		arg inModule, outModule;

		//set our selectedkey
		selectedKey = (inModule ++ "_" ++ outModule).asSymbol;

		netAddr.sendMsg("/matrix/outputName/value", outModule);
		netAddr.sendMsg("/matrix/inputName/value", inModule);

		//set our focus to the right datastructure
		matrixController.setFocus(scmMatrix.connectionsDataStructureDict[selectedKey], false);
		this.sendConnections();//move sendconnection to callback from value, so two matrixes focusing on same thing update accordingly
		this.sendInputNames();
		this.sendOutputNames();
	}

	init{

		editMode = \modify;

		matrixController = SCMStructureController("doesn'tneedaname?");
		matrixController.callbackFunction = {
			arg scmCtrl, controllerName;
			this.sendConnections();
		};

		//list of controls for matrix
		matrixCtrls = [];

		//select current module as output
		matrixCtrls = matrixCtrls.add(
			SCMMetaCtrl(\selectCurrentAsOutput, 0, "/x").functionSet_{
				arg val;
				if(val>0.5)
				{
					var currentModuleName;
					currentModuleName = refToMainMenu.selectedGroup.name;
					selectedOutModule = currentModuleName;
					this.setSelectedModulesAndSendToOSC(selectedInModule, selectedOutModule);
				};
			};
		);
		//select current module as input
		matrixCtrls = matrixCtrls.add(
			SCMMetaCtrl(\selectCurrentAsInput, 0, "/x").functionSet_{
				arg val;
				if(val>0.5)
				{
					var currentModuleName;
					currentModuleName = refToMainMenu.selectedGroup.name;
					selectedInModule = currentModuleName;
					this.setSelectedModulesAndSendToOSC(selectedInModule, selectedOutModule);
				};
			};
		);

		//select input module
		matrixCtrls = matrixCtrls.add(
			SCMMetaCtrl(\chooseModule, 0, "/selectedinput").functionSet_{
				arg moduleIndex;
				if(moduleIndex < scmMatrix.modulesNames.size)
				{
					selectedInModule = scmMatrix.modulesNames[moduleIndex];
					this.setSelectedModulesAndSendToOSC(selectedInModule, selectedOutModule);
				};
			};
		);

		//select output module
		matrixCtrls = matrixCtrls.add(
			SCMMetaCtrl(\chooseModule, 0, "/selectedoutput").functionSet_{
				arg moduleIndex;

				if(moduleIndex < scmMatrix.modulesNames.size)
				{
					//get in SCMMatrix the module name
					selectedOutModule = scmMatrix.modulesNames[moduleIndex];
					this.setSelectedModulesAndSendToOSC(selectedInModule, selectedOutModule);

				}
			};
		);

		//drag button down (edit/delete selection)
		matrixCtrls = matrixCtrls.add(
			SCMMetaCtrl(\dragButton, 0, "/down").functionSet_{
				arg val;
				var moduleOutputs, moduleInput;
				moduleInput = val[0];
				moduleOutputs = val[2..];

				//check if that moduleInput index has an input for this module
				if(moduleInput < scmMatrix.moduleData[selectedInModule].inputs.size )
				{
					//check if an output connection was selected
					if(moduleOutputs[0].asSymbol != \none)
					{
						//loop through those outputs to connect to input
						moduleOutputs.do{
							arg outputIndex;
							//check if that outputIndex has an output for this module
							if(outputIndex < scmMatrix.moduleData[selectedOutModule].outputs.size)
							{

								//create connection if doesn't exist yet
								var connectionName;
								connectionName =this.convertIndexesToConnectionName([outputIndex, moduleInput]);

								if(	editMode == \modify)
								{
									//the goal here is to create a connection,
									//which means to create a new SCMMetaControl of two values that need to be stored (In a SCMDataStructure in matrix)
									//then this menu can focus on those connections, modifying and showing through a custom interface translator
									//this menu can act as the translator

									//check if connection exists
									if(scmMatrix.connectionsDataStructureDict[selectedKey].includesControl(connectionName, postFix:"") == false)
									{
										scmMatrix.connectionsDataStructureDict[selectedKey].addControl(SCMMetaCtrl(connectionName, [0,0], postFix:""));
										scmMatrix.connectBusses(selectedOutModule, selectedInModule, outputIndex, moduleInput,0,0);
									};
									scmMatrix.sendGlobalConnections();
								};

								if(	editMode == \delete)
								{
									if(scmMatrix.connectionsDataStructureDict[selectedKey].includesControl(connectionName, postFix:"") == true)
									{
										scmMatrix.connectionsDataStructureDict[selectedKey].removeControl(connectionName, postFix:"");
										this.sendConnections();
										scmMatrix.deleteConnectionSynth(selectedOutModule, selectedInModule, outputIndex, moduleInput);
									};
									scmMatrix.sendGlobalConnections();
								};
							};
						};
					};
				};
			};
		);

		//drag button minmax(move) set range
		matrixCtrls = matrixCtrls.add(
			SCMMetaCtrl(\dragButton, 0, "/minmax").functionSet_{
				arg val;
				var min, max;
				var inputs, output;
				output = val[0];//input/output name are inversed
				inputs = val[4..];
				min =  val[1];
				max =  val[2];

				//check if an output was selected
				if(inputs[0].asSymbol != \none)
				{
					//loop through the module outputs and connect each to drag input
					inputs.do{
						arg inputIndex;
						var connectionName;
						connectionName = this.convertIndexesToConnectionName([ inputIndex, output]);
						matrixController.set(connectionName, postFix:"", value:[min,max], excludeFromCallback:false);
						scmMatrix.modifySynthConnection(selectedOutModule, selectedInModule, inputIndex, output, min* -1, max * -1);
					}
				}


			};
		);

		netAddr.sendMsg("/matrix/matrixEditMode/x", *[0,1]);


		//set edit mode (delete/modify)
		matrixCtrls = matrixCtrls.add(
			SCMMetaCtrl(\matrixEditMode, 0, "/x").functionSet_{
				arg val;
				var index;
				index = val.asInt.indexOf(1);
				if(index == 0)
				{
					editMode = \delete;
				};
				if(index == 1)
				{
					editMode = \modify;
				};
			};
		);


		//init controller and data structure
		matrixCtrlsController = SCMStructureController('matrix', netAddr);
		matrixCtrlsDataStructure = SCMControlDataStructure();

		//copy the controls over to the datastructure
		matrixCtrls.do{
			arg ctrl;
			matrixCtrlsDataStructure.addControl(ctrl);
		};

		//set controller to datastructure
		matrixCtrlsController.setFocus(matrixCtrlsDataStructure);
	}
}

SCMMatrix {

	//data used by matrixMenu
	var < modulesNames;
	var < moduleData;// datastructure of type: (\module1Name : (\inputs: [\name, \name], \outputs: [\name, \name]), \module2Name ...)
	//state of intermodules
	var globalConnections;


	//other
	var allPossibleConnections;

	//dictionary of data structures of connection controls(variable size)
	var < connectionsDataStructureDict;

	//dict to keep track of connection synths
	var interModuleConnectionSynthsDict;

	*new{
		^super.new.init();
	}

	init{

		modulesNames = SCM.groups.collect{ arg group; group.name; };

		//send names to matrix
		SCM.ctrlrs.do{
			arg ctrlr;
			// ctrlr.sendMsg("/matrix/moduleRoutingSelect/setLabels", modulesNames);
			ctrlr.sendMsg("/matrix/chooseModule/setLabels", modulesNames);
		};


		//assemble the input/output names
		moduleData = modulesNames.collect(
			{
				arg name;
				var group, dataDict;
				dataDict = ();
				group = SCM.getGroup(name);
				dataDict[\inputs] = group.matrixInputs;
				dataDict[\outputs] = group.matrixOutputs;

				[name, dataDict]
			}
		).flatten.asDict;

		allPossibleConnections = (modulesNames!2).allTuples.collect{arg array; (array[0] ++ "_" ++ array[1]).asSymbol};

		//dict of SCMControlDataStructure
		connectionsDataStructureDict = allPossibleConnections.collect{arg connectionName; [connectionName, SCMControlDataStructure()]}.flatten.asDict;

		//dict to keep track of connection synths
		interModuleConnectionSynthsDict = ();

		// allConnections = allPossibleConnections.collect({arg name; [name, ()]}).flatten.asDict;
		this.sendGlobalConnections();
	}

	deleteConnectionSynth{
		arg outModuleName, inModuleName, outputIndex, inputIndex;
		var connectionKey, synth, inIndexName, inbus;
		connectionKey = [outModuleName, inModuleName, outputIndex, inputIndex].asCompileString.asSymbol;
		synth = interModuleConnectionSynthsDict[connectionKey];
		synth.set(\base, 0);
		synth.set(\range, 0);
		//it might be a problem that the set doesnt get confirmed... maybe find another aproach to recenter...
		{
			interModuleConnectionSynthsDict[connectionKey].free;
			interModuleConnectionSynthsDict[connectionKey] = nil;

		}.defer(0.2);
	}

	modifySynthConnection{
		arg outModuleName, inModuleName, outputIndex, inputIndex, min, max;
		var connectionKey, synth;
		connectionKey = [outModuleName, inModuleName, outputIndex, inputIndex].asCompileString.asSymbol;
		synth = interModuleConnectionSynthsDict[connectionKey];
		synth.set(\base, min);
		synth.set(\range, max);

	}

	connectBusses{
		arg outModuleName, inModuleName, outputIndex, inputIndex, min, max;
		var inbus, outbus, inIndexName, outIndexName, inModule, outModule, varConnectionType, connectionKey,  isQuad = false;

		//get group from name
		inModule = SCM.getGroup(inModuleName);
		outModule = SCM.getGroup(outModuleName);


		//convert from index to name
		outIndexName = moduleData[outModuleName][\outputs][outputIndex];
		inIndexName = moduleData[inModuleName][\inputs][inputIndex];


		//get bus numbers
		inbus = inModule.matrixBusses[inIndexName];
		outbus = outModule.matrixBusses[outIndexName];

		if(inbus.rate == \control && outbus.rate == \control)
		{
			varConnectionType = \matrixConnectionKr;
		};
		if(inbus.rate == \control && outbus.rate == \audio)
		{
			varConnectionType = \matrixConnectionArKr;
			if(isQuad == true)
			{
				varConnectionType = \matrixConnectionArKr;
			};
		};
		if(inbus.rate == \audio && outbus.rate == \control )
		{
			varConnectionType = \matrixConnectionKrAr;
			if(isQuad == true)
			{
				varConnectionType = \matrixConnectionKrAr;
			}
		};
		if(inbus.rate == \audio && outbus.rate == \audio)
		{
			varConnectionType = \matrixConnectionAr;
			if(isQuad == true)
			{
				varConnectionType = \matrixConnectionAr;
			}
		};


		connectionKey = [outModuleName, inModuleName, outputIndex, inputIndex].asCompileString.asSymbol;
		if(varConnectionType != nil)
		{
			interModuleConnectionSynthsDict[connectionKey] = Synth.new(varConnectionType, [\in:outbus, out:inbus, \base:min,\range:max]);
		}
	}


	sendGlobalConnections {
		//return a list of active inter group connections by spliting the connection name that are not empty
		var globalConnections;
		globalConnections  = connectionsDataStructureDict.collect({arg  value, key; var returnVal; if(value.controls.isEmpty == true){returnVal = nil}{returnVal = key}; returnVal;}).keys.asArray.collect{arg st; st.asString.split($_)};

		//convert the group names to indexes
		globalConnections = globalConnections.deepCollect(2, {arg v; modulesNames.indexOf(v.asSymbol)});
		if(globalConnections.isEmpty)
		{
			globalConnections = "empty";
		};

		//update osc outputs everywhere
		SCM.ctrlrs.do{
			arg ctrlr;
			ctrlr.sendMsg("/matrix/routingVisualiser4/connections", globalConnections.flatten);
		};
	}

}