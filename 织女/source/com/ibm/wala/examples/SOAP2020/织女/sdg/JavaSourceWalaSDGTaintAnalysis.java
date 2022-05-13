package com.ibm.wala.examples.SOAP2020.织女.sdg;

import java.util.Collection;

import com.ibm.wala.cast.java.ipa.callgraph.AstJavaSSAPropagationCallGraphBuilder;
import com.ibm.wala.cast.java.ipa.modref.AstJavaModRef;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.examples.SOAP2020.织女.EndpointFinder;
import com.ibm.wala.examples.SOAP2020.织女.JavaWalaBuilder;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.modref.ModRef;
import com.ibm.wala.ipa.slicer.NormalReturnCaller;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.Statement.Kind;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.intset.MutableMapping;

import magpiebridge.core.AnalysisConsumer;

public class JavaSourceWalaSDGTaintAnalysis extends WalaSDGTaintAnalysis {
	
	public JavaSourceWalaSDGTaintAnalysis(MutableMapping<String> uriCache) {
		super(uriCache);
	}

	public static EndpointFinder<Statement> getDeviceSource = (s) -> {
		if (s.getKind()==Kind.NORMAL_RET_CALLER) {
			MethodReference ref = ((NormalReturnCaller)s).getInstruction().getCallSite().getDeclaredTarget();
			if (ref.getName().toString().equals("getDeviceId") ||
					ref.getName().toString().equals("getPrivateId")) {
				return true;
			}
		}
	
		return false;
	};
	public static EndpointFinder<Statement> sendMessageSink = (s) -> {
		if (s.getKind()==Kind.PARAM_CALLER) {
			MethodReference ref = ((ParamCaller)s).getInstruction().getCallSite().getDeclaredTarget();
			if (ref.getName().toString().equals("sendTextMessage") ||
					ref.getName().toString().equals("broadcastSomething")) {
				return true;
			}
		}
	
		return false;
	};

	@Override
	public String source() {
		return "JavaSourceTaintDemo";
	}

	public AstJavaSSAPropagationCallGraphBuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server) throws WalaException {
	  return JavaWalaBuilder.makeBuilder(files, server);
	}

	@Override
	protected EndpointFinder<Statement> sourceFinder() {
		return JavaSourceWalaSDGTaintAnalysis.getDeviceSource;
	}

	@Override
	protected EndpointFinder<Statement> sinkFinder() {
		return JavaSourceWalaSDGTaintAnalysis.sendMessageSink;
	}

	@Override
	protected ModRef<InstanceKey> modRef() {
		return new AstJavaModRef<>();
	}

}