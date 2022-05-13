package com.ibm.wala.examples.SOAP2020.织女.ifds;

import java.util.Collection;
import java.util.function.Function;

import com.ibm.wala.cast.python.ipa.callgraph.PythonSSAPropagationCallGraphBuilder;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.examples.SOAP2020.织女.PythonWalaBuilder;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.AllocationSiteInNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.intset.MutableMapping;
import com.ibm.wala.util.intset.OrdinalSet;

import magpiebridge.core.AnalysisConsumer;

public class PythonWalaIFDSTaintAnalysis extends WalaIFDSTaintAnalysis {

	public PythonWalaIFDSTaintAnalysis(MutableMapping<String> uriCache) {
		super(uriCache);
	}

	@Override
	public String source() {
		return "PytonWalaIFDSTaintAnalysis";
	}

	@Override
	protected Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sourceFinder() {
		PointerAnalysis<? super InstanceKey> ptrs = builder.getPointerAnalysis();
		return (s) -> {
			SSAInstruction inst = s.getDelegate().getInstruction();
			if (inst instanceof SSAGetInstruction) {
				LocalPointerKey objKey = new LocalPointerKey(s.getNode(), inst.getUse(0));
				OrdinalSet<? super InstanceKey> objs = ptrs.getPointsToSet(objKey);
				for(Object x : objs) {
					if (x instanceof AllocationSiteInNode) {
						AllocationSiteInNode xx = (AllocationSiteInNode)x;
						if (xx.getNode().getMethod().getReference().equals(PythonWalaBuilder.flask) &&
							xx.getSite().getProgramCounter() == 5) {
							return true;
						}
					}
				}
			} 
			
			return false;
		};
	}

	@Override
	protected Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sinkFinder() {
		return (s) -> {
			SSAInstruction inst = s.getDelegate().getInstruction();
			if (inst instanceof SSAAbstractInvokeInstruction) {
				CallSiteReference cs = ((SSAAbstractInvokeInstruction)s.getDelegate().getInstruction()).getCallSite();
				for(CGNode callee : CG.getPossibleTargets(s.getNode(), cs)) {
					if (callee.getMethod().getReference().toString().contains("subprocess/function/call")) {	
						return true;
					}
				}
			}
		
			return false;
		};	
	}

	@Override
	public PythonSSAPropagationCallGraphBuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server)
			throws WalaException {
		return PythonWalaBuilder.makeBuilder(files, server);
	}

}
