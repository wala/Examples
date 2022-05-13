package com.ibm.wala.examples.SOAP2020.织女.ifds;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.ibm.wala.cast.ipa.callgraph.AstSSAPropagationCallGraphBuilder;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.Module;
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
import com.ibm.wala.examples.SOAP2020.织女.Print;
import com.ibm.wala.examples.SOAP2020.织女.WalaTaintAnalysis;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
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
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;
import com.ibm.wala.util.intset.MutableMapping;

import magpiebridge.core.AnalysisConsumer;
import magpiebridge.core.AnalysisResult;
import magpiebridge.core.Kind;

public abstract class WalaIFDSTaintAnalysis extends WalaTaintAnalysis<Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean>> {
	protected CallGraph CG;
	protected AstSSAPropagationCallGraphBuilder builder;
	
	public WalaIFDSTaintAnalysis(MutableMapping<String> uriCache) {
		super(uriCache);
	}

	/** set of tainted value numbers for each node */
	private class 织女Domain extends MutableMapping<Pair<Integer,List<Position>>>
			implements TabulationDomain<Pair<Integer,List<Position>>, BasicBlockInContext<IExplodedBasicBlock>> {

		private static final long serialVersionUID = -1897766113586243833L;
		
		@Override
		public boolean hasPriorityOver(PathEdge<BasicBlockInContext<IExplodedBasicBlock>> p1,
				PathEdge<BasicBlockInContext<IExplodedBasicBlock>> p2) {
			// don't worry about worklist priorities
			return false;
		}
	}

	class 织女FlowFunctions implements IPartiallyBalancedFlowFunctions<BasicBlockInContext<IExplodedBasicBlock>> {
		private final 织女Domain domain;
		private final ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> supergraph;

		public 织女FlowFunctions(ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> supergraph, 织女Domain domain) {
			this.supergraph = supergraph;
			this.domain = domain;
		}

		@Override
		public IUnaryFlowFunction getNormalFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			final IExplodedBasicBlock ebb = src.getDelegate();
			final IExplodedBasicBlock dbb = dest.getDelegate();
	
			return new IUnaryFlowFunction() {

				private void propagate(SSAInstruction inst, Pair<Integer, List<Position>> vn, MutableIntSet r) {
					boolean propagates = inst instanceof SSAPhiInstruction || inst instanceof SSAPiInstruction
							|| inst instanceof SSABinaryOpInstruction || inst instanceof SSAUnaryOpInstruction;

					if (propagates) {
						for (int i = 0; i < inst.getNumberOfUses(); i++) {
							if (vn.fst == inst.getUse(i)) {
								List<Position> x = new LinkedList<>(vn.snd);
								if (src.getMethod() instanceof AstMethod && inst.iIndex() >= 0) {
									Position next = ((AstMethod)src.getMethod()).debugInfo().getInstructionPosition(inst.iIndex());
									if (next != null) {
										x.add(next);
									}
								}
								Pair<Integer, List<Position>> nvn = Pair.make(inst.getDef(), x);							
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
					Pair<Integer, List<Position>> vn = domain.getMappedObject(d1); 
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
				Pair<Integer, List<Position>> vn = domain.getMappedObject(d1);
				for (int i = 0; i < inst.getNumberOfUses(); i++) {
					if (vn.fst == inst.getUse(i)) {
						List<Position> x = new LinkedList<>(vn.snd);
						if (src.getMethod() instanceof AstMethod) {
							Position next = ((AstMethod)src.getMethod()).debugInfo().getInstructionPosition(inst.iIndex());
							if (next != null) {
								x.add(next);
							}
						}
						Pair<Integer, List<Position>> key = Pair.make(i+1, x);							
						if (! domain.hasMappedIndex(key)) {
							domain.add(key);
						}
						r.add(domain.getMappedIndex(key));
					}
				}
				return r;
			};
		}

		// pass tainted values back to caller, if appropriate
		@Override
		public IUnaryFlowFunction getReturnFlowFunction(BasicBlockInContext<IExplodedBasicBlock> call,
				BasicBlockInContext<IExplodedBasicBlock> src, BasicBlockInContext<IExplodedBasicBlock> dest) {
			int retVal = call.getLastInstruction().getDef();
			return (d1) -> {
				Pair<Integer, List<Position>> vn = domain.getMappedObject(d1);
				MutableIntSet result = IntSetUtil.make();
				supergraph.getPredNodes(src).forEachRemaining(pb -> { 
					SSAInstruction inst = pb.getDelegate().getInstruction();
					if (inst instanceof SSAReturnInstruction && inst.getUse(0) == vn.fst) {
						List<Position> x = new LinkedList<>(vn.snd);
						if (src.getMethod() instanceof AstMethod) {
							Position next = ((AstMethod)src.getMethod()).debugInfo().getInstructionPosition(inst.iIndex());
							if (next != null) {
								x.add(next);
							}
						}
						Pair<Integer, List<Position>> key = Pair.make(retVal, x);
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

		// data flow back from an unknown call site, so flow to anything possible.
		@Override
		public IUnaryFlowFunction getUnbalancedReturnFlowFunction(BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			CGNode retFromNode = src.getNode();
			CGNode returnToNode = dest.getNode();
			return d1 -> {
				Pair<Integer, List<Position>> vn = domain.getMappedObject(d1);
				MutableIntSet result = IntSetUtil.make();
				CG.getPossibleSites(returnToNode, retFromNode).forEachRemaining(site -> { 
					for(SSAAbstractInvokeInstruction call : returnToNode.getIR().getCalls(site)) {
						supergraph.getPredNodes(src).forEachRemaining(pb -> { 
							SSAInstruction ret = pb.getDelegate().getInstruction();
							if (ret instanceof SSAReturnInstruction && ret.getUse(0) == vn.fst) {
								List<Position> x = new LinkedList<>(vn.snd);
								if (src.getMethod() instanceof AstMethod) {
									Position next = ((AstMethod)src.getMethod()).debugInfo().getInstructionPosition(ret.iIndex());
									if (next != null) {
										x.add(next);
									}
								}
								Pair<Integer, List<Position>> key = Pair.make(call.getDef(), x);
								if (! domain.hasMappedIndex(key)) {
									domain.add(key);
								}
								result.add(domain.getMappedIndex(key));
							}
						});

					}
				});
				return result;
			};
		}
	}

	@Override
	public void analyze(Collection<? extends Module> files, AnalysisConsumer server, boolean rerun) {
		try {
			builder = makeBuilder(files, server);
			CG = builder.makeCallGraph(builder.getOptions());
			
			ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> supergraph = ICFGSupergraph.make(CG);

			织女Domain domain = new 织女Domain();

			class 织女Problem implements PartiallyBalancedTabulationProblem<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<Integer, List<Position>>> {

				private final 织女FlowFunctions flowFunctions = new 织女FlowFunctions(supergraph, domain);

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
						if (sourceFinder().apply(bb)) {
							Position next = ((AstMethod)bb.getMethod()).debugInfo().getInstructionPosition(instruction.iIndex());
							Pair<Integer, List<Position>> fact = Pair.make(instruction.getDef(), Collections.singletonList(next));
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

			PartiallyBalancedTabulationSolver<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<Integer, List<Position>>> solver = PartiallyBalancedTabulationSolver
					.createPartiallyBalancedTabulationSolver(new 织女Problem(), null);
			TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<Integer, List<Position>>> R = solver.solve();

			Set<List<Position>> result = HashSetFactory.make();

			System.err.println(R);
			
			R.getSupergraphNodesReached().forEach((sbb) -> { 
				if (sinkFinder().apply(sbb)) { 
					SSAInstruction inst = sbb.getDelegate().getInstruction();
					Position sink = ((AstMethod)sbb.getMethod()).debugInfo().getInstructionPosition(inst.iIndex());
					System.err.println("sink " + inst);
					R.getResult(sbb).foreach(i -> {
						Pair<Integer, List<Position>> trace = domain.getMappedObject(i);
						for(int j = 0; j < inst.getNumberOfUses(); j++) { 
							if (inst.getUse(j) == trace.fst) {
								List<Position> x = new LinkedList<>(trace.snd);	
								x.add(sink);
								result.add(x);
							}
						}
					});
				}
			});

			Set<AnalysisResult> ars = HashSetFactory.make();
			result.forEach(lp -> { 
				lp.forEach(step -> { 
					ars.add(new AnalysisResult() {

						@Override
						public Kind kind() {
							return Kind.Diagnostic;
						}

						@Override
						public String toString(boolean useMarkdown) {
							return "tainted flow";
						}

						@Override
						public Position position() {
							return Print.fixIncludedURL(step);
						}

						@Override
						public Iterable<Pair<Position, String>> related() {
							Set<Pair<Position, String>>  trace = HashSetFactory.make();
							lp.forEach(e -> {  trace.add(Pair.make(e, "flow step")); });
							return trace;
						}

						@Override
						public DiagnosticSeverity severity() {
							return DiagnosticSeverity.Warning;
						}

						@Override
						public Either<Pair<Position, String>, List<TextEdit>> repair() {
							// TODO Auto-generated method stub
							return null;
						}

						@Override
						public String code() {
							try {
								return new SourceBuffer(position()).toString();
							} catch (IOException e) {
								return "unknown";
							}
						} 
					});
				});
			});
			server.consume(ars, this);
		} catch (WalaException | IllegalArgumentException | CancelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		
	}

}
