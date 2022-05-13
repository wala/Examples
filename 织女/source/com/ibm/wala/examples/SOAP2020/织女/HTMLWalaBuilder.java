package com.ibm.wala.examples.SOAP2020.织女;

import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

import com.ibm.wala.cast.js.ipa.callgraph.JSCFABuilder;
import com.ibm.wala.cast.js.util.JSCallGraphBuilderUtil;
import com.ibm.wala.cast.js.util.JSCallGraphBuilderUtil.CGBuilderType;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.ModuleEntry;
import com.ibm.wala.classLoader.SourceFileModule;
import com.ibm.wala.util.WalaException;

import magpiebridge.core.AnalysisConsumer;

public class HTMLWalaBuilder {

	public static JSCFABuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server) throws WalaException {
		assert files.size() == 1;
		ModuleEntry module = files.iterator().next().getEntries().next();
		try {
			URL url = ((SourceFileModule)module).getFile().toURI().toURL();
			return JSCallGraphBuilderUtil.makeHTMLCGBuilder(url, CGBuilderType.ZERO_ONE_CFA_WITHOUT_CORRELATION_TRACKING, new InputStreamReader(module.getInputStream()));
		} catch (MalformedURLException e) {
			assert false : e;
			return null;
		}
	}

}
