ParagraphPbindefs { // release version

	*make {|aParagraphMain, aParagraphPattern|
		var paragraphMain = aParagraphMain;
		var paragraphPattern = aParagraphPattern;
		var server = paragraphMain.server;
		var patternName = paragraphPattern.patternName;
		var generatorsGroup = paragraphPattern.generatorsGroup;
		var patternMasterBus = paragraphPattern.patternMasterBus;
		var patternMasterSynth = paragraphPattern.patternMasterSynth;
		var lastNodes = paragraphPattern.lastNodes;
		var buffers = paragraphPattern.buffers;
		var extMidiInstrSynths = paragraphPattern.extMidiInstrSynths;
		var vstInstr = paragraphPattern.vstInstr;
		var oscAddr = paragraphMain.oscAddr;


		Pbindef(patternName,
			// subdivide the dur into n events
			// use ~dup to compensate for the subdivisions by Psubdivide to keep the param values for the whole \dur
			\div, Pdef((patternName++\prdiv).asSymbol, {Pn(~pattern ? 1)}),
			// the prob and logical values are used in the \instrument Pfunc
			\prob, Pdef((patternName++\prprob).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),
			\logical, Pdef((patternName++\prlogical).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),
			\group, generatorsGroup,
			\out, patternMasterBus,

			\root, Pdef((patternName++\prroot).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\scale, Pdef((patternName++\prscale).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? Scale.melodicMinor)}),
			\tuning, Pdef((patternName++\prtuning).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? Tuning.et12)}),
			\mtranspose, Pdef((patternName++\prmtranspose).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\ctranspose, Pdef((patternName++\prctranspose).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\degree, Pdef((patternName++\prdegree).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\dur, Pdef((patternName++\prdur).asSymbol, {Psubdivide(Pfunc{|ev| ev[\div]}, ~pattern ? 0.25)}),
			\val, Pdef((patternName++\prval).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			// this return names of syntDefs, buffers or defined midi or vst instruments, handled by the \instrument Pfunc
			\prInstr, Pdef((patternName++\prinstrument).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? \sine)}),
			\buf, Pfunc{|ev| buffers[ev[\prInstr]] ? 0},
			\instrument, Pfunc{|ev|
				var name = ev[\prInstr];
				if(buffers[name].notNil, {
					ev[\type] = [\note, \rest].wchoose([ev[\prob], 1 - ev[\prob]]);
					if(buffers[name].numChannels == 1, {
						\trigBufMono;
					}, {
						\trigBufStereo;
					});
				}, {
					if(SynthDescLib.global[name].notNil, {
						ev[\type] = [\note, \rest].wchoose([ev[\prob], 1 - ev[\prob]]);
						ev[\prInstr];
					}, {
						if(paragraphMain.extMidiInstr[name].notNil, {
							if(paragraphMain.extMidiInstr[name][\hwIn].notNil, {
								if(extMidiInstrSynths[name].isNil, {
									if(paragraphMain.extMidiInstr[name][\hwIn] != \audioMovers, {
										extMidiInstrSynths[name] = Synth(
											if(paragraphMain.extMidiInstr[name][\numChannels] == \stereo,
												{\hwInputStereo}, {\hwInputMono}
											),
											[
												\inBus, paragraphMain.extMidiInstr[name][\hwIn],
												\out, patternMasterBus,
												\amp, paragraphMain.extMidiInstr[name][\hwInGain]
											],
											generatorsGroup);
									}, {
										fork{
											extMidiInstrSynths[name] = Synth(\vsti, [
												\out, patternMasterBus,
												\amp, paragraphMain.extMidiInstr[name][\hwInGain]
											], generatorsGroup);
											server.sync;
											paragraphMain.extMidiInstr[name][\listenToReceiver] = VSTPluginController(extMidiInstrSynths[name]);
											paragraphMain.extMidiInstr[name][\listenToReceiver].open("Listento-Receiver", editor: true);
											server.sync;
											paragraphMain.extMidiInstr[name][\listenToReceiver].editor;
										};
									});
								});
							});
							ev[\type] = [\midi, \rest].wchoose([ev[\prob], 1 - ev[\prob]]);
							ev[\midicmd] = \noteOn;
							ev[\midiout] = paragraphMain.extMidiInstr[name][\midiOutPort];
							ev[\prInstr]; // dummy return
						}, {
							if(vstInstr[name].notNil, {
								vstInstr[name][\vstSynth].synth.moveToHead(generatorsGroup);
								vstInstr[name][\vstSynth].synth.set(
									\out,
									patternMasterBus,
									\amp,
									ev.use{~amp.value},
									\pan,
									ev[\pan]
								);
								ev[\type] = [\midi, \rest].wchoose([ev[\prob], 1 - ev[\prob]]);
								ev[\midicmd] = \noteOn;
								ev[\midiout] = vstInstr[name][\midiOutPort][0];
								ev[\chan] = vstInstr[name][\midiOutPort][1];
							}, {
								if(oscAddr[name].notNil, {
									ev[\type] = [\set, \rest].wchoose([ev[\prob], 1 - ev[\prob]]);
									ev.use{~midinote.value}.asArray.do{|n|
										n = n.asInteger;
										if(ev[\type] == \set and: {oscAddr[name][\type] == \note}, {
											fork{
												oscAddr[name][\netAddr].sendMsg(
													oscAddr[name][\path],
													ev.use{~amp.value},
													n
												);
												(ev.use{~dur.value} * ev.use{~legato.value}).wait;
												oscAddr[name][\netAddr].sendMsg(oscAddr[name][\path], 0.0, n);
											};
										});

										if(ev[\type] == \set and: {oscAddr[name][\type] == \ctrl}, {
											oscAddr[name][\netAddr].sendMsg(
												oscAddr[name][\path],
												*(ev.val) // expand arrays as separate values
											);
										});
									};
								}, {
									ev[\type] = \rest;
								});
							});
						});
					});
				});
			},
			\chan, Pdef((patternName++\prchan).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),

			\divEnv, Pseg(
				Pseq([1, 0], inf),
				Pseq([Pfuncn{|ev| ev[\div] * ev[\dur]}, 0], inf),
				\lin
			),

			\prAmp, Pdef((patternName++\pramp).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0.1)}),
			\amp, Pfunc{|ev| ev[\prAmp] * ev[\divEnv]},
			\pan, Pdef((patternName++\prpan).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\legato, Pdef((patternName++\prlegato).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0.8)}),
			\stretch, Pdef((patternName++\prstretch).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),
			\reverse, Pdef((patternName++\prreverse).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\atk, Pdef((patternName++\pratk).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\rel, Pdef((patternName++\prrel).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0.9)}),

			// lfos
			\freqlfo, 0,
			\amplfo, 0,
			\panlfo, 0,

			\post, Pfunc{|ev|
				if(paragraphMain.posts, {
					{
						server.latency.wait;
						if(ev[\type] != \rest, {
							(
								patternName ++ ": "
								++ev[\prInstr]++" "
								++ev[\dur]++" "
								++ev[\degree]++" "
								++ev[\amp]++" "
							).post;
						}, {"".postln});
					}.fork(AppClock);
				});

				this.changed(patternName, ev);
			},
			\callback, {|ev|
				lastNodes = ev[\id];
			}
		);

		// a global pattern to set params on the master synth
		Pbindef((patternName++"global").asSymbol,
			\type, \set,
			\id, patternMasterSynth.nodeID,
			\args, #[
				\outL, \outR, \azimuthL, \elevationL, \azimuthR, \elevationR, \distanceL, \distanceR, \ambisonicsMix,
				\compressorThreshold, \compressorSlopeAbove, \compressorAttack, \compressorRelease,
				\filter, \hpfFreq, \hpfRes, \hpfFmAmt,
				\lpfFreq, \lpfRes, \lpfFmAmt,
				\crossSynthesis, \saturation,
				\amplfo, \azimuthLlfo, \azimuthRlfo, \elevationLlfo, \elevationRlfo,
				\hpfFreqlfo, \hpfReslfo, \lpfFreqlfo, \lpfReslfo,
				\del, \delTime, \delFbTime, \beatDur,
				\crossSynthesislfo, \saturationlfo,
				\hush
			],
			// div is shared with main pattern. I.e. set with the div method together with the pattern.
			\div, Pdef((patternName++'global'++\prdiv).asSymbol, {Pn(~pattern ? 1)}),
			\outL, Pdef((patternName++\proutL).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\outR, Pdef((patternName++\proutR).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),
			\azimuthL, Pdef((patternName++\prazimuthL).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0.785)}),
			\azimuthR, Pdef((patternName++\prazimuthR).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? -0.785)}),
			\elevationL, Pdef((patternName++\prelevationL).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\elevationR, Pdef((patternName++\prelevationR).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\distanceL, Pdef((patternName++\prdistanceL).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 5)}),
			\distanceR, Pdef((patternName++\prdistanceL).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 5)}),
			\ambisonicsMix, Pdef((patternName++\prambisonicsMix).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\compressorThreshold, Pdef((patternName++\prcompressorThreshold).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),
			\compressorSlopeAbove, Pdef((patternName++\prcompressorSlopeAbove).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),
			\compressorAttack, Pdef((patternName++\prcompressorAttack).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0.01)}),
			\compressorRelease, Pdef((patternName++\prcompressorRelease).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0.1)}),
			\filter, 1,
			\hpfFreq, Pdef((patternName++\prhpfFreq).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 20)}),
			\hpfRes, Pdef((patternName++\prhpfRes).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1.0)}),
			\hpfFmAmt, Pdef((patternName++\prhpfFmAmt).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\lpfFreq, Pdef((patternName++\prlpfFreq).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 20000)}),
			\lpfRes,  Pdef((patternName++\prlpfRes).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1.0)}),
			\lpfFmAmt, Pdef((patternName++\prlpfFmAmt).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),

			\beatDur, Pfunc{paragraphMain.tempoClock.beatDur},
			\delTime, Pdef((patternName++\prdelTime).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0.5)}),
			\delFbTime, Pdef((patternName++\prdelFbTime).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 3)}),
			\del, Pdef((patternName++\prdel).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\crossSynthesis, Pdef((patternName++\prcrossSynthesis).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 0)}),
			\saturation, Pdef((patternName++\prsaturation).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),

			// shared values with the main pattern.
			\dur, Pdef((patternName++'global'++\prdur).asSymbol, {Psubdivide(Pfunc{|ev| ev[\div]}, ~pattern ? 0.25)}),
			\stretch, Pdef((patternName++'global'++\prstretch).asSymbol, {Pdup(Pfunc{|ev| ev[\div]}, ~pattern ? 1)}),

			//lfos
			\amplfo, 0,
			\azimuthLlfo, 0,
			\azimuthRlfo, 0,
			\elevationLlfo, 0,
			\elevationRlfo, 0,
			//\distanceLlfo, 0,
			//\distanceRlfo, 0,
			\hpfFreqlfo, 0,
			\hpfReslfo, 0,
			\lpfFreqlfo, 0,
			\lpfReslfo, 0,
			\crossSynthesislfo, 0,
			\saturationlfo, 0
		);
	}
}