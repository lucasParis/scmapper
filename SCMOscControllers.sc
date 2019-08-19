SCMOSCOrchestrateMenu{
	var netAddr;
	var name;

	//controllers for this class modules Controls
	var modulesMenuControllersDict;
	var modulesControlsControllersDict;

	//data structure for module items affecting controls in this class (prep, jump, automate, automatestop, preptoggle...)
	var modulesDataStructuresDict;
	// var modulesControlsDict;

	//controllers for this class menuControls
	var menuController;
	//data structure for menu items affecting controls in this class (prep, jump, automate, automatestop, preptoggle...)
	var menuDataStructure;
	var menuControls;


	// var moduleInteractionMethod;
	var metaItemsControllerDict;
	var metaItemsDataStructureDict;
	var metaItemsControlsDict;

	//for meta control: need a controller connected to the module data structure that shifts things
	var moduleOffset;

	var > mainMenuShortcut;



	var moduleNames;
	var allPresetsNames;

	*new{
		arg netAddr, name;
		^super.newCopyArgs(netAddr, name).init();
	}

	init{


		moduleOffset = 0;

		this.setupMenuCtrls;
		// this.setupModuleCtrls;


	}

	initCtrlrData{
		moduleNames = SCM.groups.collect{arg group; group.name};
		this.setupModuleCtrls;
	}

	updatePlayStates{
		//change the datastructures that each controller is connected to
		var allModulesPlayStates = [];
		10.do{
			arg i;
			var moduleIsPlaying;

			moduleIsPlaying = SCM.groups[i+moduleOffset].isPlaying.asInt;
			allModulesPlayStates = allModulesPlayStates.add(moduleIsPlaying);

			netAddr.sendMsg("/orchestrate/module" ++ i ++ "/active" , moduleIsPlaying);
		};

		netAddr.sendMsg("/orchestrate/plays/x" , *allModulesPlayStates);
	}

	updateOffsetStructures{
		netAddr.sendMsg("/orchestrate/offsetModules", moduleOffset);
		// netAddr.sendMsg("/orchestrate/plays", moduleOffset);


		//change the datastructures that each controller is connected to
		10.do{
			arg i;
			var moduleMenuDataStructureToFocusOn;
			var moduleControlsDataStructureToFocusOn;
			// var moduleIsPlaying;
			//
			// moduleIsPlaying = SCM.groups[i+moduleOffset].isPlaying.asInt;
			//
			// netAddr.sendMsg("/orchestrate/module" ++ i ++ "/active" , moduleIsPlaying);

			//----- for menu items
			if( SCM.groups[i+moduleOffset]!= nil)
			{
				moduleMenuDataStructureToFocusOn = SCM.groups[i+moduleOffset].menuControlsDataStructure;
				modulesMenuControllersDict[i].setFocus(moduleMenuDataStructureToFocusOn);

				//----- for other controls
				moduleControlsDataStructureToFocusOn = SCM.groups[i+moduleOffset].allControlsDataStructure;
				modulesControlsControllersDict[i].setFocus(moduleControlsDataStructureToFocusOn);
			};

		};

		this.updatePlayStates;
		this.sendPresetNames;

		//send orchestrate/plays/x

	}
	sendPresetNames{
		var presetExists;
		allPresetsNames = [];
		10.do{
			arg i;
			var group;
			group = SCM.getGroup(moduleNames[i + moduleOffset]);
			if(group != nil)
			{
				allPresetsNames = allPresetsNames.add(group.allPresets.collect{arg dict; dict[\name]});

			};

		};

		presetExists = allPresetsNames.collect{arg array; (0.1!array.size).extend(9,-0.2)};
		allPresetsNames = allPresetsNames.collect{arg array; array.extend(9,' ')};

		// netAddr.sendMsg("/orchestrate/presets", '@labels', *({arg i; "preset" ++ i}!90).clump(9).flop.flatten);
		netAddr.sendMsg("/orchestrate/presets", '@labels', *(allPresetsNames.flatten.clump(9).flop.flatten));
		netAddr.sendMsg("/orchestrate/presets/light", *(presetExists.flatten.clump(9).flop.flatten));
	}

	setupModuleCtrls{

		modulesMenuControllersDict = ();
		modulesControlsControllersDict = ();
		// modulesDataStructuresDict = ();
		// modulesControlsDict = ();

		metaItemsControllerDict = ();
		metaItemsDataStructureDict = ();
		metaItemsControlsDict = ();



		//create 10 controllers
		10.do{
			arg i;
			var mainMenuController, moduleMenuDataStructureToFocusOn;
			var mainModuleController, moduleControlsDataStructureToFocusOn;
			var metaController, metaDataStructure;
			var metaControls;

			var moduleName = ("module" ++ i).asSymbol;

			//----- menu controls
			//create a controller
			if(SCM.groups[i] != nil)
			{
				mainMenuController = SCMStructureController(('orchestrate/module' ++ i).asSymbol, netAddr);

				//set
				moduleMenuDataStructureToFocusOn = SCM.groups[i].menuControlsDataStructure;
				mainMenuController.setFocus(moduleMenuDataStructureToFocusOn);

				//store for later
				modulesMenuControllersDict[i] = mainMenuController;


				//----- all other controls
				//create a controller
				mainModuleController = SCMStructureController(('orchestrate/module' ++ i).asSymbol, netAddr);

				//set
				moduleControlsDataStructureToFocusOn = SCM.groups[i].allControlsDataStructure;
				mainModuleController.setFocus(moduleControlsDataStructureToFocusOn);

				//store for later
				modulesControlsControllersDict[i] = mainModuleController;



				//create controls for meta control (focusing on allcontrolsDatastructure)
				// randomize
				metaControls = [];

				metaControls = metaControls.add(
					SCMMetaCtrl(\randomize, 0, "/x").functionSet_{
						arg val;
						var value, firstTouch;
						value = val[0];
						firstTouch = val[1];
						if(firstTouch > 0.5)
						{
							mainModuleController.startRandomize();
						};

						mainModuleController.randomize(value);
						// moduleGenericMenuControlsController.jump;
					};
				);



				//shiftUpDown x
				metaControls = metaControls.add(
					SCMMetaCtrl(\shiftUpDown, 0.5, "/x").functionSet_{
						arg val;
						var value, firstTouch;
						var deadZone = 0.08;
						// val.postln;
						value = val[0].linlin(0,1,-1,1).excess(deadZone);
						value = value.linlin(-1+deadZone,1-deadZone, -1,1);
						firstTouch = val[1];
						if(firstTouch > 0.5)
						{
							mainModuleController.startShiftUpDown();
						};

						mainModuleController.shiftUpDown(value);
					};
				);

				//convert to dict
				metaControls = metaControls.collect{arg ctrl; [(ctrl.name ++ ctrl.postFix).asSymbol, ctrl]}.flatten.asDict;

				//init controller and data structure
				metaController = SCMStructureController(('orchestrate/module' ++ i).asSymbol, netAddr);
				metaDataStructure = SCMControlDataStructure();

				//copy the controls over to the datastructure
				metaControls.keysValuesDo{
					arg key, ctrl;
					metaDataStructure.addControl(ctrl);
				};

				// moduleInteractionMethod = \normal;

				//set controller to datastructure
				metaController.setFocus(metaDataStructure);

				metaItemsControllerDict[i] = metaController;
				metaItemsControlsDict[i] = metaControls;
				metaItemsDataStructureDict[i] = metaDataStructure;


				this.sendPresetNames;
			};
		};
	}

	setupMenuCtrls{
		//list of controls for this menu
		menuControls = [];
		//changeModule changeModule
		menuControls = menuControls.add(
			SCMMetaCtrl(\offsetPlus, 0, '/x').functionSet_{
				arg val;
				if(val > 0.5)
				{
					moduleOffset = (moduleOffset+1).clip(0,max(SCM.groups.size-10,0));
					this.updateOffsetStructures;
				};
			};
		);

		menuControls = menuControls.add(
			SCMMetaCtrl(\offsetMinus, 0, '/x').functionSet_{
				arg val;
				if(val > 0.5)
				{
					moduleOffset = (moduleOffset-1).clip(0,max(SCM.groups.size-10,0));
					this.updateOffsetStructures;
				};

			};
		);

		menuControls = menuControls.add(
			SCMMetaCtrl(\presets, 0, '/x').functionSet_{
				arg val;
				var index, moduleIndex, presetIndex, group;
				index = val.indexOf(1.0);
				if(index!= nil)
				{
					moduleIndex = index%10;
					presetIndex = (index/10).floor.asInt;
					// presetIndex.postln;

					//load preset

					group = SCM.groups[moduleIndex + moduleOffset];
					if(group.allPresets.size > presetIndex)
					{
						// group.allPresets.collect({arg group; group[\name]}).postln;
						if(group.allPresets[presetIndex][\name] != \empty)
						{
							// group.allPresets[presetIndex][\name].postln;
							modulesControlsControllersDict[moduleIndex].focus.controls.keysValuesDo
							{
								arg name, control;
								var postFix, presetValue;

								postFix = ("/" ++ name.asString.split($/)[1]).asSymbol;

								//add name/postfix to structure controller and datastructure

								//after diner:
								// - add name + postfix to presetMorph in controlstruct and datastruct
								// - make sure postfix is ok in presets saved



								// group.allPresets[presetIndex][\values].postln;
								//check if control exists in preset
								if(group.allPresets[presetIndex][\values].includesKey((name).asSymbol))
								{

									presetValue = group.allPresets[presetIndex][\values][(name).asSymbol];

									// modulesControlsControllersDict[moduleIndex].focus.controls.postln;
									// modulesControlsControllersDict[moduleIndex].set(name.asString.split($/)[0].asSymbol, postFix, presetValue);
									modulesControlsControllersDict[moduleIndex].presetMorph(name.asString.split($/)[0].asSymbol, postFix, 1, presetValue);
								};

							};

						};
					}


				};
			};
		);

		//gather presets

		menuControls = menuControls.add(
			SCMMetaCtrl(\plays, 0!10, '/x').functionSet_{
				arg vals;
				//set already is available in controller
				vals.do{
					arg val, j;
					if(val > 0.5)
					{
						modulesMenuControllersDict[j].set(\play, "/x", 1);
					}
					{
						modulesMenuControllersDict[j].set(\play, "/x", 0);
					};
				};
			};
		);
		menuControls = menuControls.add(
			SCMMetaCtrl(\gotoModule, 0!10, '/x').functionSet_{
				arg vals;
				var index;
				index = vals.indexOf(1.0);
				if(index != nil)
				{
					if(mainMenuShortcut!= nil)
					{
						var groupName;
						groupName = moduleNames[index + moduleOffset];

						mainMenuShortcut.selectGroup(groupName.asSymbol);
					};
				};
			};
		);

		//convert to dict
		menuControls = menuControls.collect{arg ctrl; [(ctrl.name ++ ctrl.postFix).asSymbol, ctrl]}.flatten.asDict;

		//init controller and data structure
		menuController = SCMStructureController(('orchestrate').asSymbol, netAddr);
		menuDataStructure = SCMControlDataStructure();

		//copy the controls over to the datastructure
		menuControls.keysValuesDo{
			arg key, ctrl;
			menuDataStructure.addControl(ctrl);
		};


		// moduleInteractionMethod = \normal;

		//set controller to datastructure
		menuController.setFocus(menuDataStructure);

	}
}

SCMOSCMainMenu{
	var netAddr;
	var name;
	var midiOffset;

	var menuControls;
	var < selectedGroup;

	//controllers for the selected module (should they all be already created for all the groups and you just switch the index?)
	var moduleControlsController;//module controls
	var moduleGenericMenuControlsController;//specific generic controls also found in the menu

	//controllers for this class menuControls
	var mainMenuController;
	//data structure for menu items affecting controls in this class (prep, jump, automate, automatestop, preptoggle...)
	var mainMenuDataStructure;
	var mainMenuControls;


	var moduleInteractionMethod;

	var keyboard;


	*new{
		arg netAddr, name, midiOffset;
		^super.newCopyArgs(netAddr, name, midiOffset).init();
	}

	selectGroup{
		arg groupName;
		var scmGroup;

		groupName = groupName.asSymbol;

		//get group from SCM
		scmGroup = SCM.getGroup(groupName);

		if(scmGroup != nil)
		{
			//store selected group
			selectedGroup = scmGroup;

			//setup controller
			if(moduleControlsController != nil)//if not empty, free the last one
			{
				moduleControlsController.free;
				// moduleControlsController.interactionMethod = \normal;
			};

			//setup controller
			if(moduleGenericMenuControlsController != nil)//if not empty, free the last one
			{
				moduleGenericMenuControlsController.free;
				// moduleControlsController.interactionMethod = \normal;
			};


			//for all module controls
			moduleControlsController = SCMStructureController(groupName, netAddr);
			moduleControlsController.setFocus(scmGroup.allControlsDataStructure);

			//for generic controls also found in the menu
			//could move this to init but test like this for now
			moduleGenericMenuControlsController = SCMStructureController(\mainMenu, netAddr);
			moduleGenericMenuControlsController.setFocus(scmGroup.menuControlsDataStructure);

			//send preset names
			4.do{
				arg i;
				netAddr.sendMsg(("/extraPresetMenu/presetMenu" ++ i), '@items', *scmGroup.allPresets.collect{arg dict; dict[\name]});
			};


		};
	}

	init{


		keyboard = SCMKeyboardOscMenu(netAddr);


		//list of controls for this menu
		mainMenuControls = [];

		//changeModule changeModule
		mainMenuControls = mainMenuControls.add(
			SCMMetaCtrl(\changeModule, 0, '/name').functionSet_{
				arg groupName;
				// var groupName = msg;
				this.selectGroup(groupName);
			};
		);

		//automate
		mainMenuControls = mainMenuControls.add(
			SCMMetaCtrl(\automate, 0, "/x").midiButtonMap_(7 + midiOffset).functionSet_{
				arg val;
				if(val > 0.5)
				{
					this.setModuleInteractionMethod(\automate);
				}
				{
					//on down, tell params to start their thing

					//update automation time
					var autoTime = mainMenuControls['automateTime/x'].value;
					autoTime = pow(2, autoTime*5+2).round;
					moduleControlsController.setAutomationTime(autoTime);
					moduleGenericMenuControlsController.setAutomationTime(autoTime);


					moduleControlsController.checkForAutomationAndGo;
					moduleGenericMenuControlsController.checkForAutomationAndGo;


					//check that not in prep mode:
					if(mainMenuControls['prep/x'].value > 0.5)
					{
						this.setModuleInteractionMethod(\prepare);
					}
					{
						this.setModuleInteractionMethod(\normal);
					};
				};
			};
		);
		//automate Time
		mainMenuControls = mainMenuControls.add(
			SCMMetaCtrl(\automateTime, 1/5, "/x").functionSet_{
				arg valTime;

				var autoTime = pow(2,valTime*5+2).round;
				moduleControlsController.setAutomationTime(autoTime);
				moduleGenericMenuControlsController.setAutomationTime(autoTime);


			};
		);

		//automate stop
		mainMenuControls = mainMenuControls.add(
			SCMMetaCtrl(\stopAutomation, 0, "/x").functionSet_{
				arg val;
				if(val > 0.5)
				{
					// "automate stop".postln;
					moduleControlsController.stopAutomation;
					moduleGenericMenuControlsController.stopAutomation;
				}
			};
		);

		// randomize
		mainMenuControls = mainMenuControls.add(
			SCMMetaCtrl(\randomize, 0, "/x").functionSet_{
				arg val;
				var value, firstTouch;
				value = val[0];
				firstTouch = val[1];
				if(firstTouch > 0.5)
				{
					moduleControlsController.startRandomize();
				};

				moduleControlsController.randomize(value);
				// moduleGenericMenuControlsController.jump;
			};
		);



		//shiftUpDown x
		mainMenuControls = mainMenuControls.add(
			SCMMetaCtrl(\shiftUpDown, 0.5, "/x").functionSet_{
				arg val;
				var value, firstTouch;
				var deadZone = 0.08;
				value = val[0].linlin(0,1,-1,1).excess(deadZone);
				value = value.linlin(-1+deadZone,1-deadZone, -1,1);
				firstTouch = val[1];
				if(firstTouch > 0.5)
				{
					moduleControlsController.startShiftUpDown();
				};

				moduleControlsController.shiftUpDown(value);
				// moduleGenericMenuControlsController.jump;
			};
		);

		//jump
		mainMenuControls = mainMenuControls.add(
			SCMMetaCtrl(\jump, 0, "/x").midiButtonMap_(11 + midiOffset).functionSet_({
				arg val;

				if(val > 0.5)
				{
					if(moduleControlsController!= nil)
					{
						moduleControlsController.jump;
					};

					// moduleGenericMenuControlsController.jump;
				};
			});
		);
		//prep
		mainMenuControls = mainMenuControls.add(
			SCMMetaCtrl(\prep, 0, "/x").midiButtonMap_(9 + midiOffset, true).functionSet_{
				arg val;
				if(val>0.5)
				{
					this.setModuleInteractionMethod(\prepare);
				}
				{
					this.setModuleInteractionMethod(\normal);
				};
			};
		);

		//fast prep
		mainMenuControls = mainMenuControls.add(
			SCMMetaCtrl(\fastPrep, 0, "/x").midiButtonMap_(6 + midiOffset, false).functionSet_{
				arg val;
				if(val>0.5)
				{
					this.setModuleInteractionMethod(\fastPrep);
					moduleControlsController.enterFastPrep;

				}
				{

					this.setModuleInteractionMethod(\normal);
					moduleControlsController.exitFastPrep;
				};
			};
		);

		//currentToPrep
		mainMenuControls = mainMenuControls.add(
			SCMMetaCtrl(\currentToPrep, 0, "/x").midiButtonMap_(10 + midiOffset,false).functionSet_{//
				arg val;
				if(val>0.5)
				{
					moduleControlsController.currentToPrep;
					// this.setModuleInteractionMethod(\prepare);
				};
			};
		);

		//matrixOuts
		/*mainMenuControls = mainMenuControls.add(
		SCMMetaCtrl(\matrixOuts, 0, "/x").functionSet_{
		arg val;

		};
		);

		//matrixIns
		mainMenuControls = mainMenuControls.add(
		SCMMetaCtrl(\matrixIns, 0, "/x").functionSet_{
		arg val;

		};
		);*/

		//fadeToPrep
		mainMenuControls = mainMenuControls.add(
			SCMMetaCtrl(\fadeToPrep, 0, "/x").functionSet_{
				arg val;
				var value, firstTouch;
				value = val[0];
				firstTouch = val[1];
				if(firstTouch > 0.5)
				{
					moduleControlsController.startFadeToPrep();
				};
				moduleControlsController.fadeToPrep(value);
			};
		);

		/*		//fast Prep
		mainMenuControls = mainMenuControls.add(
		SCMMetaCtrl(\fastPrep, 0, "/x").functionSet_{
		arg val;

		};
		);*/


		//save preset
		mainMenuControls = mainMenuControls.add(
			SCMMetaCtrl(\save, 0, "/x").functionSet_{
				arg val;
				if(val > 0.5)
				{
					keyboard.enable;
					keyboard.enterCallback = {
						arg string;

						// "saving".postln;
						// string.postln;
						selectedGroup.savePreset(string);
						// scmMatrix.saveMatrixPreset(string);
					};
				};

			};
		);

		//presetMorph X
		mainMenuControls = mainMenuControls.add(
			SCMMetaCtrl(\presetMorph, 0, "/xy").functionSet_{
				arg val;
				var presets;
				var x, weights, lowerPreset, higherPreset, fraction;

				x = val[0] * 3;
				// x = 0 * 4;
				lowerPreset = x.floor;
				lowerPreset = lowerPreset.clip(0,2);
				higherPreset = x.ceil;
				higherPreset = higherPreset.clip(1,3);
				fraction = x - lowerPreset;
				// weights = 1-((0..3)-x).abs.clip(0,1);

				if(val[2] > 0.5)
				{
					//firsttouch
					moduleControlsController.startPresetMorph();
				};

				//convert from morph x index to menu selected presetIndex
				lowerPreset = selectedGroup.getCtrl(("presetMenu" ++ lowerPreset.asInt).asSymbol, "/selection").value.asInt;
				higherPreset = selectedGroup.getCtrl(("presetMenu" ++ higherPreset.asInt).asSymbol, "/selection").value.asInt;

				//convert from menu selected presetIndex to presetDict
				lowerPreset = selectedGroup.allPresets[lowerPreset];
				higherPreset = selectedGroup.allPresets[higherPreset];


				moduleControlsController.focus.controls.keysValuesDo
				{
					arg name, control;
					var postFix, interpedValue;
					var lowValue, highValue;
					postFix = control.postFix;

					//add name/postfix to structure controller and datastructure

					//after diner:
					// - add name + postfix to presetMorph in controlstruct and datastruct
					// - make sure postfix is ok in presets saved
					if(lowerPreset[\values].includesKey((name).asSymbol))
					{
						if(higherPreset[\values].includesKey((name).asSymbol))
						{
							lowValue = lowerPreset[\values][(name).asSymbol];
							highValue = higherPreset[\values][(name).asSymbol];
							interpedValue = (lowValue * (1- fraction)) + (highValue * fraction);
							moduleControlsController.presetMorph(name.asString.split($/)[0], postFix, val[1], interpedValue);
						};
					};
				};
				// presets = 4.collect{
				// 	arg i;
				// 	var index;
				// 	// if()
				// 	index = selectedGroup.getCtrl(("presetMenu" ++ i).asSymbol, "/selection").value.asInt;
				//
				// };
				// presets.postln;

				/*presets[0].keysValuesDo{
				arg ctrlName, value;
				4.do{

				}*/

				// };




			};
		);

		//convert to dict
		mainMenuControls = mainMenuControls.collect{arg ctrl; [(ctrl.name ++ ctrl.postFix).asSymbol, ctrl]}.flatten.asDict;

		//init controller and data structure
		mainMenuController = SCMStructureController('mainMenu', netAddr);
		mainMenuDataStructure = SCMControlDataStructure();

		//copy the controls over to the datastructure
		mainMenuControls.keysValuesDo{
			arg key, ctrl;
			mainMenuDataStructure.addControl(ctrl);
		};

		moduleInteractionMethod = \normal;

		//set controller to datastructure
		mainMenuController.setFocus(mainMenuDataStructure);
	}

	setModuleInteractionMethod{
		arg interactionMethod;
		moduleInteractionMethod = interactionMethod;

		moduleControlsController.interactionMethod = interactionMethod;
		moduleGenericMenuControlsController.interactionMethod = interactionMethod;

	}

	getModuleInteractionMethod{
		//called when a value comes in to a module on the same ipad as this menu
		^moduleInteractionMethod;
	}
}


SCMOSCDirectCtrlr{
	// var < netAddr;
	var name;
	var port;

	var < mainMenu;
	var < matrixMenu;

	var moduleControlsControllers;//module controls
	var moduleGenericMenuControlsControllers;//specific generic controls also found in the menu


	*new{
		arg port, name;// ip,
		^super.new.init(port, name);//ip,
	}

	init{
		arg port_, name_, globalCtrlrIndex_;//ip,
		name = name_;
		port = port_;
		// netAddr = NetAddr(ip, port);





		/*// output clock, simple stuff
		SCM.proxySpace.clock.play({
		var beatCount;
		//get beats loop over 2 bars
		beatCount = SCM.proxySpace.clock.beats.mod(8)*4;
		// this.sendMsg("/clockCount", beatCount);
		//wait until next 16h
		0.25;
		},4);
		*/
	}

	initCtrlrData{
		//controllers for all SCM groups
		moduleControlsControllers = ();
		moduleGenericMenuControlsControllers = ();

		SCM.groups.do{
			arg group;


			moduleControlsControllers[group.name.asSymbol] =  SCMStructureController(group.name.asSymbol, nil, port);//netAddr // removed netaddr for changing td ports
			moduleControlsControllers[group.name.asSymbol].setFocus(group.allControlsDataStructure);

			moduleGenericMenuControlsControllers[group.name.asSymbol] =  SCMStructureController(group.name.asSymbol, nil, port);//netAddr // removed netaddr for changing td ports
			moduleGenericMenuControlsControllers[group.name.asSymbol].setFocus(group.menuControlsDataStructure);
		};
	}

	updatePlayStates{

	}

	sendMsg{
		arg path, value;
		// netAddr.sendMsg(path, *value);
	}

	sendColorMsg{
		arg path, color;
		// netAddr.sendMsg(path, '@color', color);
	}
}



SCMOSCMenuedCtrlr{
	var < netAddr;
	var name;
	var < midiOffset;

	var < mainMenu;
	var < matrixMenu;
	var < orchestrateMenu;

	// var < initCtrlrData;

	*new{
		arg ip, port, name, midiOffset;
		^super.new.init(ip, port, name, midiOffset);
	}

	init{
		arg ip, port, name_, midiOffset_;
		name = name_;
		netAddr = NetAddr(ip, port);

		midiOffset = midiOffset_;

		//main per group menu
		mainMenu = SCMOSCMainMenu(netAddr, "mainMenu", midiOffset);
		matrixMenu = SCMOSCMatrixMenu(netAddr, "matrix");
		matrixMenu.refToMainMenu = mainMenu;

		orchestrateMenu = SCMOSCOrchestrateMenu(netAddr, "orchestrate");
		orchestrateMenu.mainMenuShortcut = mainMenu;


		// output clock, simple stuff
		SCM.proxySpace.clock.play({
			var beatCount;
			//get beats loop over 2 bars
			beatCount = SCM.proxySpace.clock.beats.mod(8)*4;
			this.sendMsg("/clockCount", beatCount);
			//wait until next 16h
			0.25;
		},4);
	}

	initCtrlrData{
		orchestrateMenu.initCtrlrData;
		matrixMenu.initCtrlrData;

	}

	updatePlayStates{
		orchestrateMenu.updatePlayStates;
		matrixMenu.updatePlayStates;
	}

	sendMsg{
		arg path, value;
		netAddr.sendMsg(path, *value);
	}

	sendColorMsg{
		arg path, color;
		netAddr.sendMsg(path, '@color', color);
		// ctrlr.sendMsg(ctrldict['addr'].asString.replace("/x", ""),'@color', shiftJumpColor);//send color orange

	}
}


SCMLemurCtrlr{
	var < netAddr;
	var name;
	var <selectedGroupName;
	var <groupReference;

	var inPresetSaveMode;
	var globalCtrlrIndex;

	var liveOrDisk;

	*new{
		arg ip, port, name, globalCtrlrIndex;
		^super.new.init(ip, port, name, globalCtrlrIndex);
	}

	init{
		arg ip, port, name_, globalCtrlrIndex_;
		name = name_;
		netAddr = NetAddr(ip, port);
		selectedGroupName = nil;

		globalCtrlrIndex = globalCtrlrIndex_;

		inPresetSaveMode = false;

		this.setupMasterGroupMenu;

		liveOrDisk = 1;

		this.set("/masterMenu2/diskNames", ([1,2,3,4,5]).collect{arg i; i.asString});
		this.set("/masterMenu2/liveOrDisk/x", 0);
		this.set("/masterMenu2/savePreset/x", 0);
		this.set("/scTempo", 120.linlin(SCM.tempoMin, SCM.tempoMax,0,1));


		SCM.proxySpace.clock.play({
			var beatCount;
			beatCount = SCM.proxySpace.clock.beats.mod(8)*4;
			// netAddr.sendM
			this.set("/clockCount", beatCount);
			0.25;

		},4);



	}

	setupInstanceListener{
		//replace with srcid arg
		arg adr, function;
		OSCFunc(
			{
				|msg, time, addr, recvPort|
				//check if the message is comming from this controller
				if(addr.port == netAddr.port)
				{
					function.value(msg[1..]);
				}
			},
			adr
		);
	}

	selectGroup{
		arg groupName;
		//check if it's valid
		if(groupName.isKindOf(Symbol)){
			//check if it is in active groups

			var scmGroup = SCM.getGroup(groupName);

			if(scmGroup != nil,{
				//if found, store name and group reference
				selectedGroupName = groupName;
				// groupReference = scmGroup;
				this.setGroupRef(scmGroup);

				//and send values from group to UI
				this.updateMenuElementsFromGroup;
			},{
				//otherwise set to nil
				selectedGroupName = nil;
				groupReference = nil;
			});
		};
	}

	setGroupRef{
		arg ref;
		groupReference = ref;
	}

	getGroupRef{
		^groupReference;
	}

	setupMasterGroupMenu{
		//listener for menu group CHANGE
		this.setupInstanceListener('/masterMenu/changeModule/name', {
			arg args;
			//get the selected group from OSC
			var groupName = args[0];
			this.selectGroup(groupName);
			groupReference.controls.do{arg ctrl; ctrl.menuFeedbackIndex = globalCtrlrIndex};


		});

		//listener for menu group currentToPrep
		this.setupInstanceListener('/masterMenu2/currentToPrep/x', {
			arg args;
			if(groupReference != nil){
				if(args[0] > 0.5)
				{

					groupReference.controls.do{arg ctrl; ctrl.currentToPrep};

				}
			};

		});

		// ledsOn

		//listener for menu group ledsOn
		this.setupInstanceListener('/masterMenu/ledsOn/x', {
			arg args;
			if(groupReference != nil){
				groupReference.getCtrl('ledsOn').augmentedSet(args[0]); //
			};
		});


		//listener for menu group PLAY
		this.setupInstanceListener('/masterMenu/play/x', {
			arg args;
			if(this.getGroupRef != nil){
				if(args[0] >0.5)
				{
					this.getGroupRef.play();
				}
				{
					this.getGroupRef.stop();
				};
			};
		});

		//listener for menu group volume
		this.setupInstanceListener('/masterMenu/volume/x', {
			arg args;
			if(groupReference != nil){
				groupReference.getCtrl('volume').augmentedSet(args[0]); //
			};
		});




		//listener for menu group prep
		this.setupInstanceListener('/masterMenu/prep/x', {
			arg args;
			if(groupReference != nil){
				if(args[0] > 0.5)
				{groupReference.controls.do{arg ctrl; ctrl.enterPrepMode}; }
				{groupReference.controls.do{arg ctrl; ctrl.exitPrepMode}; }
			};
		});

		//listener for menu group prep
		this.setupInstanceListener('/masterMenu/jump/x', {
			arg args;
			if(args[0] > 0.5)
			{
				this.jumpGroup;
			}
		});
		//listener for automateTime
		this.setupInstanceListener('/masterMenu/automateTime/x', {
			arg args;

			if(groupReference != nil){
				var autoTime = pow(2,args[0]*5+2).round;
				groupReference.controls.do{arg ctrl; ctrl.setAutomateTime(autoTime)};
			}
		});

		//listener for stopAutomation
		this.setupInstanceListener('/masterMenu/stopAutomation/x', {
			arg args;

			if(groupReference != nil){
				if(args[0] > 0.5)
				{
					groupReference.controls.do{arg ctrl; ctrl.stopAutomation()};
				};
			};
		});

		//listener for loadDefault
		this.setupInstanceListener('/masterMenu2/loadDefault/x', {
			arg args;

			if(groupReference != nil){
				if(args[0] > 0.5)
				{
					groupReference.controls.do{arg ctrl; ctrl.loadDefaultToPrep()};
				};
			};
		});


		//listener for liveOrDisk
		this.setupInstanceListener('/masterMenu2/liveOrDisk/x', {
			arg args;
			if(groupReference != nil){
				liveOrDisk = args[0];
				if(liveOrDisk> 0.5)
				{
					this.set("/masterMenu2/diskNames",groupReference.filePresetNames)
				}
				{
					this.set("/masterMenu2/diskNames", ([1,2,3,4,5]).collect{arg i; i.asString});
					this.set('/masterMenu2/preset/light', groupReference.activePresets * 0.4);
				};
			};
		});



		//listener for menu group automate
		this.setupInstanceListener('/masterMenu/automate/x', {
			arg args;
			if(groupReference != nil){
				if(args[0] > 0.5)
				{
					groupReference.controls.do{arg ctrl; ctrl.enterAutomateMode};
				}
				{
					groupReference.controls.do{arg ctrl; ctrl.exitAutomateMode};
				}
			};
		});

		//listener for menu group presetSave
		this.setupInstanceListener('/masterMenu2/savePreset/x', {
			arg args;
			if(groupReference != nil){
				if(args[0] > 0.5)
				{
					inPresetSaveMode = true;
				}
				{
					inPresetSaveMode = false;
				};
			};
		});

		//listener for menu group preset
		this.setupInstanceListener('/masterMenu2/preset/x', {
			arg args;
			if(groupReference != nil){
				var radioValue;
				radioValue = args.find([1]);
				(radioValue != nil).if{
					if(inPresetSaveMode)
					{
						//SAVE
						if(liveOrDisk > 0.5)
						{//disk mode
							groupReference.savePresetToFile(radioValue);
							this.set("/masterMenu2/diskNames",groupReference.filePresetNames);

						}
						{//live Mode
							groupReference.controls.do{arg ctrl; ctrl.savePreset(radioValue)};
							groupReference.updatePresetStatus(radioValue);
							this.set('/masterMenu2/preset/light', groupReference.activePresets * 0.4);
						};
					}
					{
						//LOAD
						if(liveOrDisk > 0.5)
						{//disk mode
							groupReference.loadPresetFromFile(radioValue);
						}
						{//live Mode
							if(groupReference.activePresets[radioValue] > 0.5)
							{
								groupReference.controls.do{arg ctrl; ctrl.loadPresetToPrep(radioValue)};
							};
						};
					};
				};
			};
		});

	}

	jumpGroup{
		if(groupReference != nil){
			groupReference.controls.do{arg ctrl; ctrl.jump};

			this.updateMenuElementsFromGroup;
			// groupReference.getCtrl(\volume).value;
		};
	}

	updateMenuElementsFromGroup{
		var volume, ledsOn;

		this.set('/masterMenu/play/x', SCM.getGroup(selectedGroupName).isPlaying.asInt);

		volume = SCM.getGroup(selectedGroupName).getCtrl('volume').value;
		this.set('/masterMenu/volume/x', volume);


		ledsOn = SCM.getGroup(selectedGroupName).getCtrl('ledsOn').value;
		this.set('/masterMenu/ledsOn/x', ledsOn);

		this.set('/masterMenu2/preset/light', SCM.getGroup(selectedGroupName).activePresets * 0.8);
		if(liveOrDisk> 0.5)
		{
			this.set("/masterMenu2/diskNames",groupReference.filePresetNames)
		};

	}

	set{
		arg path, value;
		netAddr.sendMsg(path, *value);
	}

	setColor{
		arg path, color;
		netAddr.sendMsg(path, '@color', color);
		// ctrlr.sendMsg(ctrldict['addr'].asString.replace("/x", ""),'@color', shiftJumpColor);//send color orange

	}

	setPhysics{
		arg path, value;
		netAddr.sendMsg(path, '@physic', value);
		// ctrlr.sendMsg(ctrldict['addr'].asString.replace("/x", ""),'@color', shiftJumpColor);//send color orange

	}
}