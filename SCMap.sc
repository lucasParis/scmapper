
SCM {
	classvar <proxySpace;

	classvar tempo_;
	classvar <> tempoMin;
	classvar <> tempoMax;

	//list of things
	classvar <> groups;
	classvar <ctrlrs;
	classvar <midiCtrlrs;
	classvar <dataOutputs;// to touchdesigner, but could be other?


	classvar < masterServerGroup;

	//dataoutput
	classvar <> visualLatency;
	classvar <> replyIDCount;
	classvar <dataOutRate;

	//master fx
	classvar <> masterFXdeferTime;//for the buggy play not working
	classvar < masterGroup;//hold SCMGroup for masterFX

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

		//reset database
		groups = [];

		visualLatency = 0;

		dataOutRate = 60;

		replyIDCount = 0;

		tempoMin = 20;
		tempoMax = 200;

		masterFXdeferTime = 2;

		OSCdef(\fpsReroute,
			{
				arg msg;
				SCM.ctrlrs.do{
					arg ctrlr;
					ctrlr.set("/fps", msg[1]);
				};
			}, "/touch/fps"
		);

		OSCdef(\tempo,
			{
				arg msg;
				SCM.setTempo(msg[1].linlin(0,1,tempoMin,tempoMax));
			}, "/scTempo"
		);

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

	*newGroup{
		arg name, channels = 2;
		var group;
		group = SCMGroup.new(name, channels);
		groups = groups.add(group);
		^group;
	}

	*setTempo{
		arg tempo;
		tempo_ = tempo;
		proxySpace.clock.tempo = tempo_/60;

		SCM.ctrlrs.do{
					arg ctrlr;
			ctrlr.set("/scTempo", tempo_.linlin(tempoMin,tempoMax,0,1));
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

	*newLemurCtrlr{
		arg ip, port, name = \notNamed;
		var return;
		return = SCMLemurCtrlr.new(ip, port, name);
		ctrlrs = ctrlrs.add(return);
		^return;
	}

	*newTwisterCtrlr{
		var return;
		return = SCMTwister.new();
		midiCtrlrs = midiCtrlrs.add(return);
		^return;

	}

	*clock{
		^SCM.proxySpace[\tempo];
	}

	*eventToTD{
		arg event, groupName, patternName;
		var evt = event.copy, stringEvent, sendAddr, delay;//copy event, leave the original event unmodified

		//add patternEvent tag and instrument name to OSC address
		// sendAddr = ('/patternEvent/'++ groupName ++ '/' ++ evt[\instrument].asString);
		sendAddr = ('/patternEvent/'++ groupName ++ '/' ++ patternName ++ '/' ++ evt[\instrument].asString);

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

		//calculate delay
		delay = evt[\timingOffset] * SCM.proxySpace.clock.tempo.reciprocal;

		if(evt[\timingOffset].size > 0)
		{
			delay = 0;
		};

		if(evt[\lag].size < 1)
		{
			delay  =delay + evt[\lag];
		};


		// sendAddr.postln;
		//send to TD with a delay for visual sync
		dataOutputs.do{
			arg tdOut;
			{
				tdOut.dat.sendMsg(sendAddr , *["stringEvent", stringEvent.asSymbol]);
			}.defer(max(Server.local.latency-(visualLatency)+delay, 0));
		};

		//return original event for playing pattern
		^event;
	}

	*postSynthLib{
		~synthsLib1.do{arg item; ("\\" ++ item).postln;};
	}

	*setupServer{
		arg channels = 2;
		Server.local.options.memSize_(2.pow(20));
		Server.local.options.numWireBufs = 512;
		Server.local.options.maxSynthDefs  =2048;
		Server.local.options.numOutputBusChannels = channels;


		ServerBoot.add({"hello".postln;}, Server.local);

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
				SCM.init();

			}
		)

	}
}



SCMLemurCtrlr{
	var < netAddr;
	var name;

	*new{
		arg ip, port, name;
		^super.new.init(ip, port, name);
	}

	init{
		arg ip, port, name_;
		name = name_;
		netAddr = NetAddr(ip, port);
	}
	set{
		arg path, value;
		netAddr.sendMsg(path, *value);
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