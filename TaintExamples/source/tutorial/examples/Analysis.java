package tutorial.examples;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.ibm.wala.ipa.slicer.NormalReturnCaller;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamCallee;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.Statement.Kind;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.NonNullSingletonIterator;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.traverse.BFSPathFinder;

public class Analysis {

	@FunctionalInterface
	interface EndpointFinder<T> {
		
		boolean endpoint(T s);
		
	}
	
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

	public static EndpointFinder<Statement> documentWriteSink = (s) -> {
		if (s.getKind()==Kind.PARAM_CALLEE) {
			String ref = ((ParamCallee)s).getNode().getMethod().toString();
			if (ref.contains("Document_prototype_write")) {	
				return true;
			}
		}
	
		return false;
	};

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

	public static EndpointFinder<Statement> documentUrlSource = (s) -> {
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
	
	public static <T> Set<List<T>> getPaths(Graph<T> G, EndpointFinder<T> sources, EndpointFinder<T> sinks) {
		Set<List<T>> result = HashSetFactory.make();
		for(T src : G) {
			if (sources.endpoint(src)) {
				for(final T dst : G) {
					if (sinks.endpoint(dst)) {
						BFSPathFinder<T> paths = 
								new BFSPathFinder<T>(G, new NonNullSingletonIterator<T>(src), new Predicate<T>() {
									@Override
									public boolean test(T t) {
										return t.equals(dst);
									}
								});
						List<T> path;
						if ((path = paths.find()) != null) {
							System.err.println(path);
							result.add(path);
						}
					}
				}
			}
		}
		return result;
	}
}
