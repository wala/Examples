package com.ibm.wala.examples.SOAP2020.织女.ifds;

import java.util.Collection;

import com.ibm.wala.cast.js.ipa.callgraph.JSCFABuilder;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.examples.SOAP2020.织女.HTMLWalaBuilder;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.intset.MutableMapping;

import magpiebridge.core.AnalysisConsumer;

public class JSHtmlWalaIFDSTaintAnalysis extends JSWalaIFDSTaintAnalysis {
	public JSHtmlWalaIFDSTaintAnalysis(MutableMapping<String> uriCache) {
		super(uriCache);
	}

	@Override
	public String source() {
		return "JSHtmlIFDSTaintDemo";
	}

	public JSCFABuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server) throws WalaException {
		return HTMLWalaBuilder.makeBuilder(files, server);
	}
}