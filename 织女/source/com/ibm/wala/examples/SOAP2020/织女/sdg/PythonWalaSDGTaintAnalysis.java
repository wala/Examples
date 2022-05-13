package com.ibm.wala.examples.SOAP2020.织女.sdg;

import java.util.Collection;

import com.ibm.wala.cast.python.ipa.callgraph.PythonSSAPropagationCallGraphBuilder;
import com.ibm.wala.cast.python.modref.PythonModRef;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.examples.SOAP2020.织女.EndpointFinder;
import com.ibm.wala.examples.SOAP2020.织女.PythonWalaBuilder;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.AllocationSiteInNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.modref.ModRef;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.intset.MutableMapping;
import com.ibm.wala.util.intset.OrdinalSet;

import magpiebridge.core.AnalysisConsumer;

public class PythonWalaSDGTaintAnalysis extends WalaSDGTaintAnalysis {
	public PythonWalaSDGTaintAnalysis(MutableMapping<String> uriCache) {
		super(uriCache);
	}

	private PythonSSAPropagationCallGraphBuilder builder;
	
	@Override
	public String source() {
		return "WALA Python SDG taint";
	}

	@Override
	protected EndpointFinder<Statement> sourceFinder() {
		PointerAnalysis<? super InstanceKey> ptrs = builder.getPointerAnalysis();
		return (s) -> {
			if (s.getKind()==Statement.Kind.NORMAL) {
				NormalStatement ns = (NormalStatement) s;
				SSAInstruction inst = ns.getInstruction();
				if (inst instanceof SSAGetInstruction) {
					LocalPointerKey objKey = new LocalPointerKey(ns.getNode(), inst.getUse(0));
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
			}
			return false;
		};		
	}

	@Override
	protected EndpointFinder<Statement> sinkFinder() {
		CallGraph CG = builder.getCallGraph();
		return (s) -> {
			if (s.getKind()==Statement.Kind.PARAM_CALLER) {
				CallSiteReference cs = ((ParamCaller)s).getInstruction().getCallSite();
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
	protected ModRef<InstanceKey> modRef() {
		return new PythonModRef();
	}

	@Override
	public PythonSSAPropagationCallGraphBuilder makeBuilder(Collection<? extends Module> files,
			AnalysisConsumer server) throws WalaException {
		return builder = PythonWalaBuilder.makeBuilder(files, server);
	}
}