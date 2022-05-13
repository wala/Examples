package com.ibm.wala.examples.SOAP2020.织女.ifds;

import java.util.Collection;
import java.util.function.Function;

import com.ibm.wala.cast.ipa.callgraph.AstSSAPropagationCallGraphBuilder;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.examples.SOAP2020.织女.JavaWalaBuilder;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.intset.MutableMapping;

import magpiebridge.core.AnalysisConsumer;

public class JavaSourceWalaIFDSTaintAnalysis extends WalaIFDSTaintAnalysis {

	public JavaSourceWalaIFDSTaintAnalysis(MutableMapping<String> uriCache) {
		super(uriCache);
	}

	@Override
	public String source() {
		return "JavaWalaIFDSTaintAnalysis";
	}

	@Override
	protected Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sourceFinder() {
		return (s) -> {
			SSAInstruction inst = s.getDelegate().getInstruction();
			if (inst instanceof SSAAbstractInvokeInstruction) {
				MethodReference ref = ((SSAAbstractInvokeInstruction)inst).getCallSite().getDeclaredTarget();
				if (ref.getName().toString().equals("getDeviceId") ||
						ref.getName().toString().equals("getPrivateId")) {
					return true;
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
				MethodReference ref = ((SSAAbstractInvokeInstruction)inst).getCallSite().getDeclaredTarget();
				if (ref.getName().toString().equals("sendTextMessage") ||
						ref.getName().toString().equals("broadcastSomething")) {
					return true;
				}
			}
		
			return false;
		};
	}

	@Override
	public AstSSAPropagationCallGraphBuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server)
			throws WalaException {
		return JavaWalaBuilder.makeBuilder(files, server);
	}

}
