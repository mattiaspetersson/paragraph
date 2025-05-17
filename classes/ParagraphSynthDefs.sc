ParagraphSynthDefs { // release version
	classvar shaperBuf, <synthNames, <samplesDirectory, <params;

	*initClass {
		synthNames = [];
		params = [];
		samplesDirectory = "/Users/mattpete/work/sc/empClassLib/paragraph/samples/"; // put all samples to make available in this folder. not in use atm!
	}

	*buildGenerator {|name, func, monoOrStereo = \mono, params|
		synthNames = synthNames.add(name);
		params.do{|p| params = params.add(p)};

		SynthDef(name, {
			|out, buf, freq, gate = 1, pan, amp, atk = 0, rel = 0.9, loop = 0,
			freqlfo, panlfo, amplfo,
			synthLag = 0, lagTime = 0|
			var f = Select.ar(
				synthLag,
				[freq + freqlfo, freq.lag(lagTime) + freqlfo]
			), g = gate, a = atk, r = rel, b = buf, l = loop; // something scales the incoming lfo. seems independent of the lfo itself. happens in this synthdef?
			var env, sig, pannedOut;
			env = EnvGen.kr(Env.asr(atk, 1, rel), gate + Impulse.kr(0), doneAction: 2);

			sig = SynthDef.wrap(func, [\ar, \kr, \kr, \kr, \kr, \kr], [f, g, a, r, b, l]);

			switch(monoOrStereo,
				\mono, {pannedOut = Pan2.ar(sig, pan + panlfo)},
				\stereo, {
					pannedOut = Pan2.ar(
						sig[0],
						(pan + panlfo).linlin(0, 1, -1, 1 )
					) + Pan2.ar(
						sig[1],
						(pan + panlfo).linlin(-1, 0, -1, 1)
					)
				}
			);
			pannedOut = pannedOut * (amp + amplfo) * env;
			Out.ar(out, pannedOut);
		}, [\kr,\kr,\ar, \kr, \ar, \ar, \kr, \kr, \kr, \kr, \kr, \kr, \kr, \ar, \ar, \ar, \ar, \kr, \kr, \kr, \kr, \kr, \kr, \kr]).add;
	}

	*buildSendFx {|name, func|
		synthNames = synthNames.add(name);
		SynthDef(name, {|in, outL, outR, amp = 1, ambisonicsBus,
			azimuthL = 0.785, elevationL = 0, azimuthR = -0.785, elevationR = 0, distanceL = 5, distanceR = 5,
			ambisonicsMix = 0|

			var input, sig, ambiMix;

			ambiMix = IEnvGen.kr(Env([[0, 1], [1, 0]], [1, 1], \welch), (ambisonicsMix).lag(0.5));
			input = LeakDC.ar(In.ar(in, 2));

			sig = SynthDef.wrap(func, [\ar], [input]);

			// non-adjacent stereo outputs
			Out.ar(outL, sig[0] * ambiMix[1]);
			Out.ar(outR, sig[1] * ambiMix[1]);

			// first order ambisonics output
			Out.ar(ambisonicsBus, FoaPanB.ar(sig[0] * ambiMix[0], azimuthL, elevationL));
			Out.ar(ambisonicsBus, FoaPanB.ar(sig[1] * ambiMix[0], azimuthR, elevationR));
		}).add;
	}

	*buildInsertFx {|name, func|
		synthNames = synthNames.add((name++'Insert').asSymbol);
		SynthDef((name++'Insert').asSymbol, {|in, out, wet = 1|

			var input, sig, dryWetMix;

			dryWetMix = IEnvGen.kr(Env([[0, 1], [1, 0]], [1, 1], \welch), wet.lag(0.05));
			input = LeakDC.ar(In.ar(in, 2));

			sig = SynthDef.wrap(func, [\ar], [input]);

			//sig = (input * dryWetMix[1]) + (sig * dryWetMix[0]);
			ReplaceOut.ar(out, sig);
		}).add;
	}

	*buildLfo {|name, func|
		synthNames = synthNames.add(name);
		SynthDef(name, {|out, freq = 1, amount|
			var sig, f = freq, a = amount;
			sig = SynthDef.wrap(func, nil, [f, a]);
			Out.ar(out, sig)
		}).add;
	}

	*fx {
		^(
			extStereoFx: [
				{|input, hwInGain = 1|
					var sig;
					sig = input * hwInGain;
				},
				[]
			],

			extMonoFx: [
				{|input, hwInGain = 1|
					var sig;
					sig = input.sum * hwInGain;
					sig!2;
				},
				[]
			],

			reverb: [
				{|input, revTime = 1.8|
					var sig;
					sig = NHHall.ar(input, revTime);
				},
				[\revTime]
			],

			shuffler: [
				{|input, density = 18|
					var sigL, sigR, trig, bufL, bufR;
					bufL = LocalBuf(SampleRate.ir);
					bufR = LocalBuf(SampleRate.ir);
					RecordBuf.ar(input[0], bufL);
					RecordBuf.ar(input[1], bufR);
					trig = Dust.kr(density!2);
					sigL = TGrains.ar(
						2,
						trig[0],
						bufL,
						TChoose.kr(trig[0], [-1.01, -1, 1, 1.01]),
						0,
						TRand.kr(0.18, 0.81, trig[0]),
						TRand.kr(-1, 1, trig[0]),
						0.3,
					);
					sigR = TGrains.ar(
						2,
						trig[1],
						bufR,
						TChoose.kr(trig[1], [-1.01, -1, 1, 1.01]),
						0,
						TRand.kr(0.18, 0.81, trig[1]),
						TRand.kr(-1, 1, trig[1]),
						0.3,
					);
					sigL+sigR;
				},
				[\density]
			],

			karplusStrong: [
				{|input,
					t_trig = 0, plkFrq = 18, plkDcyTime = 9|
					var pluck;
					pluck = Pluck.ar(input, t_trig, 1, plkFrq.reciprocal, plkDcyTime);
					pluck!2;
				},
				[\plkFrq, \plkDcyTime]
			]
		);
	}

	*generators {
		^(
			sine: [
				{|freq, gate, atk, rel, buf, loop,
					baseFreq|
					SinOsc.ar(freq + baseFreq + SinOsc.ar(\fmFrq.kr(0), 0, \fmAmt.kr(0)), 0, 0.2);
				},
				\mono,
				[\fmFrq, \fmAmt] // list of params to expose for the language
			],

			sineFb: [
				{|freq, gate, atk, rel, buf, loop,
					fb|
					SinOscFB.ar(freq, fb, 0.2);
				},
				\mono,
				//[\fb] // list of params to expose for the language
			],

			saw: [
				{|freq, gate, atk, rel, buf, loop,
					baseFreq, modFreq, modAmount|
					Saw.ar(freq + baseFreq + SinOsc.ar(modFreq, 0, modAmount), 0.2);
				}, \mono
			],

			trigBufMono: [
				{|freq, gate, atk, rel, buf, loop,
					reverse|
					var sig, r = (freq.cpsmidi - 60).midiratio * BufRateScale.kr(buf);
					sig = PlayBuf.ar(
						1,
						buf,
						Select.kr(reverse, [r, r * -1]),
						startPos: (BufFrames.kr(buf)-2) * reverse,
						loop: loop
					);
					sig;
				}, \mono
			],

			trigBufStereo: [
				{|freq, gate, atk, rel, buf, loop,
					reverse|
					var sig, r = (freq.cpsmidi - 60).midiratio * BufRateScale.kr(buf);
					sig = PlayBuf.ar(
						2,
						buf,
						Select.kr(reverse, [r, r * -1]),
						startPos: (BufFrames.kr(buf)-2) * reverse,
						loop: loop
					);
					sig;
				}, \stereo
			],

			/*vsti: [
				{|freq, gate, atk, rel, buf, loop|
					VSTPlugin.ar(nil, 2);
				}, \stereo
			],*/

			\hwInputStereo: [
				{|freq, gate, atk, rel, buf, loop,
					inBus|
					SoundIn.ar([inBus, inBus+1]);
				}, \stereo
			],

			\hwInputMono: [
				{|freq, gate, atk, rel, buf, loop,
					inBus|
					SoundIn.ar([inBus]);
				}, \mono
			]
		);
	}

	*lfos {
		^(
			lfo_sin: {|freq = 1, amount|
				SinOsc.ar(freq, 0, amount);
			},

			lfo_tri: {|freq = 1, amount|
				LFTri.ar(freq, 0, amount);
			},

			lfo_sqr: {|freq = 1, amount|
				LFPulse.ar(freq, 0, 0.5, amount);
			},

			lfo_saw: {|freq = 1, amount|
				LFSaw.ar(freq, 0, amount);
			},

			lfo_noise0: {|freq = 1, amount|
				LFDNoise0.ar(freq, amount);
			},

			lfo_noise1: {|freq = 1, amount|
				LFDNoise1.ar(freq, amount);
			}
		);
	}

	// not in use atm!
	*samples {|aParagraphMain|
		var dict = aParagraphMain.samples, server = aParagraphMain.server;
		(ParagraphSynthDefs.samplesDirectory++"*").pathMatch.collect{|folder|
			(folder++"*").pathMatch.collect{|file|
				if(file.isSoundFile, {
					dict[(PathName(file).fileNameWithoutExtension.replace(" ", "")).asSymbol] = Buffer.read(server, file);
				});
			};
		};
	}

	*patternMasterSynthDefs {|ptnName|
		SynthDef(('patternMaster'++ptnName).asSymbol, {
			|in, outL, outR, amp = 1, recOut, sc = 0, scIn, scOut, latency = 0,
			fxOut,
			ambisonicsBus, azimuthL = 0.785, elevationL = 0, azimuthR = -0.785, elevationR = 0, distanceL = 5, distanceR = 5,
			ambisonicsMix = 0,

			//compressor, filter, cross synthesis and saturation
			compressorThreshold = 1, compressorSlopeAbove = 1, compressorAttack = 0.01, compressorRelease = 0.1,
			filter = 1, hpfFreq = 20, hpfRes = 0.1, hpfFmAmt = 0,
			lpfFreq = 16000, lpfRes = 0.1, lpfFmAmt = 0,
			delTime = 0.5, delFbTime = 3, del = 0, beatDur = 1,
			crossSynthesis = 0, saturation = 1,

			//lfos
			amplfo, azimuthLlfo, azimuthRlfo, elevationLlfo, elevationRlfo, //distanceLlfo, distanceRlfo,
			hpfFreqlfo, hpfReslfo, lpfFreqlfo, lpfReslfo, crossSynthesislfo, saturationlfo, vol = 1|

			var input, sig, sideChainInput, scEnvFol, satVal, extInput, ambiMix, local;

			ambiMix = IEnvGen.kr(Env([[0, 1], [1, 0]], [1, 1], \welch), (ambisonicsMix).lag(0.5));
			input = In.ar(in, 2) * vol.lag(0.05);
			sideChainInput = In.ar(scIn, 2);
			scEnvFol = Amplitude.kr(sideChainInput, 0.1, 0.1, 1000);

			SendPeakRMS.kr(input, 10, 3, ('/levels'++ptnName).asSymbol); // for visualizing levels. not used atm!

			// modulation between channels and compressor with sidechain
			sig = Compander.ar(
				SelectX.ar((crossSynthesislfo + crossSynthesis).clip(0.0, 1.0), [input, (input * sideChainInput)]), //rm. add convolution, spectral tricks etc...
				Select.ar(sc, [input, sideChainInput]), //SelectX here instead to crossfade between input and sidechain?
				compressorThreshold,
				1,
				compressorSlopeAbove,
				compressorAttack,
				compressorRelease
			);

			// filters
			sig = Select.ar(filter, [
				sig,
				RLPF.ar( // hipass filter going into lopass filter
					RHPF.ar(
						sig,
						(hpfFreqlfo + hpfFreq.lag(0.01) - (scEnvFol * hpfFmAmt)).clip(20, 20000),
						(hpfReslfo + hpfRes.lag(0.01)).clip(0.0001, 1.0)
					),
					(lpfFreqlfo + lpfFreq.lag(0.01) + (scEnvFol * lpfFmAmt)).clip(20, 20000),
					(lpfReslfo + lpfRes.lag(0.01)).clip(0.0001, 1.0)
				)
			]);

			// beat synced delay
			sig = XFade2.ar(sig, CombL.ar(sig, 4, (beatDur * delTime).clip(0, 4), (beatDur * delFbTime)), del.linlin(0, 1, -1, 1));

			// latency compensation if working with outboard fx
			sig = DelayL.ar(sig, 1, latency);

			// saturation
			satVal = (saturationlfo + saturation.lag(0.1)).clip(1.0, 99.0);
			sig = (sig * satVal).tanh / (satVal ** 0.6); // formula by James Harkins (satVal can't be 0!)
			sig = sig * (amp + amplfo);

			Out.ar(scOut, sig);
			Out.ar(fxOut, sig);

			// non-adjacent stereo outputs for multi-channel setups
			Out.ar(outL, sig[0] * ambiMix[1]);
			Out.ar(outR, sig[1] * ambiMix[1]);

			// first order ambisonics output
			Out.ar(ambisonicsBus, FoaPanB.ar(sig[0] * ambiMix[0], azimuthL + azimuthLlfo, elevationL + elevationLlfo));
			Out.ar(ambisonicsBus, FoaPanB.ar(sig[1] * ambiMix[0], azimuthR + azimuthRlfo, elevationR + elevationRlfo));

			// record the stereo output of the channel. for ambisonics recording; use the master out recording in ParagraphMain.
			Out.ar(recOut, sig);
		}).add;

		SynthDef('fxSend', {|inBus, outBus, lvl|
			Out.ar(outBus, In.ar(inBus, 2) * (lvl.lag(0.01)).clip(0, 1));
		}).add;
	}

	*masterSynthDef {|decoder|
		// the master synth sums all channels on the stereo buses as well as on the ambisonics bus
		SynthDef('master', {|foaIn, out, vol = 1, recOut, mRevTime = 1.8, mRevWet = 0.3|
			var stInput, foaInput, outSig, reverb, wdMix;
			stInput = In.ar(0, 32); //limited to 32 hw channels
			stInput = HPF.ar(stInput, 30);
			stInput = Limiter.ar(stInput * 0.5 * vol.lag(0.05));
			foaInput = In.ar(foaIn, 4); //1st order = 4 channels
			foaInput = FoaDecode.ar(foaInput, decoder);
			foaInput = Limiter.ar(foaInput * 0.5 * vol.lag(0.05));
			outSig = if(decoder.numChannels > stInput.size,
				{foaInput + stInput.extend(foaInput.size, 0)},
				{stInput + foaInput.extend(stInput.size, 0)}
			);
			//SendPeakRMS.kr(outSig, 10, 3, '/mainLevel'); //TODO: better level meter
			ReplaceOut.ar(out, outSig);
			Out.ar(recOut, outSig);
		}).add;
	}
}