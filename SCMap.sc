
SCM {
	classvar <proxySpace;
	classvar tempo_;
	classvar <groups;
	classvar <ctrlrs;
	classvar <dataOutputs;// to touchdesigner, but could be other?

	classvar masterBus;
	classvar masterServerGroup;

	classvar <> groups;


	*init{
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

		//reset database
		groups = [];

	}

	masterFX{
		arg function;
		var proxy, proxyName, input;
		proxyName = \masterFX;

		//new proxy with audio input
		// groups.do
		// input = {
		// patterns.reject(_.hasFX).collect(_.patternOut()).sum + proxies.collect(_.getNodeProxy()).sum

		// };
		// proxy = SCMProxy.new(proxyName, function, this, input);


		// //add SCMProxy after exvery generator of this group in server hierachy
		// proxy.serverGroup = Group.new(serverGroup, 'addToTail');
		//
		// //disable output for all generators
		// patterns.do(_.sendToOutput = false);
		// proxies.do(_.sendToOutput = false);
		//
		// //add proxy to parent group
		// proxies = proxies.add(proxy);
		// ^proxy;//return
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

	*setupServer{
		Server.local.options.memSize_(2.pow(20));
		Server.local.options.numWireBufs = 512;
		Server.local.options.maxSynthDefs  =2048;

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