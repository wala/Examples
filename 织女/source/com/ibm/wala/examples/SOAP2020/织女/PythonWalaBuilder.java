package com.ibm.wala.examples.SOAP2020.织女;

import java.io.IOException;
import java.util.Collection;

import com.ibm.wala.cast.python.client.PythonAnalysisEngine;
import com.ibm.wala.cast.python.ipa.callgraph.PythonSSAPropagationCallGraphBuilder;
import com.ibm.wala.cast.python.types.PythonTypes;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.core.util.strings.Atom;

import magpiebridge.core.AnalysisConsumer;

public class PythonWalaBuilder {

	public static PythonSSAPropagationCallGraphBuilder makeBuilder(Collection<? extends Module> files,
			AnalysisConsumer server) throws WalaException {
		PythonAnalysisEngine<?> E = new PythonAnalysisEngine<Void>() {
			@Override
			public Void performAnalysis(PropagationCallGraphBuilder builder) throws CancelException {
				assert false;
				return null;
			}
		};

		E.setModuleFiles(files);
		
		try {
			return E.defaultCallGraphBuilder();
		} catch (IllegalArgumentException | IOException e) {
			throw new WalaException("WALA error", e);
		}
	}

	public static final MethodReference flask = 
	MethodReference.findOrCreate(
			TypeReference.findOrCreate(PythonTypes.pythonLoader, "Lflask"),
			new Selector(
					Atom.findOrCreateUnicodeAtom("import"),
					Descriptor.findOrCreateUTF8("()Lflask;")));
}
