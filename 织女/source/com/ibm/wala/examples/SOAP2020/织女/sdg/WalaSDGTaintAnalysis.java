package com.ibm.wala.examples.SOAP2020.织女.sdg;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.ibm.wala.cast.ipa.callgraph.AstSSAPropagationCallGraphBuilder;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.examples.SOAP2020.织女.EndpointFinder;
import com.ibm.wala.examples.SOAP2020.织女.Print;
import com.ibm.wala.examples.SOAP2020.织女.WalaTaintAnalysis;
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
import com.ibm.wala.util.intset.MutableMapping;

import magpiebridge.core.AnalysisConsumer;
import magpiebridge.core.AnalysisResult;
import magpiebridge.core.Kind;
import magpiebridge.core.MagpieServer;
import magpiebridge.util.SourceCodePositionUtils;

public abstract class WalaSDGTaintAnalysis extends WalaTaintAnalysis<EndpointFinder<Statement>> {

	protected abstract ModRef<InstanceKey> modRef();
	
	protected WalaSDGTaintAnalysis(MutableMapping<String> uriCache) {
		super(uriCache);
	}

	@SuppressWarnings("incomplete-switch")
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
				Command witness = null;
				if (server instanceof MagpieServer) {
					try {
						int i = 0;
						String nodes = "";
						String edges = "";
						
						for(Statement node : path) {
							Position pos = Print.getPosition(node);
							String text = null;
							if (pos != null) {
								text = new SourceBuffer(pos).toString().replace("\"", "\\\"").trim();
							} else if (node.getNode().getMethod() instanceof AstMethod) {
								switch(node.getKind()) {
								case PARAM_CALLEE:
									AstMethod m = (AstMethod)node.getNode().getMethod();
									text = "call to " + new SourceBuffer(pos = m.debugInfo().getCodeNamePosition());
									break;
								case NORMAL_RET_CALLEE:
									m = (AstMethod)node.getNode().getMethod();
									text = "return from " + new SourceBuffer(pos = m.debugInfo().getCodeNamePosition());
									break;
								}
							}
							
							    if (text != null) {
								try {
								    // have seen bad uris cause trouble...
								    URL url = new URL(((MagpieServer)server).getClientUri(pos.getURL().toString()));
								    Location loc = SourceCodePositionUtils.getLocationFrom(pos);
								    
								    if (text.indexOf('\n') >= 0) {
									text = text.substring(0, text.indexOf('\n')) + "...";
								    }
								    
								    Range r = loc.getRange();
								    
								    int uriIdx;
								    if (! uriCache.hasMappedIndex(url.toString())) {
									uriIdx = uriCache.add(url.toString());
								    } else {
									uriIdx = uriCache.getMappedIndex(url.toString());
								    }
								    
								    nodes += ",{ id: " + i
									+ ", url: " + uriIdx
									+ ", fc: " + r.getStart().getCharacter()
									+ ", fl: " + r.getStart().getLine()
									+ ", lc: " + r.getEnd().getCharacter()
									+ ", ll: " + r.getEnd().getLine() 
									+ ", label: \"" + text + "\" }";
								    i++;
								} catch (Exception e) {
								    // ...so skip the node if the URI causes trouble
								}
							    }
						}
						
						for(int j = 0; j < i-1; j++) {
							edges += ",{ arrows: 'to', from: " + j + ", to: " + (j+1) + " }";
						}
						
						nodes = nodes.substring(1);
						edges = edges.substring(1);

						String showPath = "http://" + InetAddress.getLocalHost().getHostName() + ":" +((MagpieServer)server).getHttpServer().getAddress().getPort() + "/zhinu?nodes=" + URLEncoder.encode(nodes, StandardCharsets.UTF_8.toString()) + "&edges=" + URLEncoder.encode(edges, StandardCharsets.UTF_8.toString());

						witness = new Command();
		                witness.setTitle("Witness");
		                witness.setCommand("openURL");	
		                witness.setArguments(Arrays.asList(showPath, "Witness", Boolean.TRUE, Boolean.FALSE));

					} catch (IOException e) {

					}
				}

				Command xxx = witness;
				
				List<AnalysisResult> pr = new LinkedList<>();
				for(int i = 0; i < path.size(); i++) {
					int idx = i;
					Statement last = path.get(idx);
					if (Print.getPosition(last) == null) {
						continue;
					}
					class Result implements AnalysisResult {
						private final Kind kind;
						private Result(Kind kind) {
							this.kind = kind;
						}
						@Override
						public Kind kind() {
							return kind;
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

						@Override
						public Iterable<Command> command() {
							if (xxx != null) {
								return Collections.singleton(xxx);
							} else {
								return Collections.emptySet();
							}
						} 

					}
					
					pr.add(new Result(Kind.CodeLens));
					pr.add(new Result(Kind.Diagnostic));
				}
				results.addAll(pr);
			});

			server.consume(results, this);
		} catch (WalaException | IllegalArgumentException | CancelException e) {
		    System.err.println(e);
		}
	}

	public <T> Set<List<T>> getPaths(Graph<T> G, EndpointFinder<T> sources, EndpointFinder<T> sinks) {
		Set<List<T>> result = HashSetFactory.make();
		for(T src : G) {
			if (sources.endpoint(src)) {
				BFSPathFinder<T> paths = 
						new BFSPathFinder<T>(G, 
	                              new NonNullSingletonIterator<T>(src), 
	                              (Predicate<T>)dst -> sinks.endpoint(dst));
					List<T> path;
					while ((path = paths.find()) != null) {
						result.add(path);
					}
			}
		}
		return result;
	}

}
