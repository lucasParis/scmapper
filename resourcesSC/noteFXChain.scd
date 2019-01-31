Event.addEventType(\noteFxChain, {
	|server|
	var bus, group, fxEvent, fxSynth, maxSize;
	// possible options:
	// - synths parrallel/summed (currently parrallel into single fx, parrallel each has it's own fx, summed would go into single fx, with summed durations for dur)


	// currentEnvironment.postln;

	// ~fx.isKindOf(Array).postln;

	if(~fx != nil, { // add condition for when fx is an array
		if(~fx != \none,
			{
				(~monoFX != nil).if{
					// only single bus -> monobus
				};

				maxSize = currentEnvironment.collectAs({arg item; item.size},Array).sort.last;
				// maxSize.postln;
				if((maxSize > 1) && (~monoFX != true))
				{bus = maxSize.collect{Bus.audio(s,2)};}//multichannel bus
				{bus = [Bus.audio(s,2);]};

				~type = \note;

				fxEvent = ();
				fxEvent[\dur] = currentEnvironment[\dur];
				currentEnvironment.keysValuesDo{//only add keys that have prefix fx
					arg key, value;
					(key.asString.find("fx_") == 0).if{
						key = key.asString.replace("fx_","").asSymbol;
						fxEvent[key] = value;
						// fxEvent.postln;

					};
				};
				/*					(fxEvent[\addBefore] != nil).if
				{
				group = Group.new(s,\addBefore);
				}
				{
				"new group".postln;*/
				group = Group.new(s);
				// };


				currentEnvironment[\group] = group;
				// currentEnvironment.postln;
				// fxEvent = currentEnvironment.copy;
				currentEnvironment[\addAction] = 0;


				// bus.postln;

				// (fxEvent[\legatoFX] != nil).if{fxEvent[\legato] = fxEvent[\legatoFX];};

				fxEvent[\instrument] = ~fx;
				fxEvent[\addAction] = 1;
				fxEvent[\in] = bus;
				fxEvent[\out] = ~out;

				// fxEvent.postln;

				// fxEvent.postln;
				fxEvent.play;


				fxSynth = [];

				fxEvent.id.do{
					arg id, i;
					// id.postln;
					// i.postln;
					// fxEvent[\bus]

					fxSynth = fxSynth.add(Synth.basicNew(fxEvent.instrument, fxEvent.server, id););
					fxSynth.last.register(true);
					// fxSynth.last.postln;
					(~monoFX == true).if{//monoFx only has one bus
						fxSynth.last.onFree(
							{
								// "freemono".postln;
								bus[0].free;
								group.free;
							}
						);
					}
					{
						fxSynth.last.onFree(
							{
								// ~monoFX.postln;
								bus[i].free;
								// group.free;
								maxSize = maxSize-1;
								(maxSize < 1).if{group.free;};
							}
						);
					}
				};
				// fxSynth.postln;
				/*}
				{
				"not array".postln;
				fxSynth = Synth.basicNew(fxEvent.instrument, fxEvent.server, fxEvent.nodeID);
				fxSynth.register(true);
				fxSynth.onFree({{bus.free}; group.free;});
				};*/



				/*fxSynth = Synth.basicNew(fxEvent.instrument, fxEvent.server, fxEvent.nodeID);
				fxSynth.register(true);
				fxSynth.onFree({bus[0].free; group.free;});
				*/
				currentEnvironment[\out] = bus;
				currentEnvironment.play;
				// currentEnvironment.postln;

			},
			{

				~type = \note;
				currentEnvironment.play;
				// currentEnvironment.postln;

			}
		);
	});
});