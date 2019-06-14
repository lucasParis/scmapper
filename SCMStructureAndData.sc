SCMStructureController{
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
		//notification from focus, called when a ctrl changes
		//the controller knows what interaction mode this controller is in so it feeds the right data
		arg value, scmCtrl;

		netAddr.sendMsg(formatAddressWithPostFix.(containerName, scmCtrl.name, scmCtrl.postFix), value);

	}

	setFocus{
		arg focus_;
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
		controls[name] = scmCtrl;
	}

	set{
		arg name, postFix, val, interactionMethod = \normal, sourceFocuser = nil; // , hash; //use hash to not send back to where the control came from
		name = name.asSymbol;

		controls[name].setValueByInteractionMethod(val, interactionMethod);

		//send value to focusers if they are in the right mode
		focusers.keysValuesDo{
			arg focuserHash, focuser;
			//if this is not the source focuser and it's interaction method matches
			if(focuserHash != sourceFocuser.hash && focuser.interactionMethod == interactionMethod)
			{
				var value = controls[name].getValueByInteractionMethod(interactionMethod);

				focuser.valueChangedFromFocus(value, controls[name]);
			};
		};
	}
}
