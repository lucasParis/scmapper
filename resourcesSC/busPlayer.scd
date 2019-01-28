SynthDef(\SCMbusPlayer_stereo,
	{
		arg in, out = 0, gate = 1, fadeIn = 0, fadeOut = 3;
		var src, env;
		env = Env.asr(fadeIn,1,fadeOut,-4).ar(2,gate);
		src = In.ar(in,2);
		Out.ar(out, src*env);
	}
).add;
