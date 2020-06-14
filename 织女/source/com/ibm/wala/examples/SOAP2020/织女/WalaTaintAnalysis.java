package com.ibm.wala.examples.SOAP2020.织女;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.ibm.wala.cast.ipa.callgraph.AstSSAPropagationCallGraphBuilder;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.modref.ModRef;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.NonNullSingletonIterator;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.traverse.BFSPathFinder;

import magpiebridge.core.AnalysisConsumer;
import magpiebridge.core.AnalysisResult;
import magpiebridge.core.Kind;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.ToolAnalysis;

public abstract class WalaTaintAnalysis implements ToolAnalysis {

	protected abstract EndpointFinder<Statement> sourceFinder();
	
	protected abstract EndpointFinder<Statement> sinkFinder();

	protected abstract ModRef<InstanceKey> modRef();
	
	@Override
	public void analyze(Collection<? extends Module> files, AnalysisConsumer server, boolean again) {
		try {
			AstSSAPropagationCallGraphBuilder builder = makeBuilder(files, server);			
			CallGraph CG = builder.makeCallGraph(builder.getOptions());
			System.err.println(CG);
			Graph<Statement> SDG = new SDG<>(CG, builder.getPointerAnalysis(), modRef(), DataDependenceOptions.NO_BASE_NO_HEAP_NO_EXCEPTIONS, ControlDependenceOptions.NONE);
			System.err.println(SDG);
			Set<List<Statement>> paths = getPaths(SDG, sourceFinder(), sinkFinder());
			Set<AnalysisResult> results = HashSetFactory.make();
			paths.forEach((path) ->  {
				List<AnalysisResult> pr = new LinkedList<>();
				for(int i = 0; i < path.size(); i++) {
					int idx = i;
					Statement last = path.get(idx);
					if (Print.getPosition(last) == null) {
						continue;
					}
					pr.add(new AnalysisResult() {
						@Override
						public Kind kind() {
							return Kind.Diagnostic;
						}

						@Override
						public String toString(boolean useMarkdown) {
							if (isSink(pr)) {
								return "tainted sink";
							} else if (isSource(pr)) {
								return "tainted source";
							} else {
								return "tainted flow step";
							}
						}

						private boolean isSource(List<AnalysisResult> pr) {
							return pr.indexOf(this) == pr.size()-1;
						}

						private boolean isSink(List<AnalysisResult> pr) {
							return pr.indexOf(this) == 0;
						}


						@Override
						public Position position() {
							return Print.getPosition(last);
						}

						@Override
						public Iterable<Pair<Position, String>> related() {
							List<Pair<Position, String>> info = new LinkedList<>();
							path.forEach((p) -> {
								try {
									Position pos = Print.getPosition(p);
									if (pos != null) {
									String text = new SourceBuffer(pos).toString();
									if (text != null) {
										info.add(Pair.make(pos, text.trim()));
									}
									}
								} catch (IOException e) {
									// skip this one
								}
							});
							Collections.reverse(info);
							return info;
						}

						@Override
						public DiagnosticSeverity severity() {
							return 
								isSink(pr)?
								   DiagnosticSeverity.Error:
								   isSource(pr)?
									  DiagnosticSeverity.Warning:
									  DiagnosticSeverity.Information;
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
				}
				results.addAll(pr);
			});

			server.consume(results, this);
		} catch (WalaException | IllegalArgumentException | CancelException e) {
			if (server instanceof MagpieServer) {
				MessageParams mp = new MessageParams();
				mp.setType(MessageType.Error);
				mp.setMessage(e.toString());
				((MagpieServer)server).getClient().showMessage(mp);
			} else {
				System.err.println(e);
			}
		}
	}

	protected abstract AstSSAPropagationCallGraphBuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server) throws WalaException;

	public <T> Set<List<T>> getPaths(Graph<T> G, EndpointFinder<T> sources, EndpointFinder<T> sinks) {
		Set<List<T>> result = HashSetFactory.make();
		for(T src : G) {
			if (sources.endpoint(src)) {
				BFSPathFinder<T> paths = 
					new BFSPathFinder<T>(G, new NonNullSingletonIterator<T>(src), (Predicate<T>)dst -> sinks.endpoint(dst));
				List<T> path;
				while ((path = paths.find()) != null) {
					result.add(path);
				}
			}
		}
		return result;
	}

}