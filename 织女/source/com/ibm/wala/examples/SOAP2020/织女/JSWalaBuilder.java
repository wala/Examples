package com.ibm.wala.examples.SOAP2020.织女;

import java.util.Collection;

import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.js.ipa.callgraph.JSCFABuilder;
import com.ibm.wala.cast.js.loader.JavaScriptLoaderFactory;
import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.cast.js.util.JSCallGraphBuilderUtil;
import com.ibm.wala.cast.js.util.JSCallGraphBuilderUtil.CGBuilderType;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;

import magpiebridge.core.AnalysisConsumer;

public class JSWalaBuilder {

	public static JSCFABuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server) throws WalaException {
		Collection<Module> m2 = HashSetFactory.make();
		files.forEach((m) -> m2.add(m));
		m2.add(JSCallGraphBuilderUtil.getPrologueFile("prologue.js"));
		Module[] modules = m2.toArray(new Module[files.size()]);
		JavaScriptLoaderFactory loaders = new JavaScriptLoaderFactory(new CAstRhinoTranslatorFactory());
		JSCFABuilder builder = JSCallGraphBuilderUtil.makeCGBuilder(loaders, modules, CGBuilderType.ONE_CFA, AstIRFactory.makeDefaultFactory());
		return builder;
	}
	
}
