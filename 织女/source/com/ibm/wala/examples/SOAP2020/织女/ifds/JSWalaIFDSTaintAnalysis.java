package com.ibm.wala.examples.SOAP2020.织女.ifds;

import java.util.Collection;
import java.util.function.Function;

import com.ibm.wala.cast.ipa.callgraph.AstSSAPropagationCallGraphBuilder;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.examples.SOAP2020.织女.JSWalaBuilder;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.intset.MutableMapping;

import magpiebridge.core.AnalysisConsumer;

public class JSWalaIFDSTaintAnalysis extends WalaIFDSTaintAnalysis {	
	public JSWalaIFDSTaintAnalysis(MutableMapping<String> uriCache) {
		super(uriCache);
	}

	@Override
	public String source() {
		return "JSWalaIFDSTaintAnalysis";
	}

	@Override
	protected Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sourceFinder() {
		return (ebb) -> { 
			SSAInstruction inst = ebb.getDelegate().getInstruction();
			 if (inst instanceof SSAGetInstruction) {
				 if (((SSAGetInstruction)inst).getDeclaredField().getName().toString().equals("URL")) {
					 return true;
				 }
			 }

			 return false;
		 };
	}

	@Override
	protected Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sinkFinder() {
		return (bb) -> { 
			 SSAInstruction inst = bb.getDelegate().getInstruction();
			 if (inst instanceof SSAAbstractInvokeInstruction) {
				 for (CGNode target : CG.getPossibleTargets(bb.getNode(), ((SSAAbstractInvokeInstruction) inst).getCallSite())) {
					 if (target.getMethod().getDeclaringClass().getName().toString().contains("prototype_write")) {
						 return true;
					 }
				 }
			 }
			 
			 return false;
		 };	}

	@Override
	public AstSSAPropagationCallGraphBuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server)
			throws WalaException {
		return JSWalaBuilder.makeBuilder(files, server);
	}

}
