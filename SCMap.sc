
SCM {
	classvar <proxySpace;
	classvar tempo_;
	classvar <groups;
	classvar <ctrlrs;
	classvar <dataOutputs;// to touchdesigner, but could be other?

	classvar masterBus;
	classvar < serverGroup;

	classvar <> groups;

	classvar < masterProxy;
	classvar <> controls;


	*init{
		"initialising SCM".postln;
		if(NetAddr.langPort != 57120)
		{
			var errorMessage = "_____ WARNING _____ langPort not 57120, reboot interpreter please ________";
			20.do{errorMessage.postln};
		};

		//initialise proxy space
		proxySpace.clear;//clear if allready exists
		proxySpace = ProxySpace.new(Server.local);//make new proxy
		proxySpace.makeTempoClock(2);//setup tempoclock
		proxySpace.quant = 4;//setup tempoclock


		serverGroup = Group.new(Server.local);

		//reset database
		groups = [];

	}

	*newCtrl{
		arg name, defaultValue = 0, postFix = "/x";
		var ctrl;

		//new ctrl
		ctrl = SCMCtrl.new(name, defaultValue, postFix, this);
		// add control to this group
		this.controls = this.controls.add(ctrl);
		^ctrl;//return
	}

	*masterFX{
		arg function;
		var proxy, proxyName, input, groupAudios;
		proxyName = \masterFX;

		//proxy inputs
		input = {
			groups.collect{arg group; group.getOutput};
		};

		masterProxy = SCMProxy.new(proxyName, function, this, input);
		masterProxy.serverGroup = Group.new(serverGroup, 'addToTail');

		this.masterProxy.play;
		this.masterProxy.outputBus.play;
	}

	*newGroup{
		arg name;
		var group;
		group = SCMGroup.new(name);
		groups = groups.add(group);
		^group;
	}

	*setTempo{
		arg tempo;
		tempo_ = tempo;
		proxySpace.clock.tempo = tempo_/60;

	}

	*newTDDataOut{
		var dataOut;
		// dataOutputs.send
		// dataOut = SCMTDDataOut.new();
		// dataOutputs = dataOutputs.add(dataOut);
	}

	*newLemurCtrlr{
		arg ip, port, name = \notNamed;
		var return;
		return = SCMLemurCtrlr.new(ip, port, name);
		ctrlrs = ctrlrs.add(return);
		^return;
	}

	*clock{
		^SCM.proxySpace[\tempo];
	}

	*postSynthLib{
		~synthsLib1.do{arg item; ("\\" ++ item).postln;};
	}

	*setupServer{
		Server.local.options.memSize_(2.pow(20));
		Server.local.options.numWireBufs = 512;
		Server.local.options.maxSynthDefs  =2048;

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

}






