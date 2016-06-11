package tutorial.examples;

import java.util.List;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.slicer.NormalReturnCaller;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamCallee;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.Statement.Kind;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.graph.traverse.BFSPathFinder;

public class Analysis {

	interface EndpointFinder {
		
		boolean endpoint(Statement s);
		
	}
	
	public static EndpointFinder sendMessageSink = new EndpointFinder() {
		@Override
		public boolean endpoint(Statement s) {
			if (s.getKind()==Kind.PARAM_CALLER) {
				MethodReference ref = ((ParamCaller)s).getInstruction().getCallSite().getDeclaredTarget();
				if (ref.getName().toString().equals("sendTextMessage")) {
					return true;
				}
			}

			return false;
		}
	};

	public static EndpointFinder documentWriteSink = new EndpointFinder() {
		@Override
		public boolean endpoint(Statement s) {
			if (s.getKind()==Kind.PARAM_CALLEE) {
				String ref = ((ParamCallee)s).getNode().getMethod().toString();
				if (ref.equals("<Code body of function Lpreamble.js/DOMDocument/Document_prototype_write>")) {
					return true;
				}
			}

			return false;
		}
	};

	public static EndpointFinder getDeviceSource = new EndpointFinder() {
		@Override
		public boolean endpoint(Statement s) {
			if (s.getKind()==Kind.NORMAL_RET_CALLER) {
				MethodReference ref = ((NormalReturnCaller)s).getInstruction().getCallSite().getDeclaredTarget();
				if (ref.getName().toString().equals("getDeviceId")) {
					return true;
				}
			}

			return false;
		}
	};

	public static EndpointFinder documentUrlSource = new EndpointFinder() {
		@Override
		public boolean endpoint(Statement s) {
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
		}
	};
	
	public static Set<List<Statement>> getPaths(SDG<InstanceKey> G, EndpointFinder sources, EndpointFinder sinks) {
		Set<List<Statement>> result = HashSetFactory.make();
		for(Statement src : G) {
			if (sources.endpoint(src)) {
				for(Statement dst : G) {
					if (sinks.endpoint(dst)) {
						BFSPathFinder<Statement> paths = new BFSPathFinder<Statement>(G, src, dst);
						List<Statement> path = paths.find();
						if (path != null) {
							result.add(path);
						}
					}
				}
			}
		}
		return result;
	}
}
