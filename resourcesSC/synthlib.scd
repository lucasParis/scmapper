// Template synthdefs
SynthDef.new(\l1fx_lala,{
	arg amp = 1, out = 0, in = 0, gate = 1, dry=0, replyID = -1,  seed = 5673;
	var input, fx, env, verb, quitAfter;
	RandID.ir(id: 0);
	RandSeed.ir(trig: 1, seed: seed);

	env = Env.adsr(0.0, 0.3,1,0.05).ar(0, gate);
	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	// SendReply.kr(Impulse.kr(60), "/instrument/param", env, replyID);
	input = In.ar(in,2);
	fx = CheapVerb.ar(input, 0.4,20);

	Out.ar(out, ((dry*input) + fx)*env*amp);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/instrument/param"])
).add;

SynthDef.new(\l1_lala,{
	arg freq, amp = 1, out = 0, gate = 1, replyID = -1;
	var src, env, quitAfter;
	env = Env.adsr(0.01, 0.3,1,0.05).ar(0, gate);

	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	// SendReply.kr(Impulse.kr(60), "/instrument/param", env, replyID);

	src = SinOsc.ar(freq, mul:amp*env);

	OffsetOut.ar(out, src!2);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/instrument/param"])
).add;


// utility functions
~postArgs = {
	arg synthName;
	var path, f, descSynth;
	path = ('~/' ++ '_presetPaste').standardizePath;
	f = File(path,"w");

	descSynth = SynthDescLib.at(synthName).controls.collect{arg item; ["\n" ++ "\\"++ item.name.asSymbol, item.defaultValue.round(0.0000001)]}.flatten.asString.replace("[").replace("]");

	f.write(descSynth);
	f.close;

	("pbcopy < "++path).asString.unixCmd;
};

~postArgsPbind = {
	arg synthName;
	var path, f, descSynth, isFx;
	path = ('~/' ++ '_presetPaste').standardizePath;
	f = File(path,"w");

	descSynth = SynthDescLib.at(synthName).controls;
	isFx = synthName.asString.contains("fx_");


	descSynth = descSynth.select{arg item; (item.name.asSymbol != 'gate') && (item.name.asSymbol != 'out')&& (item.name.asSymbol != 'in')&&(item.name.asSymbol != 'freq') };

	descSynth  = descSynth.collect{
		arg item;
		item.name.asString.postln;
		isFx.if{
			["\n" ++ "\\fx_"++ item.name.asString, item.defaultValue.round(0.0000001)];
		}
		{
			["\n" ++ "\\"++ item.name.asString, item.defaultValue.round(0.0000001)];
		};
	};
	descSynth = descSynth.flatten.asString.replace("[").replace("]");

	isFx.not.if{
		descSynth = descSynth.insert(0,"\\instrument, '" ++ synthName.asString ++ "',\n\\dur, 1,\n\\octave, 4,\n\\degree, 0,\n\\legato, 1,\n//////synth parameters");

	}
	{
		descSynth = descSynth.insert(0,"\\type, \\noteFx,\n\\monoFX, false,\n\\fx,  '" ++ synthName.asString ++ "',\n\\fx_legato, 1,\n//////fx parameters");
	};
	f.write(descSynth);
	f.close;
	descSynth.postln;

	("pbcopy < "++path).asString.unixCmd;
};

~postArgsEvent = {
	arg synthName;
	var path, f, descSynth;
	path = ('~/' ++ '_presetPaste').standardizePath;
	f = File(path,"w");

	// a = a
	descSynth = SynthDescLib.at(synthName).controls;
	descSynth = descSynth.select{arg item; (item.name.asSymbol != 'gate') && (item.name.asSymbol != 'out') };
	descSynth = descSynth.collect{arg item; [item.name.asString.asSymbol, item.defaultValue.round(0.0000001)]}.flatten.as(Event);//.removeItems("t_")
	descSynth[\dur] = 1;
	descSynth = descSynth.asString();

	descSynth = descSynth.removeItems("\n");
	descSynth = descSynth ++ ".play;";
	descSynth = descSynth.insert(1," \'instrument': '" ++ synthName.asString ++ "',");
	f.write(descSynth);
	f.close;
	("pbcopy < "++path).asString.unixCmd;

};

~randomMetadataPlay = {
	arg synthname, freq= 100, dur=0.3;
	var metadata, evt;
	metadata = SynthDescLib.global[synthname].metadata[\specs].copy;
	// metadata.postln;
	metadata = metadata.keysValuesChange{arg key, value; key.postln; value.map(rrand(0,1.0))};
	evt = (instrument: synthname, freq:freq, dur:dur, amp: 1);
	evt = evt.merge(metadata);
	evt.play;
};


// Synthdefs
//split into files?
SynthDef.new(\l1_fxDown,{
	arg freq, amp = 1, len = 5, curve = -4, dirt = 1, out = 0, gate = 1;
	var src, env, envPitch, envMod, dirtSig;
	// env = Env.adsr(0, len ,0,0.05,curve:5);
	env = Env.linen(0,0,len,1,5);
	env = EnvGen.ar(env, 1, doneAction:2);
	envPitch = Env.adsr(0, len*0.5,0,0.05);
	envPitch = EnvGen.ar(envPitch, 1);

	envMod = Env.adsr(len, 0.03,0,0.05,curve: curve);
	envMod = EnvGen.ar(envMod, 1);

	src = SinOsc.ar(freq + envPitch.linlin(0,1,0,freq*3), mul:amp*env);
	src = src + AllpassL.ar(src, 0.2, 0.1*envMod,len*2);

	src = LeakDC.ar(src);

	dirtSig = (src.softclip*10).tanh;

	dirtSig = HPF.ar(dirtSig, freq * 2);
	dirtSig = HPF.ar(dirtSig, freq * 2);
	dirtSig = HPF.ar(dirtSig, freq * 3);
	dirtSig = dirtSig * dirt;

	src = src + dirtSig;
	OffsetOut.ar(out, (src*env)!2);
},
metadata:(specs:(len:ControlSpec.new(0.1, 20), curve:ControlSpec.new(-10,4),dirt:ControlSpec.new(0,1), ))).add;
// )
// Synth.new(\l1_fxDown, [freq:43, len:4, dirt:1, curve:-4])

// Synth.new(\l1_fxDown, [freq:43, len:4, dirt:0, amp:1, curve:-1])

// SynthDescLib.global[\l1_fxDown].metadata[\specs]

// transient
// (
SynthDef.new(\l1_transient,{
	arg freq, amp = 1, smear = 0.2, smearSpeed = 1, transient = 3, len = 0.06, out = 0, gate = 1;
	var src, env, envPitch, envMod;
	// env = Env.adsr(0, len ,0,0.05,curve:0);
	env = Env.perc(0,len,curve:1);
	env = EnvGen.ar(env, doneAction:2);
	envPitch = Env.adsr(0, len*0.5,0,0.05);
	envPitch = EnvGen.ar(envPitch, 1);

	envMod = Env.adsr(len, 0.03,0,0.05);
	envMod = EnvGen.ar(envMod, 1);

	src = SinOsc.ar(freq + envPitch.linlin(0,1,0,freq*transient), mul:amp*env);
	src = src + AllpassL.ar(src, 0.2, LFSaw.ar(smearSpeed*len.reciprocal, pi).range(smear.linexp(0,1,0.002,0.2),0.001),0.1);

	OffsetOut.ar(out, (src*env*amp)!2);
},
metadata:(specs:(smear: ControlSpec(0,1), smearSpeed: ControlSpec(0.001,200,\exp), transient: ControlSpec(0,80,4), len: ControlSpec(0.001, 0.5,2))) ).add;
// )
// 0.06.reciprocal

// Synth.new(\l1_transient, [freq:20, len:0.3, dur:0.2, smear:0.5, amp:0.4, smearSpeed:0.2, transient:10])
// SynthDescLib.global[\l1_transient].metadata[\specs]

// (
// Pdef(\patt, Pbind(\instrument, \l1_transient, \freq, [1000, 200], \amp, 0.5, \len,[0.01, 0.02], \timingOffset, [-0.01,0], \transient, 10, \dur, 0.75, \smear, 1, \smearSpeed, Pwhite(0.2,3!2)));
// );
//
// Pdef(\patt2, Pbind(\instrument, \l1_transient, \freq, [2000, 500], \amp, Pseq([Rest(), 0.5],inf), \len,[0.01, 0.02]*0.5, \timingOffset, [-0.014,0], \transient, 10, \dur, 0.25, \smear, [0.1,1], \smearSpeed, Pwhite(0.2,3!2)));
// )
// )
// Pdef(\patt).play
// Pdef(\patt2).play

// --------- noisy chord
// (
SynthDef.new(\l1_noiseChord,{
	arg freq, amp = 1,ratio=1, decay = 2, out = 0, gate = 1, vibrato = 0.1, modAttack = 0.6;
	var src, env, envMod, f1, f2, bus;
	env = Env.adsr(0.05, decay,0.8,0.05);
	env = EnvGen.ar(env, gate, doneAction:2);

	envMod = Env.adsr(modAttack, 0.4,0,0.05);
	envMod = EnvGen.ar(envMod, gate);

	src = PMOsc.ar(freq,(freq*ratio) + SinOsc.ar(5,mul:freq*vibrato.linlin(0,1,0,0.1)),envMod.linlin(0,1,0.1,0.6),WhiteNoise.ar(0.2), mul:env);

	f1 = LPF.ar(src, freq * 2);
	f1 = f1 + HPF.ar((f1*10).tanh,(freq*16).clip(0,15000));

	f2 = HPF.ar(src, freq * 4, mul:4);
	// f2 = BPF.ar(f2, freq * 4 * LFPulse.ar(10).range(0.1,1), 4).excess(-30.dbamp);
	// f2 = HPF.ar(AllpassL.ar(f2, 0.2,(0.04 * f2).clip(0.005,0.1),0.02).softclip,freq*3, mul:-12.dbamp);
	f2 = AllpassL.ar(f2, 0.2,([0.01,0.02] * f2).clip([0.05,0.01],0.1),0.004).softclip;
	f2  = Splay.ar(f2, 0.8);

	bus = amp*((f2+(f1!2))).clip(-1,1);
	OffsetOut.ar(out, bus);
},
metadata:(specs:(ratio:ControlSpec(0.125,8), decay:ControlSpec(0,2), vibrato:ControlSpec(0,1), modAttack:ControlSpec(0,20)))
).add;
// )

// (\instrument: \l1_noiseChord,\freq: [100,200], \dur: 10, \vibrato:0.1, \ratio:0.5, \amp:0.5, \decay:0.8, \modAttack:10).play
// (\instrument: \l1_noiseChord,\freq: [100,200, 300, 400, 500], \dur: 1, \vibrato:1, \ratio:1, \amp:0.5).play
// (\instrument: \l1_noiseChord,\freq: ([0,4,7,11] + 48).midicps, \dur: 1, \vibrato:1, \ratio:1, \amp:0.5).play
// (\instrument: \l1_noiseChord,\freq: [80], \dur: 1, \vibrato:[0,1], \ratio:1, \amp:0.5).play
// SynthDescLib.global[\l1_noiseChord].metadata[\specs]
//
//
// (\instrument: \l1_noiseChord,\freq: [80, 160], \dur: 4, \vibrato:[0,1], \ratio:0.49, \amp:0.3).play

// Synth.new()
// (
// Pdef(\l1_noiseChord).quant = 4;
// ~middleNote = 3;
// Pdef(\l1_noiseChord, Pbind(\instrument, \l1_noiseChord,
// 	\base, Pn(Pseries(0,1,4)),
// 	\root, 0,
// 	\degree, (Pkey(\base) + Pfunc{[0,~middleNote,8]}).wrap(0,9),
// 	\octave, [3,5,5]-1,
// 	\legato, 0.75,
// 	\amp, Pwhite(0.37!3,0.5)*0.14,
// 	\dur, 2,
// 	// \vibrato, 0.2,
// 	\modAttack, Pn(Pseries(0,1,4)) * 0.6,
// ));
//
// // Pdef(\l1_noiseChord).play;
// )

// ~middleNote = 2;

// TempoClock.default.tempo = 120/60

// soft start swelling chord

// pad complex


// ---------- hat

// (


SynthDef(\l1_hatWN, {
	arg amp = 1, attack = 0, hold = 0.2, release = 0.1, hpf = 2000, out = 0, seed = 5673;
	var src, env;
	RandID.ir(id: 0);
	RandSeed.ir(trig: 1, seed: seed);
	env = Env.pairs([[0,0.00001], [attack,1], [attack+0.01,0.25], [attack+0.01+hold, 0.25], [attack+0.01+hold+release,0.0001]], [0,0,0,-2]);
	env = EnvGen.ar(env, 1, doneAction:2);
	src = WhiteNoise.ar(env*amp);
	src = HPF.ar(src, hpf);

	OffsetOut.ar(out, src!2);

},
metadata:(specs:(attack:ControlSpec(0,0.5), hold:ControlSpec(0,4), release:ControlSpec(0,4), hpf:ControlSpec(100,10000, \exp)))
).add;

// )

// Synth.new(\l1_hatWN, [seed:1000, hpf:200]);
// Synth.new(\l1_hatWN, [seed:1000, hpf:100, attack: 0, hold:0.05, release:0]);

// SynthDescLib.global[\l1_hatWN].metadata[\specs]


// ----------- hat 2

// (


SynthDef(\l1_hatWN2, {
	arg amp = 1, transient = 1, attack = 0, plateau = 0.25, hold = 0.2, release = 0.1, hpf = 2000,brf = 5000,brf2 = 8000, out = 0, seed = 5673;
	var src, env, envFade, noise;
	RandID.ir(id: 0);
	RandSeed.ir(trig: 1, seed: seed);
	env = Env.pairs([[0,0.00001], [attack,1], [attack+0.01,plateau], [attack+0.01+hold, plateau], [attack+0.01+hold+release,0.0001]], [0,0,0,-2]);
	env = EnvGen.ar(env, 1, doneAction:2);

	envFade = Env.perc(attack,0.01,1,0);

	envFade = EnvGen.ar(envFade, 1);
	noise = WhiteNoise.ar(env*amp);
	src = HPF.ar(noise, hpf);
	src = BRF.ar(src, brf,2);
	src = BRF.ar(src, brf2,2);

	src = SelectX.ar( envFade*transient, [src, noise]);

	OffsetOut.ar(out, src!2);

},
metadata:(specs:(transient:ControlSpec(0,1) ,attack: ControlSpec(0,0.5),plateau: ControlSpec(0,1),hold: ControlSpec(0,4),release:ControlSpec(0,4) ,hpf: ControlSpec(100,10000, \exp),brf: ControlSpec(200,10000, \exp),brf2: ControlSpec(200,10000, \exp),seed: ControlSpec(0,6000)))
).add;

// )

// Synth.new(\l1_hatWN2, [seed:1000, hpf:200]);
// Synth.new(\l1_hatWN2, [seed:1000, hpf:200, attack: 0, hold:0.6, release:0.1, plateau:0.5]);

// Pdef(\l1_hat2).quant = 4;
// Pdef(\l1_hat2, Pbind(\instrument, \l1_hatWN2, \amp, 1, \hpf, 200, \brf, 6000, \brf2, 2000, \amp,0.5, \hold, 0.02, \dur, 1));
// Pdef(\l1_hat2).play;


//get list of synth args


// sub lfo

// neuro stab lowpass and multi dist

//kick

//softkick

// warm saw filtered stab

// trap clump

// ambiance

// bass swelling

// (
// {
SynthDef.new(\l1_bassSwell,{
	arg freq, attack =0, amp = 1, pump = 1, moreTone = 1, pumpTime = 0.4, div = 1, fmIndex = 1, fmBeat = 0.1,modFilter = 20, decay1 = 0.2,decay2 = 1, out = 0, gate = 1, quitAfter, replyID = -1;
	var src, env, fEnv, pls, modOsc, plsFEnv, low, high, highEnv;
	env = Env.adsr(attack, 0.3,1,0.05);
	env = EnvGen.ar(env, gate, doneAction:0);


	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	SendReply.kr(Impulse.kr(60), "/l1_bassSwell/env", env, replyID);



	fEnv = Env.adsr(attack, decay1,0,0.05);
	fEnv = EnvGen.ar(fEnv, gate);

	plsFEnv = Env.adsr(attack, decay2,0,0.05,curve:-1);
	plsFEnv = EnvGen.ar(plsFEnv, gate);

	highEnv = Env.adsr(pumpTime, 0,1,0.05, curve:2);
	highEnv = EnvGen.ar(highEnv, gate);

	SendReply.kr(Impulse.kr(60), "/l1_bassSwell/fEnv", fEnv, replyID);
	SendReply.kr(Impulse.kr(60), "/l1_bassSwell/plsFEnv", plsFEnv, replyID);
	SendReply.kr(Impulse.kr(60), "/l1_bassSwell/highEnv", highEnv, replyID);


	modOsc = SawDPW.ar((-1*freq)+fmBeat, mul:env);
	src = SawDPW.ar(freq + (freq*modOsc*fmIndex), mul:env);
	pls = PulseDivider.ar(src,div);
	pls = BRF.ar(pls, plsFEnv.linexp(0,1, 400,2000),4);

	pls = Sanitize.ar(pls);
	src = DFM1.ar(src, (freq*2* fEnv.linlin(0,1,1,modFilter)) + (src*freq*1) , 0.9,1.1) + (pls * -12.dbamp);//+ (src*freq)//* fEnv.linlin(0,1,1,4)

	// low = BRF.ar(src, 4000, 4);
	// high = BPF.ar(src, 4000, 4);

	low = LPF.ar(src, freq*2);
	high = HPF.ar(src, freq*2);

	high = high + LPF.ar(WaveLoss.ar(high,1,4), freq*4);
	high = (high*highEnv).tanh.distort;


	// src = high;
	// src = low;
	src = SelectX.ar(pump, [src, low + high]);

	// src = ;
	src =SelectX.ar(moreTone, [src, src + BPF.ar(WaveLoss.ar(src,2,4), plsFEnv.linexp(0,1, 400,2000),rq:0.5, mul:1.5)]);

	src = amp*src;
	OffsetOut.ar(out, src!2);
},
metadata:(specs:( attack: ControlSpec(0,2,\exp), pump: ControlSpec(0,1), moreTone:ControlSpec(0,1), pumpTime:ControlSpec(0,2), div:ControlSpec(1,8,step:1), fmIndex:ControlSpec(0,100, 20), fmBeat:ControlSpec(0,7, 4), modFilter:ControlSpec(0,90), decay1:ControlSpec(0,10,4), decay2:ControlSpec(0,10,4)), oscReplies:["/l1_bassSwell/fEnv","/l1_bassSwell/plsFEnv","/l1_bassSwell/highEnv", "/l1_bassSwell/env"])).add;
// s.sync;
/*(instrument: \l1_bassSwell, dur: 1, freq:48.midicps, amp:1).play;*/
// }.fork;
// )


// SynthDescLib.global[\l1_bassSwell].metadata[\specs]

// (instrument: \l1_bassSwell, dur: 1, freq:36.midicps, amp:1, attack:0, pump:1, pumpTime:2, fmIndex:0, fmBeat:0, modFilter:90, decay1:0.2, decay2:0.1).play;
// ~randomMetadataPlay.value(\l1_bassSwell, 100, 2)

// (

// {
SynthDef(\l1_ambiance,{
	arg freq, amp = 1, out = 0, gate = 1;
	var src, env;

	// RandID.ir(id: 0);
	// RandSeed.kr(trig: Impulse.kr(100), seed:29993 + LFSaw.ar(0.5, mul:10));//noise osc
	// RandSeed.kr(trig: Impulse.kr(100), seed:29993 + LFSaw.ar(0.5, mul:10));//noise osc
	env = Env.adsr(0.01, 0.3,1,0.05);
	env = EnvGen.ar(env, 1, doneAction:2);

	src = BrownNoise.ar((amp!2)*env);
	src = LowShelf.ar(src, 300, 0.3,-4);
	src = Splay.ar(src,0.9);
	src = MidEQ.ar(src, LFNoise2.ar(0.1).range(300, 600),2,-10);
	src = MidEQ.ar(src, LFNoise2.ar(0.1).range(2000, 4000!2),2,-3);

	src = Splay.ar(src,0.9);
	// src = src + BPF.ar(src, 10.collect{Rand(300,5000)}.stutter(2), 0.1, 12.dbamp);
	src = src + BPF.ar(src, LFNoise1.ar(0.01).range(200!4, 1000).stutter(2), 0.05, 12.dbamp);
	src = BRF.ar(src, LFNoise1.ar(0.01).range(1000, 5000), 0.2);
	src = Splay.ar(src);

	Out.ar(out, src);
},
metadata:(specs: ( ) )
).add;

// s.sync;

/*Synth.new(\l1_ambiance, [amp:0.1])*/
// }.fork;
// )

// (
SynthDef.new(\l1_kick,{
	arg freq, attack = 0, amp = 1, out = 0, gate = 1, chirp = 0, envAmount = 20, timeScale = 1;
	var src, env, pitchEnv, attackenv;
	env = Env.adsr(attack, 0.15,-4.dbamp,0.1, curve:0);
	env = EnvGen.ar(env, gate, doneAction:2);

	pitchEnv = Env.xyc([[0,1,-8], [0.01,chirp.linlin(0,1,0.1,0.25),-1], [0.03, 0.05,0], [0.05,0,0]]);
	pitchEnv = EnvGen.ar(pitchEnv, gate, timeScale: timeScale);

	src = SinOsc.ar(freq * pitchEnv.linlin(0,1,1,envAmount), mul:amp*env);

	OffsetOut.ar(out, src!2);
},
metadata:(specs: ( attack:ControlSpec(0,1, 2), chirp:ControlSpec(0,1), envAmount: ControlSpec(0,200, 2), timeScale:ControlSpec(0.1,10, 2)) )
).add;
// s.sync;
// (instrument: \l1_kick, dur: 0.2, freq:36.midicps).play;
// }.fork;

// )



// (instrument: \l1_kick, dur: 0.2, freq:24.midicps, amp:1, chirp:0, timeScale:0.1, envAmount:300, attack:0).play;
// ~randomMetadataPlay.value(\l1_kick, 40, 0.2)

// 24.midicps.reciprocal
// {SinOsc.ar(40)}.plot(0.02)
// Env.xyc([[0,1,-7], [0.01,0.2,0], [0.03, 0.07,-1], [0.1,0,0]]).plot


// patterns:
//get all arguments in pattern format and copy to paste buffer
//set the range of arguments for smart stuff
// [instrument: \l1_kick, dur: 0.05, freq:24.midicps, amp:1, chirp:0.6, timeScale:1, envAmount:100, attack:0.04]

// (
// Pdef(\kickTest,
// 	Pbind(\instrument, \l1_kick,
// 		\dur, 0.01,
// 		\legato, 0.8,
// 		\freq,24.midicps,
// 		\amp,1,
// 		\chirp, 0,
// 		\timeScale, 0.7,
// 		\envAmount, 60,
// 		\attack, 0,
// 		// \fx, \l1_bandPass
// 	).collect{|evt|
// 		evt.postln;
// 		evt.play;
// 		evt[\amp] = Rest();
// 		// evt[]
// 	evt; }
//
// );
// )
// Pdef(\kickTest).play

//-------- SAW

// (
SynthDef(\l1_supersaw7, {
	arg freq = 1000, detune = 0.3, release = 0.2, mix = 0.4, amp = 1, out = 0, gate = 1, attack = 0, modPhase = 0, pitchbend = 0, replyID = -1;
	var src, env, paned;
	freq = (pitchbend.midiratio) * freq;
	env = EnvGen.ar(Env.adsr(attack, 0.1,0.8,release ),gate, doneAction:2);

	SendReply.kr(Impulse.kr(60), "/l1_supersaw7/env", env, replyID);

	// src = SawDPW.ar(freq * [-1*detune,detune].resamp1(7).midiratio, [-1,1].resamp1(7), mul:env );
	src = LFSaw.ar(freq * [-1*detune,detune].resamp1(7).midiratio, [0+modPhase,2].resamp1(7).wrap(0,2), mul:env );

	src = SelectX.ar(mix, [src[3]*0.3, src.sum * 1/7]);
	OffsetOut.ar(out, src*amp!2);

},
metadata:(specs: (detune: ControlSpec(0,10,4), release: ControlSpec(0,2, 2), mix: ControlSpec(0,1), attack: ControlSpec(0,1,2)), oscReplies:["/l1_supersaw7/env"] )).add;
// )
// (instrument: \supersaw7, dur: 0.4, freq:36.midicps, amp:1, detune:0.4, release:0.1, mix:300, attack:0, modPhase:0.8).play;
// ~randomMetadataPlay.value(\supersaw7, 200.0, 1)
SynthDef(\l1_fxDucker,
	{
		arg t_duckTrig = 0, decay = 0.2, curve = 0, in = 0, out = 0;
		var sig, env;
		env = Env.perc(0,decay,1,curve).ar(gate:t_duckTrig);
		// env.scope;
		sig = In.ar(in, 2);
		sig = sig * env.linlin(0,1,1,0);
		OffsetOut.ar(out, sig);
	}
).add;


SynthDef(\l1_hoover, {
	arg release = 0.2, riseTime = 0.1, fallTime = 1.7, seed = 44, gate = 1, attack  = 0;
	var snd, freq, bw, delay, decay, env, quitAfter;

	env = Env.adsr(attack, 0.3,1,release).ar(0, gate);
	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	RandID.ir(100);
	RandSeed.kr(1,seed);

	freq = \freq.kr(440);
	//change attacks
	freq = freq * Env([-5, 6, 0], [riseTime, fallTime], [\lin, -4]).kr.midiratio;
	bw = 1.035;
	// non random detunes and delays?
	snd = { DelayN.ar(Saw.ar(freq * ExpRand(bw, 1 / bw)) + Saw.ar(freq * 0.5 * ExpRand(bw, 1 / bw)), 0.01, Rand(0, 0.01)) }.dup(7);
	snd = (Splay.ar(snd) * 3).atan;
	// snd = snd * Env.asr(0.001, 1.0, release).kr(0, \gate.kr(1));
	snd = snd * env;
	// snd = FreeVerb2.ar(snd[0], snd[1], 0.3, 0.9);
	// snd = snd * Env.asr(0, 1.0, 4, 6).kr(2, \gate.kr(1));
	Out.ar(\out.kr(0), snd * \amp.kr(0.1));
}).add;


// (
// {
SynthDef(\l1_12brass,
	{
		arg freq, amp = 1, out = 0, gate = 1, time = 0.3, bendTop = 4, highAmp = 1, replyID = -1;
		var osc, osc2, mods, env, aenv, foldenv, rise, foldenv2, filt, highs, pitchEnv, outmix, quitAfter;

		RandID.ir(100);
		RandSeed.kr(1,226);

		pitchEnv = Env([-5,bendTop,0], [0.04,time/6]).ar(0,1).midiratio;
		aenv = Env.adsr(0.05,0.1,1,0.02).ar(0,gate);
		foldenv = Env.triangle(time/2).ar(0,1);
		foldenv2 = Env.triangle(time/3).ar(0,1);
		rise = Env.new([0,1], [time], 8).ar(0,gate);
		quitAfter = Done.kr(aenv);
		quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
		quitAfter = FreeSelf.kr(quitAfter);



		SendReply.kr(Impulse.kr(60), "/l1_12brass/pitchEnv", pitchEnv, replyID);
		SendReply.kr(Impulse.kr(60), "/l1_12brass/aenv", aenv, replyID);
		SendReply.kr(Impulse.kr(60), "/l1_12brass/foldenv", foldenv, replyID);
		SendReply.kr(Impulse.kr(60), "/l1_12brass/folderEnv2", foldenv2, replyID);
		SendReply.kr(Impulse.kr(60), "/l1_12brass/rise", rise, replyID);


		mods = SinOsc.ar(freq*pitchEnv*1);
		osc = VarSaw.ar((freq *pitchEnv* (Rand(-1,1!4)*0.3).midiratio  + mods.linlin(0,1,0,freq*0)).clip(20,18000), 0,0.5 + mods*0.12);
		osc = Splay.ar(osc) * foldenv.linlin(0,1,1,-1);
		osc = (osc*2).tanh;
		filt = BPF.ar(osc, foldenv2.linlin(0,1,freq,freq*4).clip(20,14000),0.4);
		highs = HPF.ar(osc, 4000, rise*2*highAmp).distort.distort;

		outmix = (filt + highs)*aenv*0.7*AmpCompA.ir(freq)*amp;
		outmix = MidEQ.ar(outmix, freq, 3,-12);
		OffsetOut.ar(out, outmix);
	},
	metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/l1_12brass/folderEnv2","/l1_12brass/folderEnv","/l1_12brass/aenv", "/l1_12brass/pitchEnv", "/l1_12brass/rise"])
).add;
// s.sync;
// (\instrument: \l1_backforth, \freq: 800, \dur: 0.5, \time:0.3).play
// (\instrument: \l1_backforth, \freq: 50, \dur: 0.3, \time: 0.3).play
// (\instrument: \l1_backforth, \freq: 100, \dur: 0.2, \time:0.1).play
// (\instrument: \l1_backforth, \freq: 400, \dur: 0.5, \time:0.3).play

// }.fork;
// )
// Env([-3,3,0], [0.04,0.3], 4).plot
// (
// {
SynthDef(\l1_brassReese,
	{
		arg freq, amp = 1, out = 0, gate = 1, time = 0.3, highAmp = 0, bendTop = 3, replyID = -1;
		var osc, osc2, mods, env, aenv, foldenv, rise, foldenv2, filt, highs, pitchEnv, outmix, quitAfter;

		RandID.ir(100);
		RandSeed.kr(1,30);

		pitchEnv = Env([-3,bendTop,0], [0.04,time/2], 4).ar(0,1).midiratio;
		aenv = Env.adsr(0.05,0.1,1,0.02).ar(0,gate);
		foldenv = Env.sine(time/4).ar(0,1);
		foldenv2 = Env.sine(time/2).ar(0,1);
		rise = Env.new([0,1], [time], 8).ar(0,gate);

		quitAfter = Done.kr(aenv);
		quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
		quitAfter = FreeSelf.kr(quitAfter);


		//OSC replies
		SendReply.kr(Impulse.kr(60), "/l1_brassReese/folderEnv2", foldenv2, replyID);
		SendReply.kr(Impulse.kr(60), "/l1_brassReese/folderEnv", foldenv, replyID);
		SendReply.kr(Impulse.kr(60), "/l1_brassReese/aenv", aenv, replyID);
		SendReply.kr(Impulse.kr(60), "/l1_brassReese/pitchEnv", pitchEnv, replyID);

		SendReply.kr(Impulse.kr(60), "/l1_brassReese/rise", rise, replyID);

		mods = SinOsc.ar(freq*pitchEnv*4);
		osc = VarSaw.ar(freq *pitchEnv* (Rand(-1,1!4)*0.3).midiratio  + mods.linlin(0,1,0,freq*0), 0,0.5 + mods*0.1);
		osc = Splay.ar(osc) * foldenv.linlin(0,1,1,-1).abs;
		osc = (osc*1.5).tanh;
		filt = BRF.ar(osc, foldenv2.linlin(0,1,freq,freq*8).clip(20,10000),2);
		filt = BRF.ar(filt, foldenv2.linlin(0,1,freq*2,freq*0.5).clip(40,10000),1);

		// filt = od
		highs = HPF.ar(osc, 4000, 0.1*highAmp).distort;

		outmix = (filt + highs)*aenv*0.7*AmpCompA.ir(freq)*amp;
		// outmix  = (outmix*2).tanh;

		outmix = MidEQ.ar(outmix, freq, 3,-6);
		// osc2 = VarSaw.ar(freq, 0 ,0.5);

		// osc = SelectX.ar(foldenv, [osc, osc2]);

		OffsetOut.ar(out, outmix);
		// OffsetOut.ar(out, highs*0.25);




	},
	metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/l1_brassReese/folderEnv2","/l1_brassReese/folderEnv","/l1_brassReese/aenv", "/l1_brassReese/pitchEnv", "/l1_brassReese/rise"])
).add;



SynthDef.new(\l1_harmonicWash,{
	arg freq, attack = 0.5, harmOffset = 0, amp = 1, out = 0,release = 2, gate = 1, timeScale = 1, lpf = 500, hpf = 2000, dirt = 0.3, minAmp = 0.32, replyID = -1, leslieSpeed = 2, stereoWidth = 0.3;
	var src, env, mods, freqs, amps, modHighs, moddist, dirtSig, highs, modSpread, quitAfter;
	env = Env.adsr(attack, 0.3,1,release).ar(0,gate);
	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	// modHighs = Env.new([0,0,[0.5],[0.5],0], [0.1,[0.01,0.1].resamp1(4)], -20, 2,0).ar;
	modHighs  = 0;

	freqs = freq * ([0.5,2.5+harmOffset,3,3.5+harmOffset] + modHighs);
	amps = AmpCompA.kr(freqs, minAmp:minAmp);
	mods = Env.new([1,0,1,0,[1,0.4],0,1,0], [0.05, [0.5,0.4],[0.05, 0.15],0.1,0,0.1,1],releaseNode: 6,loopNode:0).ar(0,1,timeScale:timeScale);
	moddist  = Env([0,1,0,1],[0.3,0.4,0.2], [-5,2], 2,0).ar(timeScale:timeScale);
	modSpread = SinOsc.ar(leslieSpeed).range(-1 * stereoWidth,stereoWidth);

	SendReply.kr(Impulse.kr(60), "/l1_harmonicWash/env", env, replyID);
	SendReply.kr(Impulse.kr(60), "/l1_harmonicWash/mods", mods, replyID);
	SendReply.kr(Impulse.kr(60), "/l1_harmonicWash/moddist", moddist, replyID);
	SendReply.kr(Impulse.kr(60), "/l1_harmonicWash/modSpread", modSpread, replyID);


	freqs[3] = freqs[3] + WhiteNoise.ar().range(-100,100);
	freqs[2] = freqs[2] + WhiteNoise.ar().range(-100,100);
	src = VarSaw.ar(freqs,width: [0.1,0.4].resamp1(4) + mods.linlin(0,1,0.2,0.5),mul:amp*env);
	src = src*amps;
	src  = Splay.ar(src,modSpread)*2;

	dirtSig = (LPF.ar(src, freq.clip(20,16000)) * moddist.linlin(0,1,1,10)*dirt).tanh.excess(0.2);
	dirtSig = HPF.ar(dirtSig, (freq*2).clip(20,16000))*2;

	highs  = HPF.ar(src, hpf);
	src = LPF.ar(src, lpf);
	src = src + dirtSig + highs;


	OffsetOut.ar(out, src);
},
metadata:(specs:(harmOffset: ControlSpec.new(-2.4, 8), attack: ControlSpec.new(0, 10), release: ControlSpec.new(0, 10), timeScale: ControlSpec.new(0, 4), lpf: ControlSpec.new(20, 16000,'exp'), hpf: ControlSpec.new(20, 16000, 'exp'), dirt: ControlSpec.new(0, 2), minAmp: ControlSpec.new(0, 1), replyID: ControlSpec.new(0, 5), stereoWidth: ControlSpec.new(-2, 2), leslieSpeed: ControlSpec.new(0, 20)), oscReplies:["/l1_harmonicWash/env", "/l1_harmonicWash/mods", "/l1_harmonicWash/moddist", "/l1_harmonicWash/modSpread"])
).add;

thisThread.randSeed = 204;

SynthDef.new(\l1fx_mashedVerbPitch,{
	arg amp = 0.1, out, in, gate = 1, verbLen = 0.5, dry=0, replyID = -1, brf = 400, brfq = 2,pitchshiftWindow = 0.4, pitchshift = 12, pitchshiftWet = 1.2, modTime = 0.5, modCurve = -2, hpf = 100, seed = 2930;
	var input, fx, env, verb, modEnv, fall, quitAfter;
	RandID.ir(id: 0);
	RandSeed.ir(trig: 1, seed: seed);

	fall = 0.004;
	env = Env.adsr(0.0, 0.3,1,0.05).ar(0, gate);
	// thisThread.randSeed = 1923;

	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	modEnv = Env([0,0,1,0,1,0,0], [0,max((modTime*0.2)-fall, fall),fall,max((modTime*0.8)-fall, fall), fall, fall],modCurve,5,0).ar(0,gate);

	SendReply.kr(Impulse.kr(60), "/l1fx_mashedVerbPitch/modEnv", modEnv, replyID);
	SendReply.kr(Impulse.kr(60), "/l1fx_mashedVerbPitch/env", env, replyID);

	input = In.ar(in,2);
	/*	// thisThread.randSeed = seed;*/

	fx = CheapVerb.ar(input, verbLen,10)*modEnv;
	fx = BRF.ar(fx, brf,brfq);
	fx = fx+ (PitchShift.ar(fx,pitchshiftWindow,SinOsc.ar(0.5).range(pitchshift-0.2,pitchshift+0.2).midiratio).excess(-40.dbamp).distort * pitchshiftWet);
	// fx = fx + FreqShift.ar(fx, [10,-1.5].stutter(2),[0,pi*0.2].stutter(2));
	fx = HPF.ar(fx, hpf);
	Out.ar(out, (fx + (dry*input))*env*amp);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/l1fx_mashedVerbPitch/env", "/l1fx_mashedVerbPitch/modEnv"])
).add;



SynthDef.new(\l1fx_lighterSpace,{
	arg amp = 1, out = 0, in = 0, gate = 1, dry=0, replyID = -1, fall = 0.006, modTime = 0.8, modCurve = 2, lpf = 300, lpfAmp = 0, modDryWet = 1,verbLen = 1, seed = 2930;
	var input, fx, env, verb, ampFollow, ampRescale, modEnv, quitAfter;
	RandID.ir(id: 0);
	RandSeed.ir(trig: 1, seed: seed);

	// fall = 0.006;
	env = Env.adsr(0.0, 0.3,1,0.05).ar(0, gate);
	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	// Done
	modEnv = Env([0,0,1,0,1,0,0], [0,max((modTime*0.2)-fall, fall),fall,max((modTime*0.8)-fall, fall), fall, fall],modCurve,5,0).ar(0,gate);

	SendReply.kr(Impulse.kr(60), "/l1fx_lighterSpace/env", env, replyID);
	SendReply.kr(Impulse.kr(60), "/l1fx_lighterSpace/modEnv", modEnv, replyID);

	input = In.ar(in,2);
	ampFollow = Amplitude.ar(input, 0.001,0.001);


	ampRescale = Sanitize.ar(ampFollow.reciprocal,8).clip(0,8);
	// ampRescale.scope;

	// Normalizer
	// thisThread.randSeed = seed;
	fx = CheapVerb.ar((input*ampRescale).softclip, verbLen,5);
	fx = Splay.ar(fx, 0.9);
	fx = SelectX.ar((1-modEnv)*modDryWet, [fx,( (LPF.ar(fx,lpf)*2).tanh) * DC.ar(lpfAmp)]);

	Out.ar(out, ((dry*input) + fx)*env*amp*0.9);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/l1fx_lighterSpace/modEnv", "/l1fx_lighterSpace/env"])
).add;


SynthDef.new(\l1_noiseChaos,{
	arg freq, attack=0, subIndex = 0.502, sustainA = 1, decayA = 0.5, decayFenv = 0.6,sawWidth = 0.5, amp = 1, filterIndex = 1, rndAmount = 0.2, out = 0, gate = 1, replyID = -1, seed = 5673, hpf = 20, tanDist = 1;
	var src, src2, env, quitAfter, noise, filt, highs, env2, mix, hp;
	RandID.ir(id: 0);
	RandSeed.ir(trig: 1, seed: seed);
	env = Env.adsr(attack, decayA,sustainA,0.05,1,-1).ar(0, gate);
	env2 = Env.adsr(0.04, decayFenv,0,0.05).ar(0, gate);

	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	// SendReply.kr(Impulse.kr(60), "/instrument/param", env, replyID);

	noise = BrownNoise.ar();
	src = VarSaw.ar(freq + (noise.linlin(-1,1,0,freq*rndAmount)),width:sawWidth, mul:amp*env);
	src2 = VarSaw.ar(freq*subIndex + src.linlin(-1,1,-1 * freq,freq),width:sawWidth,  mul:amp*env);
	filt = DFM1.ar(src+src2, freq*filterIndex + env2.linlin(0,1,0,freq*2) + noise.linlin(-1,1,0,freq) + src2.linlin(-1,1,-1*freq, 1*freq), 0.4,1).tanh.softclip;

	highs = HPF.ar(filt.excess(0.25),1000);
	highs = DelayC.ar(highs, 0.02,SinOsc.ar(5, [0,2pi].resamp1(5)).linlin(-1,1,0.05,0.01));
	highs  = Splay.ar(highs,2);
	highs = HPF.ar(highs, 1000);

	mix = highs + (filt*0.5) + (noise*0.01*env2*amp);

	mix = (mix*tanDist).tanh * env * (tanDist.reciprocal);
	hp = HPF.ar(mix, hpf);


	OffsetOut.ar(out, hp);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/instrument/param"])
).add;


SynthDef.new(\l1_samplePlayer,{
	arg freq, amp = 1, rate = 1, attack = 0, transientEnv = 10, decayA = 0.3, sustainA = 1, startPos = 0, transientLen = 0.008, out = 0, gate = 1, replyID = -1, bufnum, tanDist = 1;
	var src, env, quitAfter, envP;
	env = Env.adsr(attack, decayA,sustainA,0.05,1,-2).ar(0, gate);
	envP = Env([0,1,0],[0,transientLen], -4).ar(0,gate);

	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	src = PlayBuf.ar(2, bufnum, BufRateScale.kr(bufnum)*rate + (envP*transientEnv),loop:0, startPos:startPos) * env * amp;
	tanDist  = max(tanDist,1);
	src = (src * tanDist).tanh * (tanDist.reciprocal);
	// SendReply.kr(Impulse.kr(60), "/instrument/param", env, replyID);

	// src = SinOsc.ar(freq, mul:amp*env);

	OffsetOut.ar(out, src!2);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/instrument/param"])
).add;

SynthDef.new(\l1fx_simpleVerb,{
	arg amp = 1, out = 0, in = 0, gate = 1, dry=0, replyID = -1;
	var input, fx, env, verb, quitAfter;
	env = Env.adsr(0.0, 0.3,1,0.05).ar(0, gate);
	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	// SendReply.kr(Impulse.kr(60), "/instrument/param", env, replyID);
	input = In.ar(in,2);
	// fx = CheapVerb.ar(input, 0.4,20);
	fx = FreeVerb.ar(input, 1);

	Out.ar(out, ((dry*input) + fx)*env*amp);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/instrument/param"])
).add;


SynthDef.new(\l1fx_reverseAmp,{
	arg amp = 1,attackTime = 0, curve = 2, out = 0, in = 0, gate = 1, dry=0, replyID = -1;
	var input, fx, env, verb, quitAfter;
	env = Env.adsr(attackTime, 0.3,1,0.05,1,curve).ar(0, gate);
	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	// SendReply.kr(Impulse.kr(60), "/instrument/param", env, replyID);
	input = In.ar(in,2);
	fx = CheapVerb.ar(input, 0.4,20);

	Out.ar(out, ((dry*input) + fx)*env*amp);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/instrument/param"])
).add;


SynthDef.new(\l1_LaserSync,{
	arg freq, amp = 1, out = 0, gate = 1, replyID = -1, syncFactor = 1, modSync = 1, lfoSpeed = 5, attack = 0;
	var src, env, quitAfter, mod;
	env = Env.adsr(attack, 0.3,1,0.05).ar(0, gate);

	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	// SendReply.kr(Impulse.kr(60), "/instrument/param", env, replyID);
	mod = SinOsc.ar((freq*1)+lfoSpeed);

	src = SyncSaw.ar(freq, freq*syncFactor * mod.linlin(-1,1,1,1 + modSync), mul:amp*env*0.5);
	src = src +  LPF.ar(SyncSaw.ar(freq * 0.5, freq*0.5, mul:amp*env * 0.5), (freq*2).clip(20, 10000));
	// src = SinOsc.ar(freq, mul:amp*env);

	OffsetOut.ar(out, src!2);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/instrument/param"])
).add;
// s.sync;


SynthDef.new(\l1_fullBass,{
	arg freq, amp = 1, out = 0, gate = 1, replyID = -1, harm1 = 2,  harm2 = 4, excess = 0.01, duckT = 0.04;
	var src, env, quitAfter, envy, envy2, ducks, nEnv;
	env = Env.adsr(0.0, 0.3,1,0.05).ar(0, gate);

	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	// SendReply.kr(Impulse.kr(60), "/instrument/param", env, replyID);

	envy = Env.new([0,1,0.4,0.1], [0.01,0.2, 0.1], 4).ar(0,1);
	envy2 = Env.new([0,1,1,0.1], [0.1,0.2,0.1]).ar(0,1);
	nEnv = Env.new([0,1,0.4,0.1], [0.2,0.1,0.1]).ar(0,Impulse.ar(1));

	ducks = Env.new(1-[1,1,0,1], [duckT,0.05,0.04], [4,-4]).ar(0,1);

	src = SinOscFB.ar(freq,envy*0.3 +ducks, mul:amp*env*envy);
	src = src + SinOscFB.ar(freq*2,envy2*0.3 +ducks, mul:amp*env*envy2);
	src = src*(1-ducks);
	src = src + VarSaw.ar(freq * harm1, 0, SinOsc.ar(freq * 0.125).range(0.4,0.6), 0.02 * nEnv.linlin(0,1,1,0));
	src = src + VarSaw.ar(freq * harm2, 0, SinOsc.ar(freq * 0.125).range(0.4,0.6), 0.02*DelayN.ar(nEnv,0.1,0.02));
	src = BRF.ar(src, nEnv.linlin(0,1,500, 2000), 1);
	src = src + src.excess(excess);
	src = Normalizer.ar(src, 0.8);
	// src = (src*2).tanh;
	src = src *env * amp;
	// src = src + ((DFM1.ar(src, (freq*4) + src.linlin(0,1,0,freq*4), 0.7,2,1)*4).distort * 0.08);

	OffsetOut.ar(out, src!2);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/instrument/param"])
).add;

SynthDef.new(\l1_deepbass,{
	arg freq, amp = 1, out = 0, gate = 1, replyID = -1, clockTempo = 1, sub = 1;
	var src, filt, warmLow, subSig, pulseEnv, lfo,mix1, chorus, env, quitAfter;
	env = Env.adsr(0.01, 0.3,1,0.05).ar(0, gate);

	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	pulseEnv = LFSaw.ar(2 * clockTempo, 1);

	SendReply.kr(Impulse.kr(60), "/l1_deepbass/pulseEnv", pulseEnv, replyID);

	lfo = LFSaw.ar(0.125*0.5 * clockTempo, 1);
	src = SyncSaw.ar(freq, pulseEnv.range(freq*0.5,freq*1.5).lag(0,0), mul:lfo.range(0.2,1));

	// src = (src.clip(-1,1).pow(4) * 10).tanh;
	src= CrossoverDistortion.ar(src, 0.2,SinOsc.ar(freq*0.5).range(0.1,0.8));
	src = (src * 10).tanh;
	src = Sanitize.ar(src);
	filt = DFM1.ar(src, 1000 + src.linlin(0,1,0,4000), 0.96, 0.1, 1);
	filt = filt+ AllpassL.ar(filt, 0.2, 2*(freq.reciprocal),SinOsc.ar(0.125 * 0.25).range(0.1,1.2));
	filt = HPF.ar(filt * Env.asr(0.004,1,1).ar(0,1), 5000);
	filt = Sanitize.ar(filt);
	warmLow = LPF.ar(src,200, 2).tanh * 0.7;
	warmLow = HPF.ar(warmLow, 110);
	warmLow = HPF.ar(warmLow, 110);
	warmLow = HPF.ar(warmLow, 110);

	subSig = SinOsc.ar(freq*0.5 + pulseEnv.lincurve(-1,1,freq * 0.2, 0,-10), 0,0.2) * sub;
	mix1 = warmLow + filt + subSig;


	chorus = CombL.ar(mix1, 0.2, SinOsc.ar([freq * 0.5,(freq*0.5) + 0.1]).range(1,2) * freq.reciprocal * 1, freq.reciprocal*10);
	chorus =CombL.ar(chorus, 0.02,freq.reciprocal,freq.reciprocal * 5);
	chorus = Splay.ar(chorus, 0.9);
	// chorus = HPF.ar(chorus, 400);
	chorus = BPF.ar(chorus, 1000,1)  *  LFSaw.ar(0.5, 1).lincurve(-1,1,0.01,1,1);

	// chorus = LPF.ar(chorus, 900);

	mix1= mix1 +chorus;
	mix1 = env * mix1 * amp;



	// SendReply.kr(Impulse.kr(60), "/instrument/param", env, replyID);

	// src = SinOsc.ar(freq, mul:amp*env);

	OffsetOut.ar(out, mix1);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/l1_deepbass/pulseEnv"])
).add;

SynthDef.new(\l1fx_bpsEnved,{
	arg amp = 1, out = 0, in = 0, gate = 1, dry=0, replyID = -1,  seed = 5673, fq = 0.1, minF = 500, maxF = 1800, speedMax = 15, speedMin = 2, speed = 0.3, dropTime = 0.1, volCurve = 10, duckT = 0.04, duck = 0, duckLen =0.09 ;
	var input, fx, env, verb, quitAfter, freqs, volumes, sineF, moveFs, ducks;
	RandID.ir(id: 0);
	RandSeed.ir(trig: 1, seed: seed);

	env = Env.adsr(0.0, 0.3,1,0.05).ar(0, gate);
	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	ducks = Env.new(1-[1,1,0,1], [duckT,duckLen*0.555,duckLen*0.444], [4,-4]).ar(0,1);
	SendReply.kr(Impulse.kr(60), "/l1fx_bpsEnved/env", env, replyID);


	input = In.ar(in,2);
	//
	moveFs = Env.pairs([[0,1],[dropTime,1], [dropTime+0.1,0.2]],0).ar(0,1).lag(0,[0,0.1,0.02,0.5]);

	// freqs = LFNoise1.ar(9).range([0,1].resamp1(8).stutter(2).linexp(0,1,100,8000), [0,1].resamp1(8).stutter(2).linexp(0,1,300,12000));
	freqs = [0,1].resamp1(6).linexp(0,1,minF,maxF) * moveFs;
	SendReply.kr(Impulse.kr(60), "/l1fx_bpsEnved/freqs", freqs, replyID);

	sineF = Env.perc(0,speed,1,-2).ar(0,1).linlin(0,1,speedMin,speedMax);
	volumes = SinOsc.ar(sineF, [0,2pi].resamp1(6));
	SendReply.kr(Impulse.kr(60), "/l1fx_bpsEnved/rawVolumeLFO", volumes, replyID);
	volumes = volumes.lincurve(-1,1,0.01,1,volCurve);
	// volumes.scope;
	SendReply.kr(Impulse.kr(60), "/l1fx_bpsEnved/volumes", volumes, replyID);

	fx = BPF.ar(input,freqs.clip(20,12000).stutter(2), fq.clip(0.1,1), fq.linlin(0.1,1, 3,0.5) * 1*(volumes.stutter(2)));
	fx = Mix.new(fx);

	Out.ar(out, ((dry*input) + fx)*env*amp * (1-(ducks*duck.clip(0,1))));
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/l1fx_bpsEnved/freqs", "/l1fx_bpsEnved/volumes","/l1fx_bpsEnved/rawVolumeLFO", "/l1fx_bpsEnved/env", "/l1fx_bpsEnved/ducks"])
).add;


SynthDef.new(\l1_simpleSines,{
	arg freq, amp = 1,pulseRate = 2, attack = 3, release = 3, out = 0, gate = 1, replyID = -1;
	var src, env, quitAfter, pulse, div, compensate;
	env = Env.adsr(attack, 0.3,1,release,1,2).ar(0, gate);

	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	pulse = Impulse.ar(pulseRate);
	div = ToggleFF.ar(pulse);

	pulse = Env.perc(0,0.2,1,0).ar(0,pulse);
	compensate = AmpCompA.ar(K2A.ar([freq, freq*2]));
	SendReply.kr(Impulse.kr(60), "/l1_simpleSines/env", env, replyID);
	SendReply.kr(Impulse.kr(60), "/l1_simpleSines/pulse", pulse, replyID);
	SendReply.kr(Impulse.kr(60), "/l1_simpleSines/div", div, replyID);

	src = SinOscFB.ar([freq, freq*2 * pulse.lincurve(0,1,1,1.06,20) * div.linlin(0,1,1,1.01)],0.2, mul:[pulse.lag(0.02,0).lincurve(0,1,1,0, -1),1]*amp*env*compensate*0.4);


	OffsetOut.ar(out, src!2);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/l1_simpleSines/env", "/l1_simpleSines/pulse", "/l1_simpleSines/div"])
).add;


SynthDef.new(\l1fx_pitchWave,{
	arg amp = 1, out = 0, in = 0,dropTime = 3, gate = 1, dry=0, replyID = -1,  seed = 5673;
	var input, fx, env, verb, quitAfter, delay, delayEnv;
	RandID.ir(id: 0);
	RandSeed.ir(trig: 1, seed: seed);

	env = Env.adsr(0.0, 0.3,1,0.05).ar(0, gate);
	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	// SendReply.kr(Impulse.kr(60), "/instrument/param", env, replyID);
	input = In.ar(in,2);

	delayEnv = Env.pairs([[0,1],[dropTime,0]], 0).ar(0,Impulse.ar(1));
	delay = DelayC.ar(input, 0.2,delayEnv.lincurve(0,1,0,0.2, [-1,-2,-3].stutter(2)),0.3);
	delay = Mix.new(delay);
	fx = delay;
	// fx = CheapVerb.ar(input, 0.4,20);

	Out.ar(out, ((dry*input) + fx)*env*amp);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/instrument/param"])
).add;


SynthDef.new(\l1_moreSyncSaw,{
	arg freq, amp = 1, out = 0, gate = 1, replyID = -1, syncFactor = 1, modSync = 1, lfoSpeed = 5, attack = 0;
	var src, env, quitAfter, mod;
	env = Env.adsr(attack, 0.3,1,0.05).ar(0, gate);

	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	// SendReply.kr(Impulse.kr(60), "/instrument/param", env, replyID);
	mod = SinOsc.ar((freq*1)+lfoSpeed);

	src = SyncSaw.ar(freq, freq*syncFactor * mod.linlin(-1,1,1,1 + modSync), mul:amp*env*0.5);
	src = src +  LPF.ar(SyncSaw.ar(freq * 0.5, freq*0.5, mul:amp*env * 0.5), (freq*2).clip(20, 10000));
	// src = SinOsc.ar(freq, mul:amp*env);

	OffsetOut.ar(out, src!2);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/instrument/param"])
).add;

SynthDef.new(\l1_digitoneV2,{
	arg freq, amp = 1, lineLen = 0.8, brF = 0.5, transientAmt = 0.14, lfoSpeed= 5, out = 0, gate = 1, replyID = -1, release = 0.1;
	var src, env, quitAfter, envLin, transient, lfo;
	env = Env.adsr(0.0, 0.3,1,release,1,-3).ar(0, gate);

	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	// SendReply.kr(Impulse.kr(60), "/instrument/param", env, replyID);
	transient = Env.perc(0,0.015,transientAmt,-1).ar(0,1);

	src = SinOsc.ar(freq * transient.linlin(0,1,1,10), mul: 1/10 );
	src = src * SinOsc.ar((src * 20000) + freq*2.12 + transient.linlin(0,1,0,11000), mul: 1);
	src = src.excess(SinOsc.ar(20 + transient.linlin(0,1,0,1000)));
	envLin = Env.perc(0,lineLen,1,-1).ar(0,1);
	src  = ((src * 20 - (envLin * 15) + transient.linlin(0,1,0,30)).tanh *0.1 ) + src;

	src = LeakDC.ar(src) * amp;
	lfo =  SinOsc.ar(lfoSpeed);
	src = BRF.ar(src, (brF.linlin(0,1,200,800) + lfo.linlin(-1,1,-900,900) + envLin.lincurve(0,1,0,1000,10) + transient.linlin(0,1,0,2000)).clip(100,12000), 4);
	src = src.clip(-1,1);
	OffsetOut.ar(out, src!2);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/instrument/param"])
).add;


SynthDef.new(\l1_reeseDouble,{
	arg freq, amp = 1, out = 0, gate = 1, replyID = -1, timeScale = 1, tempoScale = 1, agression = 1, lpfFactor = 1, subOsc = 0, modFreqFactor = 1.5;
	var  env, quitAfter, osc, modOsc, outBus, lowFilt, env1, lfo1, lfo2, lfo3, env2, highBus, midBus, sub;

	env = Env.adsr(0.01, 0.3,1,0.05).ar(0, gate);

	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);





	// src = SinOsc.ar(freq, mul:amp*env);

	env1 = Env.perc(0,0.7,1,0).ar(0,1, timeScale: timeScale);
	env2 = Env.perc(0,2,1,0).ar(0,1, timeScale: timeScale);
	// lfo1 = Env.perc(0,2,1,0).ar(0,1, timeScale: timeScale);
	lfo1 = LFPulse.ar(2 * tempoScale);
	lfo2 = Env.new([0,1,0.4,0.6,0.1,0.4,0,0], [0.1,0.1,0.3,0.2,0.1,0.2,0],[0,6,2,-4,-7,-4,-4,0] , releaseNode:6, loopNode:0).ar(0,1, timeScale:2 * timeScale);
	lfo3 = Env.new([ 0, 0.6, 0, 0.4, 1, 0, 0.1, 0.4 ], [0.1,0.1,0.3,0.2,0.1,0.2,0],[0,6,2,-4,-7,-4,-4,0] , releaseNode:6, loopNode:0).ar(0,1, timeScale:2 * timeScale);


	SendReply.kr(Impulse.kr(60), "/l1_reeseDouble/env1", env1, replyID);
	SendReply.kr(Impulse.kr(60), "/l1_reeseDouble/env2", env2, replyID);
	SendReply.kr(Impulse.kr(60), "/l1_reeseDouble/lfo1", lfo1, replyID);
	SendReply.kr(Impulse.kr(60), "/l1_reeseDouble/lfo2", lfo2, replyID);
	SendReply.kr(Impulse.kr(60), "/l1_reeseDouble/lfo3", lfo3, replyID);

	sub = SinOsc.ar(freq*0.5);
	modOsc = SinOscFB.ar(freq*modFreqFactor, env2.linlin(0,1,1,0));
	osc = SyncSaw.ar(freq ,(freq * env1.linlin(0,1,1,5) )+ (modOsc.linlin(-1,1,lfo1.lag(0.01).range(0,-4)*freq, lfo1.lag(0.1).range(1,5)*freq)*agression), mul:0.5);

	osc = osc + (sub*subOsc);
	lowFilt = DFM1.ar(osc, (freq* lfo3.linlin(0,1,0.8,3) *lpfFactor)+ osc.linlin(0,1,0*freq, 2*freq),0.9 * agression, 0.9,0).tanh;

	highBus = osc * 0.1;

	highBus = BRF.ar(highBus, lfo3.lag(0.2).linlin(0,1,2000,400),2);
	highBus = BRF.ar(highBus, lfo3.linexp(0,1,1000,10000),1) * lfo2.lincurve(0,1,0.05,1, 2);
	highBus = DelayL.ar(highBus, 0.1, lfo3.linlin(0,1,0,0.03));
	highBus = CombL.ar(highBus, 0.1,freq.reciprocal + highBus.linlin(-0.2,0.2,-0.002,0.002),0.7);



	outBus = (lowFilt*0.4 * lfo3.linlin(0,1,1,0.5)) + highBus;
	outBus = LRLowCut.ar(outBus , 100,4,4);
	outBus = (outBus * amp*env);

	outBus = LeakDC.ar(outBus);

	OffsetOut.ar(out, outBus!2);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/l1_reeseDouble/env1","/l1_reeseDouble/env2", "/l1_reeseDouble/lfo1", "/l1_reeseDouble/lfo2", "/l1_reeseDouble/lfo3"])
).add;

SynthDef.new(\l1_reeseLightHighs,{
	arg freq, amp = 1, out = 0, gate = 1, replyID = -1, attack= 0.01, ampCurve = -4, timeScale = 1, tempoScale = 1, agression = 0, subOsc = 1, modFreqFactor = 1, modOffset = 0, invertEnv = 0, dilateEnv = 0.1, bpf = 400, bpfVol = 1;
	var  env, quitAfter, osc, modOsc, outBus, lowFilt, env1, lfo1, lfo2, lfo3, env2, highBus, midBus, sub, comp;

	env = Env.adsr(attack, 0.3,1,0.05,1,ampCurve).ar(0, gate);

	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);





	// src = SinOsc.ar(freq, mul:amp*env);

	dilateEnv = ([-0.1,0.124,0.022,-0.275] * dilateEnv);
	dilateEnv = ([0.1,0.1,0.3,0.2,0.1,0.2,0] + dilateEnv).abs;

	env1 = Env.perc(0,0.7,1,0).ar(0,1, timeScale: timeScale);
	env2 = Env.perc(0,0.1,1,0).ar(0,1, timeScale: timeScale);
	// lfo1 = Env.perc(0,2,1,0).ar(0,1, timeScale: timeScale);
	lfo1 = LFPulse.ar(2 * tempoScale);
	lfo2 = Env.new([0,1,0.4,0.6,0.1,0.4,0,0].linlin(0,1,invertEnv.lincurve(0,1,0,1,2), invertEnv.lincurve(0,1,1,0,-8)), dilateEnv, [0,6,2,-4,-7,-4,-4,0]+modOffset , releaseNode:6, loopNode:0).ar(0,1, timeScale: timeScale);
	lfo3 = Env.new([ 0, 0.6, 0, 0.4, 1, 0, 0.1, 0.4 ].linlin(0,1,invertEnv.lincurve(0,1,0,1,4), invertEnv.lincurve(0,1,1,0,-5)), dilateEnv, [0,6,2,-4,-7,-4,-4,0]+modOffset , releaseNode:6, loopNode:0).ar(0,1, timeScale: timeScale);


	SendReply.kr(Impulse.kr(60), "/l1_reeseLightHighs/env1", env1, replyID);
	SendReply.kr(Impulse.kr(60), "/l1_reeseLightHighs/env2", env2, replyID);
	SendReply.kr(Impulse.kr(60), "/l1_reeseLightHighs/lfo1", lfo1, replyID);
	SendReply.kr(Impulse.kr(60), "/l1_reeseLightHighs/lfo2", lfo2, replyID);
	SendReply.kr(Impulse.kr(60), "/l1_reeseLightHighs/lfo3", lfo3, replyID);

	sub = SinOsc.ar(freq*0.5);
	modOsc = SinOscFB.ar(freq*modFreqFactor, env2.linlin(0,1,1,0));
	osc = SyncSaw.ar(freq ,(freq * env1.linlin(0,1,1,5) )+ (modOsc.linlin(-1,1,lfo1.lag(0.01).range(0,-4)*freq, lfo1.lag(0.1).range(1,5)*freq)*agression), mul:0.5);

	osc = osc + (sub*subOsc);

	highBus = osc * 0.1;

	highBus = BRF.ar(highBus, lfo3.lag(0.2).linlin(0,1,2000,100),2);
	highBus = BRF.ar(highBus, lfo3.lincurve(0,1,1000,5000, 30),3) * lfo2.lincurve(0,1,-10.dbamp,1, 1);
	highBus = DelayL.ar(highBus, 0.1, lfo3.linlin(0,1,0,0.03));
	highBus = CombL.ar(highBus, 0.1,freq.reciprocal + highBus.linlin(-0.2,0.2,-0.002,0.002),0.2);




	outBus = highBus;
	outBus = highBus + BPF.ar(osc, (bpf * lfo2.lag(0.2,0).linlin(0,1,1.2,1)).clip(50,10000), 0.4,0.1*bpfVol).distort;

	outBus = LRLowCut.ar(outBus , 140,5,5);
	// comp = Peak.ar(outBus).poll.reciprocal.sanitize;
	comp = outBus.abs.lag(0.008,0.005).reciprocal.sanitize;
	comp = (outBus * comp * 0.1).tanh.clip(-1,1);

	outBus = outBus + (comp * 0.05);

	outBus = LeakDC.ar(outBus);
	outBus = (outBus * amp*env);



	OffsetOut.ar(out, outBus!2);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/l1_reeseLightHighs/env1","/l1_reeseLightHighs/env2", "/l1_reeseLightHighs/lfo1", "/l1_reeseLightHighs/lfo2", "/l1_reeseLightHighs/lfo3"])
).add;


SynthDef.new(\l1_clusterKick,{
	arg freq, amp = 1, out = 0, gate = 1, replyID = -1, release = 0.05, clusterPunch = 0, clusterOffset = 0.1, lpf = 400, sub = 1, ring=1, transient = 1, transientPDur = 0.01, transientADur = 0.07;
	var src, env, quitAfter, aEnv, pEnv, cluster, clusterHighs, clusEnv;
	env = Env.adsr(0, 0.3,1,release,1,-4).ar(0, gate);

	quitAfter = Done.kr(env);
	quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
	quitAfter = FreeSelf.kr(quitAfter);

	// SendReply.kr(Impulse.kr(60), "/instrument/param", env, replyID);


	aEnv = Env.new([0,1,0.5*sub,0], [0.0001,transientADur,0.5], -2).ar(0,1);
	pEnv = Env.new([1,20*transient,2,1], [0.00001,transientPDur,0.3], -5).ar(0,1);
	clusEnv = Env.new([0,1,0.1,0], [0.0001,0.1,0.5], -2).ar(0,1);


	cluster = SinOsc.ar(freq*pEnv.linlin(1,20,1,clusterPunch.linlin(0,1,2,4))*([1,1.2,1.8,2.2,3.4]+clusterOffset)*2,mul:0.1*clusEnv).sum;
	cluster = CombL.ar(cluster, 0.1, ((freq.reciprocal*0.50)+ SinOsc.ar(freq*1).range(-0.001,0.0001)).clip(0.0001,0.1),ring.linlin(0,1,0.5,1.4));
	cluster = CombL.ar(cluster, 0.1, ((freq.reciprocal*0.50)+ SinOsc.ar(freq*1).range(-0.001,0.006)).clip(0.0001,0.1),ring.linlin(0,1,0.01,0.2));

	clusterHighs = LRLowCut.ar((cluster.excess(-70.dbamp)*20.dbamp).tanh + cluster,500, 5,5);
	clusterHighs = LPF.ar(clusterHighs, lpf);
	cluster = LRHiCut.ar((cluster.excess(-50.dbamp)*20.dbamp).tanh + cluster,200, 3,3);

	cluster = LRLowCut.ar(cluster+clusterHighs, freq * 3,5) * 0.4;
	cluster = (cluster*2).softclip() * 2.reciprocal;

	src = SinOsc.ar(freq*pEnv, mul:aEnv);


	src = ( src + cluster) * amp*env;

	OffsetOut.ar(out, src!2);
},
metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/instrument/param"])
).add;


SynthDef.new(\l1_decayKick,{
		arg freq, amp = 1, out = 0, gate = 1, replyID = -1, release = 0.05, sub = 1, transient = 1, transientPDur = 0.01, transientADur = 0.1;
		var src, env, quitAfter, aEnv, pEnv;
		env = Env.adsr(0, 0.3,1,release,1,-4).ar(0, gate);

		quitAfter = Done.kr(env);
		quitAfter = DelayN.kr(quitAfter, 2/60, 2/60);// twoframe delay
		quitAfter = FreeSelf.kr(quitAfter);



		aEnv = Env.new([0,1,0.8*sub ,0], [0.0001,transientADur,0.5], -2).ar(0,1);
		pEnv = Env.new([1,50*transient,2,1], [0.00001,transientPDur,0.3], -5).ar(0,1);

		SendReply.kr(Impulse.kr(60), "/l1_decayKick/aEnv", env, replyID);

		src = SinOsc.ar(freq*pEnv, mul:aEnv*amp*env);

		OffsetOut.ar(out, src!2);
	},
	metadata:(specs:(/*len:ControlSpec.new(0.1, 20), */), oscReplies:["/l1_decayKick/aEnv"])
	).add;


SynthDescLib.new(\lib1, s);
~synthsLib1 = [\l1_fxDown, \l1_ambiance, \l1_transient, \l1_noiseChord, \l1_hatWN, \l1_hatWN2, \l1_kick, \l1_bassSwell, \l1_supersaw7, \l1_fxDucker, \l1_hoover, \l1_12brass, \l1_brassReese, \l1_harmonicWash, \l1fx_mashedVerbPitch, \l1fx_lighterSpace, \l1_noiseChaos, \l1_samplePlayer, \l1fx_simpleVerb, \l1fx_reverseAmp, \l1_LaserSync, \l1_fullBass, \l1_deepbass, \l1fx_bpsEnved, \l1_simpleSines, \l1fx_pitchWave, \l1_digitoneV2, \l1_moreSyncSaw, \l1_reeseDouble, \l1_reeseLightHighs, \l1_clusterKick, \l1_decayKick];
~synthsLib1.do{arg item; ("\\" ++ item).postln; SynthDescLib.getLib(\lib1).add(SynthDescLib.global[item])};

"\nSynthDescLib.getLib(\lib1).browse\n".postln;
"~postArgsPbind.value(\\l1_12brass)\n".postln;



"imported synthlib successfully".postln;
