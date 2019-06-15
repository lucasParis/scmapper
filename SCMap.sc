
SCM {
	classvar <proxySpace;

	classvar tempo;
	classvar <> tempoMin;
	classvar <> tempoMax;
	classvar <> presetFolder;
	classvar <> enablePresetSave;


	//list of things
	classvar <> groups;
	classvar <ctrlrs;
	classvar <midiCtrlrs;
	classvar <dataOutputs;// to touchdesigner, but could be other?

	classvar < imu;
	classvar < matrix;

	classvar < anvoMotors;


	classvar < masterServerGroup;

	//dataoutput
	classvar <> visualPatternLatency;
	classvar <> visualLatency;
	classvar <> replyIDCount;
	classvar <dataOutRate;

	//master fx
	classvar <> masterFXdeferTime;//for the buggy play not working
	classvar < masterGroup;//hold SCMGroup for masterFX

	classvar < playStates;//hold SCMGroup for masterFX
	classvar < colors;




	*init{
		"initialising SCM".postln;

		if(NetAddr.langPort != 57120)
		{
			var errorMessage = "_____ WARNING _____ langPort not 57120, reboot interpreter please ________";
			20.do{errorMessage.postln};
		};

		Server.local.newBusAllocators;

		MIDIClient.init;//initialise SC midi
		MIDIIn.connectAll;//connect


		ProxySynthDef.sampleAccurate = true;

		//initialise proxy space
		proxySpace.clear;//clear if allready exists
		proxySpace = ProxySpace.new(Server.local);//make new proxy
		proxySpace.makeTempoClock(2);//setup tempoclock
		proxySpace.quant = 4;//setup tempoclock
		proxySpace.clock.permanent = false;

		ProxySynthDef.sampleAccurate = true;

		masterServerGroup = Group.new(Server.local);

		colors = (	\prepare: 8336384, \normal: 4868682, \automate: 2129688);

		//reset database
		groups = [];
		dataOutputs = [];
		ctrlrs = [];
		midiCtrlrs = [];

		visualLatency = 0;
		visualPatternLatency = 0.01;

		dataOutRate = 60;

		replyIDCount = 0;

		tempoMin = 10;
		tempoMax = 160;
		tempo = 120;

		masterFXdeferTime = 2;

		playStates = [];

		enablePresetSave = false;

		// imu = SCMimu.new();

		OSCdef(\fpsReroute,
			{
				arg msg;
				SCM.ctrlrs.do{
					arg ctrlr;
					ctrlr.sendMsg("/fps", msg[1]);
				};
			}, "/touch/fps"
		);

		OSCdef(\tempoDiv,
			{
				arg msg;
				if(msg[1] > 0.5)
				{

					SCM.proxySpace.clock.play({
						tempo = tempo/2;
						tempo = tempo.clip(tempoMin,tempoMax);
						SCM.setTempo(tempo);
					}, 1);
				}
			}, "/masterMenu2/tempoDiv2/x"
		);

		OSCdef(\tempoMult,
			{
				arg msg;
				if(msg[1] > 0.5)
				{
					SCM.proxySpace.clock.play({
						tempo = tempo*2;
						tempo = tempo.clip(tempoMin,tempoMax);
						SCM.setTempo(tempo);
					}, 1);
				};
			}, "/masterMenu2/tempoMult2/x"
		);

		OSCdef(\tempo,
			{
				arg msg;
				SCM.setTempo(msg[1].linlin(0,1,tempoMin,tempoMax));
			}, "/scTempo"
		);

	}
	/**divideTempo{

	}*/

	*setupMatrix{
		matrix = SCMMatrix.new();

		SCM.ctrlrs.do{
			arg ctrlr;
			ctrlr.matrixMenu.scmMatrix = matrix;
		}
	}

	*masterFX{
		arg function, channels = 2;
		var input;

		input = {
			(groups.collect{arg group; group.getOutput}.sum);
		};

		masterGroup = SCMGroup.new(\masterFX, channels);
		masterGroup.serverGroup = Group.new(masterServerGroup, 'addToTail');
		masterGroup.linkProxy(\input, input);
		masterGroup.groupFX(function);
		{masterGroup.play;}.defer(masterFXdeferTime);
		masterGroup.listen;
		^masterGroup;
	}

	*setGroupPlayStates{
		arg scmGroupIndex, state;
		/*playStates[scmGroupIndex] = state;
		SCM.ctrlrs.do{
			arg ctrlr;
			ctrlr.sendMsg("/masterMenu/changeModule/light", (playStates * 0.6).extend(16,-0.5));
		};
		*/
	}

	*newGroup{
		arg name, channels = 2;
		var group;

		playStates = playStates.add(0);
		group = SCMGroup.new(name, channels, 4, groups.size);
		groups = groups.add(group);
		^group;
	}

	*getGroup{
		arg name;
		var result;
		//loop through controls and find the one with this name
		result = groups.select{ arg group; (group.name == name.asSymbol)};
		if(result.size > 0){result = result[0]} {result = nil};
		^result;
	}

	*setTempo{
		arg tempo_;
		tempo = tempo_;
		proxySpace.clock.tempo = tempo/60;

		SCM.ctrlrs.do{
			arg ctrlr;
			ctrlr.sendMsg("/scTempo", tempo.linlin(tempoMin,tempoMax,0,1));
		};

	}

	/** getCtrl{
	arg name;
	var result;
	//loop through controls and find the one with this name
	result = controls.select{ arg control; control.name == name; };
	if(result.size > 0){result = result[0]} {result = nil};
	^result;
	}*/

	*newTDDataOut{
		arg ip = "127.0.0.1";
		var dataOut;
		// dataOutputs.send
		dataOut = SCMTDDataOut.new(ip);
		dataOutputs = dataOutputs.add(dataOut);
	}

	*newOscCtrlr{
		arg ip, port, name = \notNamed;
		var return;
		return = SCMOSCCtrlr.new(ip, port, name);
		ctrlrs = ctrlrs.add(return);
		^return;
	}

	*newLemurCtrlr{
		arg ip, port, name = \notNamed, globalCtrlrIndex = 0;
		var return;
		return = SCMLemurCtrlr.new(ip, port, name, globalCtrlrIndex);
		ctrlrs = ctrlrs.add(return);
		^return;
	}

	*initLemurData{
		//after all group declarations...
		var names;
		names = groups.collect{arg group; group.name};
		groups.collect{arg group; ("'" + group.name + "'").replace(" ", "")};
		//send group names to lemur
		ctrlrs.do{
			arg ctrlr;
			ctrlr.sendMsg('/mainMenu/changeModule/names', names);
		};
		SCM.ctrlrs.do{
			arg ctrlr;
			ctrlr.sendMsg("/mainMenu/changeModule/light", (playStates * 0.6).extend(16,-0.5));
		};

	}

	*newTwisterCtrlr{
		var return;
		return = SCMTwister.new();
		midiCtrlrs = midiCtrlrs.add(return);
		^return;

	}

	*newJoystickCtrlr{
		var return;
		return = SCMJoystickCtrlr.new();
		midiCtrlrs = midiCtrlrs.add(return);
		^return;

	}


	*newAnVoMotors{
		anvoMotors = SCMAnVoMotors.new();
		^anvoMotors;

	}

	*clock{
		^SCM.proxySpace[\tempo];
	}

	*eventToTD{
		arg event, groupName, patternName;
		var evt = event.copy, stringEvent, sendAddr, delay, formatedEvent, instrument;//copy event, leave the original event unmodified

		//add patternEvent tag and instrument name to OSC address
		// sendAddr = ('/patternEvent/'++ groupName ++ '/' ++ evt[\instrument].asString);
		sendAddr = ('/patternEvent/'++ groupName ++ '/' ++ patternName ++ '/' ++ evt[\instrument].asString);

		instrument = evt[\instrument].asString;

		//optional osc address append, to diferentiate the same instrument in multiple patterns of the same group
		(evt[\osc_append] != nil).if{
			sendAddr = sendAddr ++ '/' ++ (evt[\osc_append].asString);//add string to ending
		};

		//store rest in key for TD
		evt[\isRest] = evt.isRest;
		//convert dur from Rest to Int if Rest
		evt[\isRest].if{
			var dur;
			dur = evt[\dur].value;
			//empty event on rest, don't want to update values
			evt = ();
			evt[\isRest] = true;
			evt[\dur] = dur;

		}
		{
			evt[\trigger] = 1;
		};
		//convert dur from beat to seconds
		evt[\dur] = evt[\dur] / proxySpace.clock.tempo;

		//format event into a python dictionnary
		// stringEvent = ~evtToPythonDictString.value(evt);

		//calculate delay
		if(evt[\timingOffset] != nil)
		{
			delay = evt[\timingOffset] * SCM.proxySpace.clock.tempo.reciprocal;
		}
		{
			delay = 0;
		};

		if(evt[\timingOffset].size > 0)
		{
			delay = 0;
		};

		if(evt[\lag] != nil)
		{
			if(evt[\lag].size < 1)
			{
				delay  =delay + evt[\lag];
			};
		};
		evt.removeAt(\group);//remove scale
		evt.removeAt(\fx_group);//remove scale
		evt.removeAt(\out);//remove scale
		evt.removeAt(\instrument);//remove scale
		formatedEvent = ~evtToPythonDictString.value(evt);


		// sendAddr.postln;
		//send to TD with a delay for visual sync
		dataOutputs.do{
			arg tdOut;
			{
				formatedEvent.keysValuesDo{
					arg addr, value;
					// addr.postln;
					// value.postln;
					if(value.isKindOf(Array))
					{
						value.do{
							arg val, i;
							var numberAddr;
							numberAddr = i.asString ++ "/" ++ addr;
							tdOut.chop.sendMsg(sendAddr ++ "/" ++ numberAddr, val);
							// tdOut.dat.sendMsg(sendAddr , numberAddr, val);
						};
					}
					{
						// tdOut.dat.sendMsg(sendAddr , addr, value);
						// tdOut.dat.sendMsg(sendAddr , addr, value);
						tdOut.chop.sendMsg(sendAddr ++ "/" ++ addr, value);
					};
					// if(evt[\isRest].not)


					// a.sendMsg("/test",addr,value);
					// name.postln;
				};

				if(evt.isRest.not)
				{
					var trigAddr;
					trigAddr= ('/trigger/'++ groupName ++ '/' ++ patternName ++ '/' ++ instrument);

					// tdOut.dat.sendMsg(sendAddr ++ "/trigger", 1);
					tdOut.dat.sendMsg(trigAddr, *[patternName ++ '_' ++ instrument,1]);
				};



			}.defer(max(Server.local.latency-(visualPatternLatency)+delay, 0));
		};

		//return original event for playing pattern
		^event;
	}

	*postSynthLib{
		~synthsLib1.do{arg item; ("\\" ++ item).postln;};
		"\nSynthDescLib.getLib(\\lib1).browse\n".postln;
		"~postArgsPbind.value(\\l1_12brass)\n".postln;

	}



	*setupServer{
		arg channels = 2;
		Server.local.options.memSize_(2.pow(20));
		Server.local.options.numWireBufs = 512;
		Server.local.options.maxSynthDefs  =2048;
		Server.local.options.hardwareBufferSize  =256;
		Server.local.options.numOutputBusChannels = channels;


		// ServerBoot.add({"hello".postln;}, Server.local);

		Server.local.waitForBoot(
			{
				var pathSCM;
				//try to find path of SCM to load resources
				Quarks.installed.do({arg quark; (quark.name == "scmapper").if{pathSCM = quark.localPath};});
				(pathSCM == nil).if{
					//post warning if not found
					20.do{"warning problem with SCM path on serberboot".postln};
				}
				{
					//otherwise load resources
					(pathSCM ++ "/resourcesSC/noteFX.scd").load;
					(pathSCM ++ "/resourcesSC/synthlib.scd").load;
					(pathSCM ++ "/resourcesSC/busPlayer.scd").load;
					(pathSCM ++ "/resourcesSC/evtToPyDict.scd").load;
				};
				//initialise SCM
				Server.local.latency = 0.05;
				SCM.init();

			}
		)

	}
}




SCMMidiCtrlr{

}







SCMTDDataOut{
	var < dat;
	var < chop;
	var ip;

	*new{
		arg ip;
		^super.new.init(ip);
	}

	init{
		arg tdIP;
		ip = tdIP;
		dat = NetAddr(ip, 10000);
		chop = NetAddr(ip, 10001);

	}


}

SCMAnVoMotors{
	var < motors;
	var motorIds;
	var motorPresets;
	var speed;

	*new{
		^super.new.init();
	}


	setPosition{
		arg motorIndex, angle;

		//to touchdesigner
		SCM.dataOutputs.do{
			arg tdOut;
			tdOut.chop.sendMsg(("/motorPosition/" ++ motorIndex).asSymbol, angle * -1);
		};
		SCM.dataOutputs.do{
			arg tdOut;
			tdOut.chop.sendMsg(("/motorSpeed").asSymbol, speed);
		};


		angle = (290*4*angle/360).asInt;
		angle = angle.snap(4);
		angle = angle * -1;

		motors[motorIndex].bend(0,angle);
		/*
		SCM.dataOutputs.do{
		arg tdOut;
		tdOut.chop.sendMsg(("/controls" ++ oscAddr).asSymbol, *rawValue);//append /controls
		};*/

	}

	setPreset{
		arg name;
		var list;
		list = motorPresets[name.asSymbol];
		if(list != nil)
		{
			list.do{arg angle, i; this.setPosition(i,angle)};
		};
	}

	setupPresets{
		motorPresets = ();
		motorPresets[\inout] = [-45, 45, 45, -45];
		motorPresets[\inoutReversed] = [-45+180, 45-180, 45-180, -45+180];
		motorPresets[\cross] = [45, -45, -45, 45];
		motorPresets[\front] = [0, 0, 0, 0];
		motorPresets[\back] = [180, -180, 180, -180];
		motorPresets[\diag1] = [45, 45, 45, 45];
		motorPresets[\diag2] = [-45, -45, -45, -45];
		motorPresets[\arrow1] = [-45, 45, -45, 45];
		motorPresets[\arrow2] = [-45, 45, -45, 45] * -1;
	}

	setupPresetListener{
		arg addr, presetName;
		OSCdef(
			("anvoOsc" ++ presetName).asSymbol,
			{
				arg msg;
				if(msg[1] > 0.5)
				{
					this.setPreset(presetName);
				}
			}, addr
		);

		OSCdef(
			"/menu3/motorspeed/x",
			{
				arg msg;
				this.setSpeed(msg[1].clip(0,1));
				SCM.ctrlrs.do{
					arg ctrlr;
					ctrlr.sendMsg( "/menu3/motorspeed/x", msg[1])//for midi if a param is mapped, store relation path->encoder/button
				};
			}, "/menu3/motorspeed/x"
		);
	}

	setSpeed{
		arg speed_;
		speed = speed_;
		// speed
		motors.do{arg motor; motor.control(0,60,speed.linlin(0,1,0,127))}
	}

	setupListeners{
		this.setupPresetListener('/motorsFront/x', \front);
		this.setupPresetListener('/motorsBack/x', \back);
		this.setupPresetListener('/motorsCross/x', \cross);
		this.setupPresetListener('/motorsInout/x', \inout);
		this.setupPresetListener('/motorsInoutRev/x', \inoutReversed);
		this.setupPresetListener('/diag1/x', \diag1);
		this.setupPresetListener('/diag2/x', \diag2);
		this.setupPresetListener('/arrow1/x', \arrow1);
		this.setupPresetListener('/arrow2/x', \arrow2);
	}

	init{
		// motorIds = [920339066,2050449709, -1417981912, -1480817946];
		motorIds = [-191674517,1983945126, -1529968077, 1608330683];
		speed = 1;
		motorIds.do{
			arg id;
			MIDIClient.destinations.do{
				arg destination, i;
				if(id == destination.uid)
				{
					motors = motors.add({MIDIOut.new(i, id)}.try{"failed to connect to midiout".postln;nil});
				}
			}
		};

		motors.do{arg motor;motor.latency = 0;};


		this.setupPresets();
		this.setupListeners();

	}



}


SCMJoystickCtrlr{
	var < midiout;
	var uid;
	*new{
		^super.new.init();
	}

	init{
		uid = -1328798234;


		MIDIClient.destinations.do{
			arg destination, i;
			if(uid == destination.uid)
			{
				midiout = {MIDIOut.new(i, uid)}.try{"failed to connect to midiout".postln;nil};
				if(midiout != nil)
				{
					midiout.latency = 0;
				}
			}
		};

		//initialise controller to white
		8.do{arg i; midiout.control(0,i,0);};

		MIDIFunc.cc(
			{
				arg midiValue;
				if(midiValue > 64)
				{
					SCM.ctrlrs[0].jumpGroup;
				};

				midiout.control(chan:0,ctlNum:6,val:midiValue);


		},6, 0);

		MIDIFunc.cc(
			{
				arg midiValue;
				if(midiValue > 64)
				{
					SCM.ctrlrs[1].jumpGroup;
				};

				midiout.control(chan:0,ctlNum:7,val:midiValue);


		},7, 0);


		//left side
		3.do{
			arg i;
			MIDIFunc.cc(
				{
					arg midiValue;
					var val;
					val = (midiValue > 64).asInt;

					if(SCM.ctrlrs[0].groupReference != nil)
					{
						SCM.ctrlrs[0].groupReference.midiIn(i,val);
					};

					midiout.control(chan:0,ctlNum:i,val:midiValue );
			},i, 0);
		};

		6.do{
			arg midiCC;
			//joysticks midi in to master fx
			MIDIFunc.cc(
				{
					arg midiValue;
					var associatedCtrlrIndex;
					SCM.masterGroup.midiIn(midiCC,midiValue.linlin(0,125,-1,1).excess(0.024));
			},midiCC+8, 0);

			MIDIFunc.cc(
				{
					arg midiValue;
					var associatedCtrlrIndex;
					SCM.masterGroup.midiIn(midiCC+6,midiValue.linlin(0,125,-1,1).excess(0.024));
			},midiCC+8+6, 0);
		};

		//right side
		3.do{
			arg i;
			MIDIFunc.cc(
				{
					arg midiValue;
					var val;
					val = (midiValue > 64).asInt;

					if(SCM.ctrlrs[1].groupReference != nil)
					{
						SCM.ctrlrs[1].groupReference.midiIn(i,val);
					};

					midiout.control(chan:0,ctlNum:i+3,val:midiValue );
			},i+3, 0);
		};
	}

}


SCMTwister{
	var < midiout;

	*new{
		^super.new.init();
	}

	init{
		{midiout = MIDIOut.newByName("Midi Fighter Twister", "Midi Fighter Twister");}.try{"failed to connect to midiout".postln;};
		midiout.latency = 0;

	}


}



SCMimu{
	var < accBus;
	var < gyroBus;
	var < magBus;
	var imuCalculations;

	var < rollBus;
	var < pitchBus;
	var < yawBus;
	var < accelMapBus;

	// accel = In.kr(~imuBusAccel,3);
	// // accel[0].scope;
	// mag = In.kr(~imuBusMag,3);
	// gyro = In.kr(~imuBusGyro,3);

	*new{
		^super.new.init();
	}

	getRawMag{
		^In.kr(magBus, 3);
	}

	getRawGyro{
		^In.kr(gyroBus, 3);
	}

	getRawAcc{
		^In.kr(accBus, 3);
	}

	getRoll{
		^In.kr(rollBus,1);
	}

	getPitch{
		^In.kr(pitchBus,1);
	}

	getYaw{
		^In.kr(yawBus,1);
	}

	getAccelMap{
		^In.kr(accelMapBus,1);
	}

	init{
		accBus = Bus.control(Server.local, 3);
		gyroBus = Bus.control(Server.local, 3);
		magBus = Bus.control(Server.local, 3);

		rollBus = Bus.control(Server.local, 1);
		pitchBus = Bus.control(Server.local, 1);
		yawBus = Bus.control(Server.local, 1);
		accelMapBus = Bus.control(Server.local, 1);


		/*3.do{
		arg count;
		this.addBendResponder(count, -60, 60, {arg value; accBus.setAt(count, value)} );
		this.addBendResponder(count+3, -180, 180, {arg value; magBus.setAt(count, value)} );
		this.addBendResponder(count+6, -10, 10, {arg value; gyroBus.setAt(count, value)} );
		};*/

		imuCalculations = {
			var roll, pitchA, yaw, accelMap, accel, mag, gyro;
			accel = In.kr(accBus,3);
			mag = In.kr(magBus,3);
			gyro = In.kr(gyroBus,3);

			roll = atan2(accel[1] , accel[2]) * 57.3;//entre 15 et 100
			roll = Sanitize.kr(roll).linlin(15,100,0,1).clip(0,1);
			pitchA = atan2((-1*accel[0]) , ((accel[1]  * accel[1] ) + (accel[2]*accel[2]) )).sqrt * 57.3; // entre -60 et 60
			pitchA = Sanitize.kr(pitchA).linlin(-60,60,0,1).clip(0,1);
			accelMap = Sanitize.kr(accel[0].linlin(-1,1,-1,1)).excess(0.1).pow(2).lag(0.05) ;
			yaw = Sweep.ar(0,Sanitize.kr(gyro[1])*2);

			Out.kr(rollBus, roll);
			Out.kr(pitchBus, pitchA);
			Out.kr(accelMapBus, accelMap);
			Out.kr(yawBus, yaw);
			// // pitchA.scope;
			//

			0;
			// accelMap.scope;

		};//.play();
	}

	addBendResponder{
		arg channel, rangeMin, rangeMax, function;
		MIDIFunc.bend(
			{
				arg val;
				var thisTime, range = 60;
				val = val.linlin(0,2**14, rangeMin, rangeMax).round(0.01);
				function.value(val);
			},channel
		);
	}

}