package com.ibm.wala.examples.SOAP2020.织女;

import java.util.Collection;

import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.js.ipa.callgraph.JSCFABuilder;
import com.ibm.wala.cast.js.ipa.modref.JavaScriptModRef;
import com.ibm.wala.cast.js.loader.JavaScriptLoaderFactory;
import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.cast.js.util.JSCallGraphBuilderUtil;
import com.ibm.wala.cast.js.util.JSCallGraphBuilderUtil.CGBuilderType;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.modref.ModRef;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamCallee;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.Statement.Kind;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;

import magpiebridge.core.AnalysisConsumer;

public class JSWalaTaintAnalysis extends WalaTaintAnalysis {

	@Override
	public String source() {
		return "JSTaintDemo";
	}

	protected JSCFABuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server) throws WalaException {
		Collection<Module> m2 = HashSetFactory.make();
		files.forEach((m) -> m2.add(m));
		m2.add(JSCallGraphBuilderUtil.getPrologueFile("prologue.js"));
		Module[] modules = m2.toArray(new Module[files.size()]);
		JavaScriptLoaderFactory loaders = new JavaScriptLoaderFactory(new CAstRhinoTranslatorFactory());
		JSCFABuilder builder = JSCallGraphBuilderUtil.makeCGBuilder(loaders, modules, CGBuilderType.ONE_CFA, AstIRFactory.makeDefaultFactory());
		return builder;
	}

	@Override
	protected EndpointFinder<Statement> sourceFinder() {
		return (s) -> {
			if (s.getKind()==Kind.NORMAL) {
				NormalStatement ns = (NormalStatement) s;
				SSAInstruction inst = ns.getInstruction();
				if (inst instanceof SSAGetInstruction) {
					if (((SSAGetInstruction)inst).getDeclaredField().getName().toString().equals("URL")) {
						return true;
					}
				}
			}
		
			return false;
		};
	}

	@Override
	protected EndpointFinder<Statement> sinkFinder() {
		return (s) -> {
			if (s.getKind()==Kind.PARAM_CALLEE) {
				String ref = ((ParamCallee)s).getNode().getMethod().toString();
				if (ref.contains("Document_prototype_write")) {	
					return true;
				}
			}
		
			return false;
		};
	}

	@Override
	protected ModRef<InstanceKey> modRef() {
		return new JavaScriptModRef<>();
	}

}