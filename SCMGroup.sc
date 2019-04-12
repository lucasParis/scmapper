SCMGroup {
	var <name;
	var <controls;

	var <patterns;
	var <>proxies;

	var <> serverGroup;

	var oscAddrPrefix;
	var oscAddrMenu;
	var < isPlaying;

	var <> fxSCMProxy;

	var < channels;

	var < assignedIDs;

	*new{
		arg groupName, channels = 2;
		^super.new.init(groupName, channels);
	}

	init{
		arg groupName, channels_;
		name = groupName;

		//setup array to hold controls, arrays and proxies
		controls = [];
		patterns = [];
		proxies = [];

		isPlaying = false;

		//setupOscAddresses
		oscAddrPrefix = ("/" ++ name).asSymbol;
		oscAddrMenu = (oscAddrPrefix ++ "/menu").asSymbol;

		//setup OSC mappings
		this.setupOscListeners();

		//send default values
		this.updateMenuFeedback('/play/x', 0);

		//setup group
		serverGroup = Group.new(SCM.masterServerGroup, 'addBefore');

		channels = channels_;
	}

	newCtrl{
		arg name, defaultValue = 0, postFix = "/x"; // subGroup = \pattern1 / nil
		var ctrl;

		//new ctrl
		ctrl = SCMCtrl.new(name, defaultValue, postFix, this);
		// add control to this group
		controls = controls.add(ctrl);
		^ctrl;//return
	}

	getCtrl{
		arg name, postFix = "/x";
		var result;
		//loop through controls and find the one with this name
		result = controls.select{ arg control; (control.name == name.asSymbol) && (control.postFix == postFix.asSymbol); };
		if(result.size > 0){result = result[0]} {result = nil};
		^result;
	}

	//add a pattern to this group
	linkPattern{
		arg patternName, pattern, manualMode = false, independentPlay = false;
		var pat;
		//new pattern
		pat = SCMPattern.new(patternName, pattern, this, channels, manualMode, independentPlay);
		// add pattern to this group
		patterns = patterns.add(pat);
		^pat;//return
	}

	//add a proxy to this group
	linkProxy{
		arg proxyName, function, audioIn = nil;
		var proxy;
		//new proxy
		proxy = SCMProxy.new(proxyName, function, this, audioIn,  channels: channels);
		// proxy.stop;
		//add proxy to this group
		proxies = proxies.add(proxy);
		^proxy;//return
	}

	groupFX{
		arg function = {arg in; in;};
		var proxy, proxyName, input;
		proxyName = (name ++ "groupFX").asSymbol;

		//new proxy with audio input
		input = {
			(patterns.reject(_.hasFX).collect(_.getOutput()).sum) + (proxies.collect(_.getOutput()).sum)

		};
		proxy = SCMProxy.new(proxyName, function, this, input, channels);
		// proxy.stop;

		//add SCMProxy after every generator of this group in server hierachy
		proxy.serverGroup = Group.new(serverGroup, 'addToTail');

		//add proxy to this group
		proxies = proxies.add(proxy);
		fxSCMProxy = proxies.last();
		^proxy;//return
	}

	getOutput{
		// channels.postln;
		^fxSCMProxy.getOutput;
	}

	newIDOverlap{
		arg  poly, overlaps, instrument;
		var count, ids;
		//calculate needed ids for voices * overlap
		count  = poly * overlaps;

		//get ids from scmapper's newID method
		ids = this.newID(count, instrument);

		//return a Pseq with values clumped for overlap (iteration technique in TD) - iterativeOverlap
		^Pseq(ids.clump(poly), inf);
	}

	newID{
		arg count, instrument;
		var assignedID;

		assignedID = [];

		//count is the number of IDs asked for and set in data structure
		count.do{
			SCM.replyIDCount = SCM.replyIDCount+1;//increment ID counter/allocator
			assignedIDs = assignedIDs.add(SCM.replyIDCount);// append to array of group's ids

			assignedID = assignedID.add(SCM.replyIDCount);// add to local array
		};

		//if synthdef is setup to reroute OSC replies
		SynthDescLib.global[instrument].metadata.includesKey(\oscReplies).if
		{
			// loop through the synthdef's osc reply addresses
			SynthDescLib.global[instrument].metadata[\oscReplies].do
			{
				arg addr;

				//OSC callback for the replies (rerouting to touch), based on replyids stored in database
				OSCdef(
					(name ++ addr).asSymbol, //osccallback name with group
					{
						arg msg;
						var values, replyID, idIndex, addrOut;
						replyID = msg[2];//get the replyID in the osc message
						idIndex = assignedIDs.find([replyID]);//search for the replyID in the database
						(idIndex != nil).if // if replyID is indexed in database
						{
							//get signal value(s) from reply
							values = msg[3..];
							//rename & prepare addr to touch
							addrOut = addr.replace("/" ++ instrument.asString, "");//remove instrument from address
							addrOut= "/" ++ name.asString ++ "/" ++ instrument.asString ++ "/" ++ idIndex.asString ++ addrOut;// format address with group/instrmnt/indx/par

							//send values
							// touchdesignerCHOP.sendMsg(addrOut, *values);

							SCM.dataOutputs.do{
								arg tdOut;
								tdOut.chop.sendMsg(addrOut, *values);
							};
						}
					},
					addr;
				);
			};
		};

		^assignedID;//return assigned id
	}

	printOn { | stream |
		stream << "SCMGroup (" << name << ")";
	}

	setupOscListeners{
		var playAddr;
		//PLAY / STOP
		playAddr = (oscAddrMenu ++ "/play/x").asSymbol;
		OSCdef(
			playAddr,
			{
				arg msg;
				if(msg[1] >0.5)
				{
					this.play();
				}
				{
					this.stop();
				}
			},
			playAddr
		);
	}

	play{
		if(isPlaying.not)
		{
			isPlaying = true;
			//play patterns
			patterns.do{arg pattern; pattern.play; };
			//play proxies
			proxies.do{arg proxy; proxy.play; };
			//play controls (busMappers)
			controls.do{arg control; control.play; };
			//send OSC feedback
			this.updateMenuFeedback('/play/x', 1, quantize:true);
		}

	}

	stop{
		if(isPlaying)
		{
			isPlaying = false;
			//stop patterns
			patterns.do{arg pattern; pattern.stop; };
			//stop proxies
			proxies.do{arg proxy; proxy.stop; };
			//stop controls (busMappers)
			controls.do{arg control; control.stop; };
			//send OSC feedback
			this.updateMenuFeedback('/play/x', 0);
		}
	}

	updateMenuFeedback{
		arg menuPath, value, quantize = false;
		var path;
		path = (oscAddrMenu ++ menuPath).asSymbol;
		//update osc outputs
		SCM.ctrlrs.do{
			arg ctrlr;
			ctrlr.set(path, value);//for midi if a param is mapped, store relation path->encoder/button
		};
		//update touchdesigner outputs
		SCM.dataOutputs.do{
			arg tdOut;
			if(quantize == true,
				{
					SCM.proxySpace.clock.playNextBar({ tdOut.chop.sendMsg(("/menu" ++ path).asSymbol, *value) })//call reset on next bar
				},
				{
					tdOut.chop.sendMsg(("/menu" ++ path).asSymbol, *value);//append /controls
				}
			);
		};

	}

	sendTrigger{
		arg nameSig, signal;
		var address;

		//prepare osc output address
		address = "/trigger/" + name.asString++ '/' ++ nameSig;
		address = address.replace(" ", "");//remove empty spaces

		// osc listener for sendReply
		OSCdef(
			address.asSymbol,
			{
				arg msg;
				var values;

				values = msg[3..];//get the signal values
				//send to touch, with sync delay
				SCM.dataOutputs.do{
					arg tdOut;
					{"many?".postln;tdOut.dat.sendMsg(address, *values)}.defer(SCM.visualLatency);
				}

		}, address);//oscdef addr for signal reply

		//create sendreply
		SendReply.ar(signal, address, 1);

	}

	sendSignal{
		arg nameSig, signal;

		var address;

		//prepare osc output address
		address = "/" + name.asString++ '/' ++ "proxy" ++ '/' ++ nameSig;
		address = address.replace(" ", "");//remove empty spaces

		// osc listener for sendReply
		OSCdef(
			address.asSymbol,
			{
				arg msg;
				var values;
				values = msg[3..];//get the signal values
				//send to touch, with sync delay
				SCM.dataOutputs.do{
					arg tdOut;
					{tdOut.chop.sendMsg(address, *values)}.defer(SCM.visualLatency);
				}

		}, address);//oscdef addr for signal reply

		//create sendreply
		SendReply.kr(Impulse.kr(SCM.dataOutRate), address, signal, -1);

	}

	listen{
		this.fxSCMProxy.outputBus.play;
	}
}