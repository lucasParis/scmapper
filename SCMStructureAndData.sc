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
	var oscPort;

	var netAddr;
	var formatAddressWithPostFix;
	var formatAddressWithoutPostFix;

	var < focus;

	var < interactionMethod;

	var < listenerList;

	var <> callbackFunction;

	*new{
		arg containerName = "interface1", oscPort = 8000;
		^super.newCopyArgs(containerName, oscPort).init();
	}

	init {

		netAddr = NetAddr("127.0.0.1", oscPort);

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
		colors = (	\prepare: 8336384, \normal: 4868682, \automate: 2129688);

		//send color to all focus's controls with selected interaction mode's color
		if(focus != nil)
		{
			focus.controls.keysValuesDo{
				arg name, scmCtrl;
				netAddr.sendMsg(formatAddressWithoutPostFix.(containerName, scmCtrl.name), '@color', colors[interactionMethod]);
				netAddr.sendMsg(formatAddressWithPostFix.(containerName, scmCtrl.name, scmCtrl.postFix), scmCtrl.getValueByInteractionMethod(interactionMethod));
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
			netAddr.sendMsg(formatAddressWithPostFix.(containerName, scmCtrl.name, scmCtrl.postFix), value);
		}


	}

	set{
		//excludeFromCallback is usefull when the data being show doesnt have a direct osc element in the interface.
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

	jump{
		focus.jump;
	}

	checkForAutomationAndGo{
		focus.checkForAutomationAndGo;
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
				var ctrlAddress = formatAddressWithPostFix.(containerName, scmCtrl.name, scmCtrl.postFix);

				//send OSC initial value
				netAddr.sendMsg(ctrlAddress, scmCtrl.value);

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
						}, ctrlAddress,  netAddr
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
		controls[(name ++ postFix.asString).asSymbol].automationCallback = {
			arg name, postFix;
			this.onAutomationMoveCallback(name, postFix);
		};

	}

	removeControl{
		arg name, postFix = "/x";

		//remove automation callback
		controls[(name ++ postFix.asString).asSymbol].automationCallback = nil;

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
			controls[(name ++ postFix.asString).asSymbol].setValueByInteractionMethod(val, interactionMethod);

			this.executeCallback(name, postFix, interactionMethod, sourceFocuser);
		};
	}

	onAutomationMoveCallback{
		arg name, postFix;
		this.executeCallback(name, postFix);
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
