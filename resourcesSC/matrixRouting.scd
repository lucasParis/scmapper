
SynthDef(
	\matrixConnectionKr,
	{
		// arg in, out, min = 0, max = 1, invert = 0;
		arg in, out, base = 0, range = 1, invert = 0;
		var sig, min, max;
		min = base;
		max = (base + range).clip(-1,1);

		sig = In.kr(in);
		// sig = 1- sig;
		sig = sig.linlin(0,1,min, max);
		Out.kr(out,sig);
	}
).add;

SynthDef(
	\matrixConnectionAr,
	{
		arg in, out, base = 0, range = 1, invert = 0;
		var sig, min, max;
		min = base;
		max = (base + range).clip(-1,1);

		sig = InFeedback.ar(in,2);
		// sig = 1- sig;
		sig = sig.linlin(0,1,min, max,nil);
		OffsetOut.ar(out,sig);
	}
).add;


SynthDef(
	\matrixConnectionArKr,
	{
		arg in, out, base = 0, range = 1, invert = 0;
		var sig, min, max;
		min = base;
		max = (base + range).clip(-1,1);

		sig = InFeedback.ar(in,2);
		// sig = 1- sig;
		sig = Amplitude.ar(sig,0.16,0.16).sum;
		sig = A2K.kr(sig);
		sig = sig.linlin(0,1,min, max);

		Out.kr(out,sig);
	}
).add;

SynthDef(
	\matrixConnectionKrAr,
	{
		arg in, out, base = 0, range = 1, invert = 0;
		var sig, min, max;
		min = base;
		max = (base + range).clip(-1,1);

		sig = In.kr(in);

		sig = sig.linlin(0,1,min, max);
		sig = K2A.ar(sig).lag(ControlDur.ir);
		Out.ar(out,sig!2);
	}
).add;

SynthDef(
	\matrixConnectionArQuad,
	{
		arg in, out, base = 0, range = 1, invert = 0;
		var sig, min, max;
		min = base;
		max = (base + range).clip(-1,1);
		sig = InFeedback.ar(in,4);
		// sig = 1- sig;
		sig = sig.linlin(0,1,min, max,nil);
		OffsetOut.ar(out,sig);
	}
).add;


SynthDef(
	\matrixConnectionArKrQuad,
	{
		arg in, out, base = 0, range = 1, invert = 0;
		var sig, min, max;
		min = base;
		max = (base + range).clip(-1,1);
		sig = InFeedback.ar(in,4);
		// sig = 1- sig;
		sig = Amplitude.ar(sig,0.16,0.16).sum;
		sig = A2K.kr(sig);
		sig = sig.linlin(0,1,min, max);

		Out.kr(out,sig);
	}
).add;

SynthDef(
	\matrixConnectionKrArQuad,
	{
		arg in, out, base = 0, range = 1, invert = 0;
		var sig, min, max;
		min = base;
		max = (base + range).clip(-1,1);
		sig = In.kr(in);

		sig = sig.linlin(0,1,min, max);
		sig = K2A.ar(sig).lag(ControlDur.ir);
		OffsetOut.ar(out,sig!4);
	}
).add;
