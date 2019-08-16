//make an alternative structureController for data that is always set and presented through a mediated interface (menu) (ie controls that have no dedicated UI element with a constant OSC address)

//just like SCMStructureController but with diferrent input and output mecanisms


SCMStructureController{
	//in truth this one is actually very specific to a direct OSC workflow, make another polyphormphic class for the different kind of interaction?
	/*
	this class is meant to provide a bridge between a OSC UI (window/container/panel) and the controls (datastructure) of a module or menu
	it is able to show the data to UI in different ways and set the data in different ways ( for meta)
	it is able to focus on diferent parts of the data ?

	*/

	var containerName;
	var netAddr;
	var recvPort;


	var formatAddressWithPostFix;
	var formatAddressWithoutPostFix;

	var < focus;

	var < interactionMethod;

	var < listenerList;

	var <> callbackFunction;

	*new{
		arg containerName = "interface1", netAddr = nil, recvPort = nil;
		^super.newCopyArgs(containerName, netAddr, recvPort).init();
	}

	init {

		// netAddr = NetAddr("127.0.0.1", oscPort);

		//formating address function
		formatAddressWithPostFix = {arg name, ctrlname, postFix; "/" ++ name ++ "/" ++ ctrlname ++ postFix};
		formatAddressWithoutPostFix = {arg name, ctrlname; "/" ++ name ++ "/" ++ ctrlname};

		listenerList = [];

		// displayMethod = \prepare;
		// displayMethod = \automate;
		this.interactionMethod = \normal;
		callbackFunction = nil;

	}

	interactionMethod_ {
		//set the way the data is presented to the UI (color and source) and the way the UI modifies the data
		arg method;
		var colors, color;
		interactionMethod = method;
		colors = (\fastPrep:	8348416, \prepare: 8336384, \normal: 4868682, \automate: 2129688);//5197647

		//send color to all focus's controls with selected interaction mode's color
		if(focus != nil)
		{
			focus.controls.keysValuesDo{
				arg name, scmCtrl;
				if(netAddr != nil)
				{
					netAddr.sendMsg(formatAddressWithoutPostFix.(containerName, scmCtrl.name), '@color', colors[interactionMethod]);
					netAddr.sendMsg(formatAddressWithPostFix.(containerName, scmCtrl.name, scmCtrl.postFix), *scmCtrl.getValueByInteractionMethod(interactionMethod));
				};
			};
		};

	}

	free{
		//reset listener list
		this.emptyListerners();

		//remove oneSelfFrom focused object
		if(focus != nil)
		{
			focus.removeFocuser(this);
			focus = nil;
		};
	}

	emptyListerners{
		//clear old listeners
		listenerList.do{
			arg listerner;
			listerner.free;
		};
		listenerList = [];
	}


	valueChangedFromFocus{
		arg value, scmCtrl;
		//notification from focus, called when a ctrl changes

		// ---- > in alternative version of controller (for scenarios like the matrix) this does something else

		//the controller knows what interaction mode this controller is in so it feeds the right data

		//make this a function so you can control how it sends the value back, or make a child class of this controller to replace this
		if(callbackFunction != nil)
		{
			callbackFunction.(scmCtrl, containerName);
		}
		{
			if(netAddr != nil)
			{
				netAddr.sendMsg(formatAddressWithPostFix.(containerName, scmCtrl.name, scmCtrl.postFix), *value);
			};
		}
	}

	set{
		//excludeFromCallback is usefull when the data being shown doesnt have a direct osc element in the interface.
		//we then rely on this feedback for visualisation so we dont want to exclude

		arg name, postFix = "/x", value = 0, excludeFromCallback = true;
		var focuserReference;
		focuserReference = this;
		if(excludeFromCallback == false)
		{
			focuserReference = nil;
		};

		focus.set(name, postFix, value, this.interactionMethod, focuserReference);
	}

	//meta controls passdown
	jump{
		focus.jump;
	}

	shiftUpDown{
		arg shiftValue;
		focus.shiftUpDown(shiftValue);
	}

	startShiftUpDown{
		focus.startShiftUpDown();
	}

	endShiftUpDown{
		focus.endShiftUpDown();
	}

	startPresetMorph{
		focus.startPresetMorph();
	}

	presetMorph{
		arg name, postFix, amount, value;
		focus.presetMorph(name, postFix, amount, value);

	}

	startRandomize{
		focus.startRandomize();
	}

	currentToPrep{
		focus.currentToPrep();
	}

	randomize{
		arg val;
		focus.randomize(val);
	}

	startFadeToPrep{
		focus.startFadeToPrep();
	}

	fadeToPrep{
		arg val;
		focus.fadeToPrep(val);
	}


	checkForAutomationAndGo{
		focus.checkForAutomationAndGo;
	}

	enterFastPrep{
		focus.enterFastPrep;
	}

	exitFastPrep{
		focus.exitFastPrep;
	}

	stopAutomation{
		focus.stopAutomation;
	}

	setAutomationTime
	{
		arg autoTime;
		focus.setAutomationTime(autoTime);
	}

	setFocus{
		//set a focused database
		//add listerners according to whats in the database
		arg focus_, createOscListeners = true;
		//if we are focused on something, then remove ourselves from that focus
		if(focus != nil)
		{
			focus.removeFocuser(this);

			//reset listener list
			this.emptyListerners();
		};

		//set the focus we currently have
		focus = focus_;

		//add ourselves to the focused object
		focus.addFocuser(this);

		if(createOscListeners == true)
		{
			//iterate through focus controls to setup brigde
			focus.controls.keysValuesDo{
				arg key, scmCtrl;
				var valueOut;
				var ctrlAddress = formatAddressWithPostFix.(containerName, scmCtrl.name, scmCtrl.postFix);

				//send OSC initial value

				valueOut = scmCtrl.value;

				//radio mode
				if(scmCtrl.isRadio == true)
				{
					var radioValue;
					radioValue = Array.fill(32,0);
					valueOut = (valueOut * (scmCtrl.radioCount-1)).round;
					radioValue[valueOut] = 1;
					valueOut = radioValue;
				};

				if(netAddr!= nil)
				{
					netAddr.sendMsg(ctrlAddress, *valueOut);
				};
				// addListener
				listenerList = listenerList.add(
					OSCFunc(
						{
							arg msg;
							var val;

							val = msg[1..];
							if(val.size == 1)
							{
								val = val[0];
							};

							focus.set(scmCtrl.name, scmCtrl.postFix, val, this.interactionMethod, this);
						}, ctrlAddress,  netAddr, recvPort
					)
				);
			};
		};
	}



}



SCMControlDataStructure {
	//always go thought this data structure, never touch controls directly, because it's this structure that is supposed to synchronise every controller
	var <> controls;
	var < focusers;

	*new{
		^super.new.init();
	}

	init {
		focusers = ();
		controls = ();

	}

	addFocuser{
		arg focuser;
		focusers[focuser.hash] = focuser;
	}

	removeFocuser{
		arg focuser;
		focusers.removeAt(focuser.hash);
	}

	addControl{
		arg scmCtrl;
		var name, postFix;

		name = scmCtrl.name;
		postFix = scmCtrl.postFix;

		//addpostfix to key
		controls[(name ++ postFix.asString).asSymbol] = scmCtrl;

		//add callback to this for automation
		controls[(name ++ postFix.asString).asSymbol].valueInternalChangeCallback = {
			arg name, postFix, interactionMethod;
			this.onInternalChangeCallback(name, postFix, interactionMethod);
		};

	}

	removeAllControls{
		//remove automation callback
		controls.keysValuesDo{
			arg name, control;
			control.valueInternalChangeCallback = nil;
		};
		controls = ();

	}

	removeControl{
		arg name, postFix = "/x";

		//remove automation callback
		controls[(name ++ postFix.asString).asSymbol].valueInternalChangeCallback = nil;

		controls.removeAt((name ++ postFix.asString).asSymbol);
	}

	includesControl{
		arg name, postFix = "/x";
		^controls.includesKey((name ++ postFix.asString).asSymbol);
	}

	jump{
		controls.keysValuesDo{
			arg name, scmCtrl;
			scmCtrl.jump;
			this.executeCallback(scmCtrl.name, scmCtrl.postFix, \normal, nil);
		};
	}

	shiftUpDown{
		arg shiftValue;
		controls.keysValuesDo{
			arg name, scmCtrl;
			scmCtrl.shiftUpDown(shiftValue);
			this.executeCallback(scmCtrl.name, scmCtrl.postFix, \normal, nil);
		};
	}

	startShiftUpDown{
		controls.keysValuesDo{
			arg name, scmCtrl;
			// name.postln;
			scmCtrl.startShiftUpDown();
			// this.executeCallback(scmCtrl.name, scmCtrl.postFix, \normal, nil);
		};
	}

	//can remove?
	endShiftUpDown{
		controls.keysValuesDo{
			arg name, scmCtrl;
			// name.postln;
			scmCtrl.endShiftUpDown();
			// this.executeCallback(scmCtrl.name, scmCtrl.postFix, \normal, nil);
		};
	}

	startPresetMorph{
		controls.keysValuesDo{
			arg name, scmCtrl;
			scmCtrl.startPresetMorph();
		};
	}

	currentToPrep{
		controls.keysValuesDo{
			arg name, scmCtrl;
			scmCtrl.currentToPrep();
		};
	}

	presetMorph{
		arg name, postFix, amount, value;
		// controls[]
		if(controls[(name ++ postFix.asString).asSymbol] != nil)
		{
			controls[(name ++ postFix.asString).asSymbol].presetMorph(amount, value);
			this.executeCallback(name, postFix, \normal, nil);

		};
		//
		// controls.keysValuesDo{
		// 	arg name, scmCtrl;
		// 	if(controls[(name ++ postFix.asString).asSymbol] != nil)
		//
		// 	scmCtrl.presetMorph(amount, value);
		// };
	}

	startRandomize{
		controls.keysValuesDo{
			arg name, scmCtrl;
			scmCtrl.startRandomize();
		};
	}

	randomize{
		arg val;
		controls.keysValuesDo{
			arg name, scmCtrl;
			// name.postln;
			scmCtrl.randomize(val);
			//update to UI values
			this.executeCallback(scmCtrl.name, scmCtrl.postFix, \normal, nil);
		};
	}

	startFadeToPrep{
		controls.keysValuesDo{
			arg name, scmCtrl;
			scmCtrl.startFadeToPrep();
		};
	}
	enterFastPrep{
		controls.keysValuesDo{
			arg name, scmCtrl;
			scmCtrl.enterFastPrep();
		};
	}

	exitFastPrep{
		controls.keysValuesDo{
			arg name, scmCtrl;
			scmCtrl.exitFastPrep();
		};
	}

	fadeToPrep{
		arg val;
		controls.keysValuesDo{
			arg name, scmCtrl;
			// name.postln;
			scmCtrl.fadeToPrep(val);
			//update to UI values
			this.executeCallback(scmCtrl.name, scmCtrl.postFix, \normal, nil);
		};
	}


	checkForAutomationAndGo{
		controls.keysValuesDo{
			arg name, scmCtrl;
			scmCtrl.checkForAutomationAndGo();
		};
	}

	stopAutomation{
		controls.keysValuesDo{
			arg name, scmCtrl;
			scmCtrl.stopAutomation();
		};
	}
	setAutomationTime{
		arg autoTime;
		controls.keysValuesDo{
			arg name, scmCtrl;
			scmCtrl.setAutomationTime(autoTime);
		};
	}


	set{
		arg name, postFix, val, interactionMethod = \normal, sourceFocuser = nil; // , hash; //use hash to not send back to where the control came from
		name = name.asSymbol;

		if(controls[(name ++ postFix.asString).asSymbol] != nil)
		{
			/*if(controls[(name ++ postFix.asString).asSymbol].isRadio == true)
			{
			var radioValue;

			radioValue = val.find([1]);
			(radioValue != nil).if{
			val = radioValue;
			};
			};*/
			controls[(name ++ postFix.asString).asSymbol].setValueByInteractionMethod(val, interactionMethod);

			this.executeCallback(name, postFix, interactionMethod, sourceFocuser);
		};
	}

	onInternalChangeCallback{
		arg name, postFix, interactionMethod;
		this.executeCallback(name, postFix, interactionMethod);
	}

	executeCallback{
		arg name, postFix, interactionMethod = \normal, sourceFocuser = nil;
		//send value to focusers if they are in the right mode

		focusers.keysValuesDo{
			arg focuserHash, focuser;

			//if this is not the source focuser and it's interaction method matches
			if(focuserHash != sourceFocuser.hash && focuser.interactionMethod == interactionMethod)
			{
				var value = controls[(name ++ postFix.asString).asSymbol].getValueByInteractionMethod(interactionMethod);

				focuser.valueChangedFromFocus(value, controls[(name ++ postFix.asString).asSymbol]);
			};
		};
	}
}
