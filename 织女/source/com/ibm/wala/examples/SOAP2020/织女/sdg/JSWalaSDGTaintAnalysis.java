package com.ibm.wala.examples.SOAP2020.织女.sdg;

import java.util.Collection;

import com.ibm.wala.cast.ipa.callgraph.AstSSAPropagationCallGraphBuilder;
import com.ibm.wala.cast.js.ipa.modref.JavaScriptModRef;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.examples.SOAP2020.织女.EndpointFinder;
import com.ibm.wala.examples.SOAP2020.织女.JSWalaBuilder;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.modref.ModRef;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamCallee;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.Statement.Kind;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.intset.MutableMapping;

import magpiebridge.core.AnalysisConsumer;

public class JSWalaSDGTaintAnalysis extends WalaSDGTaintAnalysis {

	public JSWalaSDGTaintAnalysis(MutableMapping<String> uriCache) {
		super(uriCache);
	}

	@Override
	public String source() {
		return "JSWalaSDGTaintDemo";
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

	@Override
	public AstSSAPropagationCallGraphBuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server)
			throws WalaException {
		return JSWalaBuilder.makeBuilder(files, server);
	}

}