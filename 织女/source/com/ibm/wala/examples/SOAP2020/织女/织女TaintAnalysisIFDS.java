package com.ibm.wala.examples.SOAP2020.织女;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.ibm.wala.cast.js.ipa.callgraph.JSCFABuilder;
import com.ibm.wala.cast.js.ipa.callgraph.JSCallGraphUtil;
import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.cast.js.util.JSCallGraphBuilderUtil;
import com.ibm.wala.cast.js.util.JSCallGraphBuilderUtil.CGBuilderType;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.dataflow.IFDS.ICFGSupergraph;
import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IMergeFunction;
import com.ibm.wala.dataflow.IFDS.IPartiallyBalancedFlowFunctions;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.dataflow.IFDS.IdentityFlowFunction;
import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationProblem;
import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationSolver;
import com.ibm.wala.dataflow.IFDS.PathEdge;
import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;
import com.ibm.wala.util.intset.MutableMapping;

public class 织女TaintAnalysisIFDS {

	/** the supergraph over which tabulation is performed */
	private final ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> supergraph;

	private final 织女Domain domain;

	private final Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sources;

	public 织女TaintAnalysisIFDS(CallGraph cg, Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sources) {
		supergraph = ICFGSupergraph.make(cg);
		this.sources = sources;
		this.domain = new 织女Domain();
	}

	/** set of tainted value numbers for each node */
	private static class 织女Domain extends MutableMapping<Pair<Integer,BasicBlockInContext<IExplodedBasicBlock>>>
			implements TabulationDomain<Pair<Integer,BasicBlockInContext<IExplodedBasicBlock>>, BasicBlockInContext<IExplodedBasicBlock>> {

		private static final long serialVersionUID = -1897766113586243833L;
		
		@Override
		public boolean hasPriorityOver(PathEdge<BasicBlockInContext<IExplodedBasicBlock>> p1,
				PathEdge<BasicBlockInContext<IExplodedBasicBlock>> p2) {
			// don't worry about worklist priorities
			return false;
		}
	}

	private class 织女FlowFunctions implements IPartiallyBalancedFlowFunctions<BasicBlockInContext<IExplodedBasicBlock>> {

		@Override
		public IUnaryFlowFunction getNormalFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			final IExplodedBasicBlock ebb = src.getDelegate();
			final IExplodedBasicBlock dbb = dest.getDelegate();
	
			return new IUnaryFlowFunction() {

				private void propagate(SSAInstruction inst, Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>> vn, MutableIntSet r) {
					boolean propagates = inst instanceof SSAPhiInstruction || inst instanceof SSAPiInstruction
							|| inst instanceof SSABinaryOpInstruction || inst instanceof SSAUnaryOpInstruction;

					if (propagates) {
						for (int i = 0; i < inst.getNumberOfUses(); i++) {
							if (vn.fst == inst.getUse(i)) {
								Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>> nvn = Pair.make(inst.getDef(), src);
								if (! domain.hasMappedIndex(nvn)) {
									domain.add(nvn);
								}
								r.add(domain.getMappedIndex(nvn));
							}
						}
					}
				}
								
				@Override
				public IntSet getTargets(int d1) {
					Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>> vn = domain.getMappedObject(d1); 
					MutableIntSet r = IntSetUtil.make(new int[] { domain.getMappedIndex(vn) });
					dbb.iteratePhis().forEachRemaining((inst) -> {
						propagate(inst, vn, r);
					});
					propagate(ebb.getInstruction(), vn, r);
					
					return r;
				}
			};
		}
		
		// taint parameters from arguments
		@Override
		public IUnaryFlowFunction getCallFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest, BasicBlockInContext<IExplodedBasicBlock> ret) {
			final IExplodedBasicBlock ebb = src.getDelegate();
			SSAInstruction inst = ebb.getInstruction();
			return (d1) -> {
				MutableIntSet r = IntSetUtil.make();
				Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>> vn = domain.getMappedObject(d1);
				for (int i = 0; i < inst.getNumberOfUses(); i++) {
					if (vn.fst == inst.getUse(i)) {
						Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>> key = Pair.make(i+1, src);
						if (! domain.hasMappedIndex(key)) {
							domain.add(key);
						}
						r.add(domain.getMappedIndex(key));
					}
				}
				return r;
			};
		}

		// pass tainted values back to caller
		@Override
		public IUnaryFlowFunction getReturnFlowFunction(BasicBlockInContext<IExplodedBasicBlock> call,
				BasicBlockInContext<IExplodedBasicBlock> src, BasicBlockInContext<IExplodedBasicBlock> dest) {
			int retVal = call.getLastInstruction().getDef();
			return (d1) -> {
				Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>> vn = domain.getMappedObject(d1);
				MutableIntSet result = IntSetUtil.make();
				supergraph.getPredNodes(src).forEachRemaining(pb -> { 
					SSAInstruction inst = pb.getDelegate().getInstruction();
					if (inst instanceof SSAReturnInstruction && inst.getUse(0) == vn.fst) {
						Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>> key = Pair.make(retVal, pb);
						if (! domain.hasMappedIndex(key)) {
							domain.add(key);
						}
						result.add(domain.getMappedIndex(key));
					}
				});
				return result;
			};
		}

		// local variables are not changed by calls.
		@Override
		public IUnaryFlowFunction getCallToReturnFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			return IdentityFlowFunction.identity();
		}

		// missing a callee, so assume it does nothing and keep local information.
		@Override
		public IUnaryFlowFunction getCallNoneToReturnFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			return IdentityFlowFunction.identity();
		}

		// data flow back from an unknown call site, so just keep what comes back.
		@Override
		public IFlowFunction getUnbalancedReturnFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			return IdentityFlowFunction.identity();
		}
	}

	private class 织女Problem
			implements PartiallyBalancedTabulationProblem<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>>> {

		private final 织女FlowFunctions flowFunctions = new 织女FlowFunctions();

		/** path edges corresponding to taint sources */
		private final Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> initialSeeds = collectInitialSeeds();

		/**
		 * we use the entry block of the CGNode as the fake entry when propagating from
		 * callee to caller with unbalanced parens
		 */
		@Override
		public BasicBlockInContext<IExplodedBasicBlock> getFakeEntry(BasicBlockInContext<IExplodedBasicBlock> node) {
			final CGNode cgNode = node.getNode();
			return getFakeEntry(cgNode);
		}

		/**
		 * we use the entry block of the CGNode as the "fake" entry when propagating
		 * from callee to caller with unbalanced parens
		 */
		private BasicBlockInContext<IExplodedBasicBlock> getFakeEntry(final CGNode cgNode) {
			BasicBlockInContext<IExplodedBasicBlock>[] entriesForProcedure = supergraph.getEntriesForProcedure(cgNode);
			assert entriesForProcedure.length == 1;
			return entriesForProcedure[0];
		}

		/**
		 * collect sources of taint
		 */
		private Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> collectInitialSeeds() {
			Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> result = HashSetFactory.make();
			for (BasicBlockInContext<IExplodedBasicBlock> bb : supergraph) {
				IExplodedBasicBlock ebb = bb.getDelegate();
				SSAInstruction instruction = ebb.getInstruction();
				if (sources.apply(bb)) {
					Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>> fact = Pair.make(instruction.getDef(), bb);
					final CGNode cgNode = bb.getNode();
					int factNum = domain.add(fact);
					BasicBlockInContext<IExplodedBasicBlock> fakeEntry = getFakeEntry(cgNode);
					// note that the fact number used for the source of this path edge doesn't
					// really matter
					result.add(PathEdge.createPathEdge(fakeEntry, factNum, bb, factNum));
				}
			}
			return result;
		}

		@Override
		public IPartiallyBalancedFlowFunctions<BasicBlockInContext<IExplodedBasicBlock>> getFunctionMap() {
			return flowFunctions;
		}

		@Override
		public 织女Domain getDomain() {
			return domain;
		}

		/**
		 * we don't need a merge function; the default unioning of tabulation works fine
		 */
		@Override
		public IMergeFunction getMergeFunction() {
			return null;
		}

		@Override
		public ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> getSupergraph() {
			return supergraph;
		}

		@Override
		public Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> initialSeeds() {
			return initialSeeds;
		}
	}

	/** perform the tabulation analysis and return the {@link TabulationResult} */

	public TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<Integer,BasicBlockInContext<IExplodedBasicBlock>>> analyze() {
		PartiallyBalancedTabulationSolver<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>>> solver = PartiallyBalancedTabulationSolver
				.createPartiallyBalancedTabulationSolver(new 织女Problem(), null);
		TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>>> result = null;
		try {
			result = solver.solve();
		} catch (CancelException e) {
			// this shouldn't happen
			assert false;
		}
		return result;
	}

	public ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> getSupergraph() {
		return supergraph;
	}

	public 织女Domain getDomain() {
		return domain;
	}

	public static void main(String... args) throws IllegalArgumentException, CancelException, WalaException, IOException {
	    JSCallGraphUtil.setTranslatorFactory(new CAstRhinoTranslatorFactory());
		URL url = new URL(args[0]);
		JSCFABuilder B = JSCallGraphBuilderUtil.makeHTMLCGBuilder(url, CGBuilderType.ZERO_ONE_CFA_WITHOUT_CORRELATION_TRACKING, new InputStreamReader(url.openStream()));
		CallGraph CG = B.makeCallGraph(B.getOptions());
		
		Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sources = (ebb) -> { 
			SSAInstruction inst = ebb.getDelegate().getInstruction();
			 if (inst instanceof SSAGetInstruction) {
				 if (((SSAGetInstruction)inst).getDeclaredField().getName().toString().equals("URL")) {
					 return true;
				 }
			 }

			 return false;
		 };
		 
		 Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sinks = (bb) -> { 
			 SSAInstruction inst = bb.getDelegate().getInstruction();
			 if (inst instanceof SSAAbstractInvokeInstruction) {
				 for (CGNode target : CG.getPossibleTargets(bb.getNode(), ((SSAAbstractInvokeInstruction) inst).getCallSite())) {
					 if (target.getMethod().getDeclaringClass().getName().toString().contains("prototype_write")) {
						 return true;
					 }
				 }
			 }
			 
			 return false;
		 };

		analyzeTaint(CG, sources, sinks).forEach(witness -> { 
			witness.forEach(step -> { 
				try {
					System.out.println(new SourceBuffer(step));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
		});
	}

	public static Set<List<Position>> analyzeTaint(CallGraph CG, Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sources,Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sinks) {		 
		 织女TaintAnalysisIFDS A = new 织女TaintAnalysisIFDS(CG, sources);
		 
		 TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>>> R = A.analyze();

		 Set<List<Position>> result = HashSetFactory.make();
		 
		 R.getSupergraphNodesReached().forEach((sbb) -> { 
			 if (sinks.apply(sbb)) { 
				System.out.println("sink " + sbb.getDelegate().getInstruction());
				BasicBlockInContext<IExplodedBasicBlock> bb= sbb;
				List<Position> witness = new LinkedList<>();
				steps: while (bb != null) {
					IntSet r = R.getResult(bb);
					SSAInstruction inst = bb.getDelegate().getInstruction();
					if (bb.getMethod() instanceof AstMethod) {
						Position pos = ((AstMethod)bb.getMethod()).debugInfo().getInstructionPosition(inst.iIndex());
						witness.add(0, pos);
					}
					IntIterator vals = r.intIterator();
					while(vals.hasNext()) {
						int i = vals.next();
						Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>> vn = A.domain.getMappedObject(i);
						for(int j = 0; j < inst.getNumberOfUses(); j++) { 
							if (inst.getUse(j) == vn.fst) {
								bb = vn.snd;
								System.out.println("step " + bb.getDelegate().getInstruction());
								continue steps;
							}
						}
					}
					bb = null;
				}
				if (witness.size() > 0) {
					result.add(witness);
				}
			 }
		 });
		 return result;
	}
}
