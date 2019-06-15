

SCMOSCMainMenu{
	var netAddr;
	var name;

	var menuControls;
	var moduleInteractionMethod;
	var selectedGroup;


	//controllers for the selected module
	var moduleControlsController;//module controls
	var moduleGenericMenuControlsController;//specific generic controls also found in the menu

	//controllers for this class menuControls
	var mainMenuController;
	//data structure for menu items affecting controls in this class (prep, jump, automate, automatestop, preptoggle...)
	var mainMenuDataStructure;
	var mainMenuControls;


	*new{
		arg netAddr, name;
		^super.newCopyArgs(netAddr, name).init();
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
			moduleControlsController = SCMStructureController(groupName, netAddr.port);
			moduleControlsController.setFocus(scmGroup.allControlsDataStructure);

			//for generic controls also found in the menu
			//could move this to init but test like this for now
			moduleGenericMenuControlsController = SCMStructureController(\mainMenu, netAddr.port);
			moduleGenericMenuControlsController.setFocus(scmGroup.menuControlsDataStructure);

			// and send values from group'menu to UI
		};
	}


	// makeMenuPath { arg menuItem; ^("/" ++ name ++ "/" ++ menuItem.name.asString ++ "/" ++ menuItem.postFix)}

	init{
		//list of controls for this menu
		mainMenuControls = [];

		//changeModule play
		mainMenuControls = mainMenuControls.add(
			SCMMetaCtrl(\changeModule, 0, '/name').functionSet_{
				arg groupName;
				// var groupName = msg;
				this.selectGroup(groupName);
			};
		);


		//init controller and data structure
		mainMenuController = SCMStructureController('mainMenu', netAddr.port);
		mainMenuDataStructure = SCMControlDataStructure();

		//copy the controls over to the datastructure
		mainMenuControls.do{
			arg ctrl;
			mainMenuDataStructure.addControl(ctrl);
		};

		//set controller to datastructure
		mainMenuController.setFocus(mainMenuDataStructure);

		// menuControls = [\changeModule, \currentToPrep, \ledsOn, \play, \volume, \prep, \jump,
		// \automateTime, \stopAutomation, \loadDefault, \liveOrDisk, \automate, \savePreset, \preset];

		// menuControls[\jump].oscFunction = {
		// 	arg msg;
		// 	if(msg[1] > 0.5)
		// 	{
		// 		/*if(groupReference != nil){
		// 		groupReference.controls.do{arg ctrl; ctrl.jump};
		//
		// 		this.updateMenuElementsFromGroup;
		// 		// groupReference.getCtrl(\volume).value;
		// 		};
		// 		*/
		// 	};
		// };


		/*menuControls[\play].oscFunction = {
		arg msg;
		if( selectedGroup!= nil)
		{
		if(msg[1] > 0.5)
		{

		selectedGroup.play;
		}
		{
		selectedGroup.stop;
		}
		/*if(groupReference != nil){
		groupReference.controls.do{arg ctrl; ctrl.jump};

		this.updateMenuElementsFromGroup;
		// groupReference.getCtrl(\volume).value;
		};
		*/
		};
		};

		};*/
	}

	getModuleInteractionMethod{
		//called when a value comes in to a module on the same ipad as this menu
		^\normal;

	}




	/*
	this.set("/masterMenu2/diskNames", ([1,2,3,4,5]).collect{arg i; i.asString});
	this.set("/masterMenu2/liveOrDisk/x", 0);
	this.set("/masterMenu2/savePreset/x", 0);
	this.set("/scTempo", 120.linlin(SCM.tempoMin, SCM.tempoMax,0,1));

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
	"current to prep".postln;
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
	"yo?".postln;
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

	*/
}

SCMOSCCtrlr{
	var < netAddr;
	var name;

	var < mainMenu;
	var < matrixMenu;

	*new{
		arg ip, port, name;
		^super.new.init(ip, port, name);
	}

	init{
		arg ip, port, name_, globalCtrlrIndex_;
		name = name_;
		netAddr = NetAddr(ip, port);

		//main per group menu
		mainMenu = SCMOSCMainMenu(netAddr, "mainMenu");
		matrixMenu = SCMOSCMatrixMenu(netAddr, "matrix");


		// output clock, simple stuff
		SCM.proxySpace.clock.play({
			var beatCount;
			//get beats loop over 2 bars
			beatCount = SCM.proxySpace.clock.beats.mod(8)*4;
			// this.set("/clockCount", beatCount);
			//wait until next 16h
			0.25;
		},4);
	}



	/*updateMenuElementsFromGroup{
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

	}*/

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
			"current to prep".postln;
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
							"yo?".postln;
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