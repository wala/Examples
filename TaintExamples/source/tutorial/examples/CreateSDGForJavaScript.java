/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package tutorial.examples;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.java.client.impl.ZeroCFABuilderFactory;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.java.ipa.modref.AstJavaModRef;
import com.ibm.wala.cast.java.translator.jdt.ecj.ECJClassLoaderFactory;
import com.ibm.wala.cast.js.ipa.callgraph.JSCFABuilder;
import com.ibm.wala.cast.js.ipa.modref.JavaScriptModRef;
import com.ibm.wala.cast.js.loader.JavaScriptLoaderFactory;
import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.cast.js.util.JSCallGraphBuilderUtil;
import com.ibm.wala.cast.js.util.JSCallGraphBuilderUtil.CGBuilderType;
import com.ibm.wala.cast.python.client.PythonAnalysisEngine;
import com.ibm.wala.cast.python.ipa.callgraph.PythonSSAPropagationCallGraphBuilder;
import com.ibm.wala.cast.python.loader.PythonLoaderFactory;
import com.ibm.wala.cast.python.modref.PythonModRef;
import com.ibm.wala.cast.python.types.PythonTypes;
import com.ibm.wala.cast.python.util.PythonInterpreter;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.ModuleEntry;
import com.ibm.wala.classLoader.SourceFileModule;
import com.ibm.wala.dalvik.util.AndroidEntryPointLocator;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.propagation.AllocationSiteInNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.modref.ModRef;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.warnings.Warnings;

import magpiebridge.core.AnalysisConsumer;
import magpiebridge.core.AnalysisResult;
import magpiebridge.core.Kind;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.ToolAnalysis;
import magpiebridge.projectservice.java.JavaProjectService;
import tutorial.examples.Analysis.EndpointFinder;

public class CreateSDGForJavaScript {

	static {
		try {
			Class<?> j3 = Class.forName("com.ibm.wala.cast.python.loader.Python3LoaderFactory");
			PythonAnalysisEngine.setLoaderFactory((Class<? extends PythonLoaderFactory>) j3);
			Class<?> i3 = Class.forName("com.ibm.wala.cast.python.util.Python3Interpreter");
			PythonInterpreter.setInterpreter((PythonInterpreter)i3.newInstance());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
			try {
				Class<?> j2 = Class.forName("com.ibm.wala.cast.python.loader.Python2LoaderFactory");			
				PythonAnalysisEngine.setLoaderFactory((Class<? extends PythonLoaderFactory>) j2);
				Class<?> i2 = Class.forName("com.ibm.wala.cast.python.util.Python2Interpreter");
				PythonInterpreter.setInterpreter((PythonInterpreter)i2.newInstance());
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e1) {
				assert false : e.getMessage() + ", then " + e1.getMessage();
			}
		}
	}

	private static abstract class WalaTaintAnalysis implements ToolAnalysis {

		protected abstract EndpointFinder<Statement> sourceFinder();
		
		protected abstract EndpointFinder<Statement> sinkFinder();

		protected abstract ModRef<InstanceKey> modRef();
		
		@Override
		public void analyze(Collection<? extends Module> files, AnalysisConsumer server) {
			try {
				SSAPropagationCallGraphBuilder builder = makeBuilder(files, server);			
				CallGraph CG = builder.makeCallGraph(builder.getOptions());
				System.err.println(CG);
				SDG<InstanceKey> SDG = new SDG<InstanceKey>(CG, builder.getPointerAnalysis(), modRef(), DataDependenceOptions.NO_BASE_NO_HEAP_NO_EXCEPTIONS, ControlDependenceOptions.NONE);
				System.err.println(SDG);
				Set<List<Statement>> paths = Analysis.getPaths(SDG, sourceFinder(), sinkFinder());
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
							public Pair<Position, String> repair() {
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

				server.consume(results, source());
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

		protected abstract SSAPropagationCallGraphBuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server) throws WalaException;

	}

	private static class PythonWalaTaintAnalysis extends WalaTaintAnalysis {
		private PythonSSAPropagationCallGraphBuilder builder;
		
		private final MethodReference flask = 
				MethodReference.findOrCreate(
						TypeReference.findOrCreate(PythonTypes.pythonLoader, "Lflask"),
						new Selector(
								Atom.findOrCreateUnicodeAtom("import"),
								Descriptor.findOrCreateUTF8("()Lflask;")));
						
		@Override
		public String source() {
			return "WALA Python taint";
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
								if (xx.getNode().getMethod().getReference().equals(flask) &&
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
		protected SSAPropagationCallGraphBuilder makeBuilder(Collection<? extends Module> files,
				AnalysisConsumer server) throws WalaException {
			PythonAnalysisEngine<?> E = new PythonAnalysisEngine<Void>() {
				@Override
				public Void performAnalysis(PropagationCallGraphBuilder builder) throws CancelException {
					assert false;
					return null;
				}
			};

			E.setModuleFiles(files);
			
			try {
				return builder = E.defaultCallGraphBuilder();
			} catch (IllegalArgumentException | IOException e) {
				throw new WalaException("WALA error", e);
			}
		}
	}
	
	private static class JavaSourceWalaTaintAnalysis extends WalaTaintAnalysis {
		@Override
		public String source() {
			return "JavaSourceTaintDemo";
		}

		protected SSAPropagationCallGraphBuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server) throws WalaException {
		    AnalysisScope scope = new JavaSourceAnalysisScope();

			files.forEach((m) -> scope.addToScope(JavaSourceAnalysisScope.SOURCE, m));
			
			if (server instanceof MagpieServer) {
				Set<Path> libs = ((JavaProjectService)((MagpieServer)server).getProjectService("java").get()).getLibraryPath();
				libs.forEach(p -> {
					try {
						scope.addToScope(ClassLoaderReference.Primordial, new JarFile(p.toAbsolutePath().toString()));
					} catch (IOException e) {
						assert false : e;
					}
				});
			}
	
		    String[] stdlibs = WalaProperties.getJ2SEJarFiles();
		    for (int i = 0; i < stdlibs.length; i++) {
		      try {
				scope.addToScope(ClassLoaderReference.Primordial, new JarFile(stdlibs[i]));
		      } catch (IOException e) {
		    	assert false : e;
		      }
		    }

		    IClassHierarchy cha = ClassHierarchyFactory.make(scope, new ECJClassLoaderFactory(scope.getExclusions()));
		    Warnings.clear();
		    AnalysisOptions options = new AnalysisOptions();
		    Iterable<? extends Entrypoint> entrypoints = new AndroidEntryPointLocator().getEntryPoints(cha);
		    options.setEntrypoints(entrypoints);
		    options.setReflectionOptions(ReflectionOptions.NONE);
		    IAnalysisCacheView cache = new AnalysisCacheImpl(AstIRFactory.makeDefaultFactory());
		    SSAPropagationCallGraphBuilder builder = new ZeroCFABuilderFactory().make(options, cache, cha, scope);
		 
		    return builder;
		}

		@Override
		protected EndpointFinder<Statement> sourceFinder() {
			return Analysis.getDeviceSource;
		}

		@Override
		protected EndpointFinder<Statement> sinkFinder() {
			return Analysis.sendMessageSink;
		}

		@Override
		protected ModRef<InstanceKey> modRef() {
			return new AstJavaModRef<>();
		}

	}

	
	private static class JSHtmlWalaTaintAnalysis extends JSWalaTaintAnalysis {
		@Override
		public String source() {
			return "JSHtmlTaintDemo";
		}

		protected JSCFABuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server) throws WalaException {
			assert files.size() == 1;
			ModuleEntry module = files.iterator().next().getEntries().next();
			URL url;
			try {
				url = new URL("file:" + module.getName());
				JSCFABuilder builder = JSCallGraphBuilderUtil.makeHTMLCGBuilder(url, CGBuilderType.ZERO_ONE_CFA_WITHOUT_CORRELATION_TRACKING, new InputStreamReader(module.getInputStream()));
				return builder;
			} catch (MalformedURLException e) {
				assert false : e;
				return null;
			}
		}
	}
	
	private static class JSWalaTaintAnalysis extends WalaTaintAnalysis {
		@Override
		public String source() {
			return "JSTaintDemo";
		}

		protected JSCFABuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server) throws WalaException {
			Collection<Module> m2 = HashSetFactory.make();
			files.forEach((m) -> m2.add(m));
			m2.add(JSCallGraphBuilderUtil.getPrologueFile("prologue.js"));
			Module[] modules = m2.toArray(new Module[files.size()]);
			JavaScriptLoaderFactory loaders = new JavaScriptLoaderFactory(new CAstRhinoTranslatorFactory());
			JSCFABuilder builder = JSCallGraphBuilderUtil.makeCGBuilder(loaders, modules, CGBuilderType.ONE_CFA, AstIRFactory.makeDefaultFactory());
			return builder;
		}

		@Override
		protected EndpointFinder<Statement> sourceFinder() {
			return Analysis.documentUrlSource;
		}

		@Override
		protected EndpointFinder<Statement> sinkFinder() {
			return Analysis.documentWriteSink;
		}

		@Override
		protected ModRef<InstanceKey> modRef() {
			return new JavaScriptModRef<>();
		}

	}
	
	private static SDG<InstanceKey> jssdg(String file, DataDependenceOptions data, ControlDependenceOptions ctrl) throws IOException, WalaException, CancelException {
		JSCFABuilder B = JSCallGraphBuilderUtil.makeScriptCGBuilder("tests", file);
		CallGraph CG = B.makeCallGraph(B.getOptions());
		return new SDG<InstanceKey>(CG, B.getPointerAnalysis(), new JavaScriptModRef<InstanceKey>(), data, ctrl);
	}

	private static SDG<InstanceKey> htmlsdg(String file, DataDependenceOptions data, ControlDependenceOptions ctrl) throws IOException, WalaException, CancelException {
		JSCFABuilder B = JSCallGraphBuilderUtil.makeHTMLCGBuilder(new URL(file));
		CallGraph CG = B.makeCallGraph(B.getOptions());
		PointerAnalysis<InstanceKey> ptr = B.getPointerAnalysis();
		return new SDG<InstanceKey>(CG, ptr, new JavaScriptModRef<InstanceKey>(), data, ctrl);
	}

	private static SDG<InstanceKey> sdg(String file, DataDependenceOptions data, ControlDependenceOptions ctrl) throws IOException, WalaException, CancelException {
		if (file.endsWith("html")) {
			return htmlsdg(file, data, ctrl);
		} else {
			return jssdg(file, data, ctrl);
		}
	}

	public static MagpieServer bridge() {
		MagpieServer bridge = new MagpieServer();
		bridge.addProjectService("java", new JavaProjectService());
		bridge.addAnalysis(Either.forRight(new JSWalaTaintAnalysis()), "js", "javascript");
		bridge.addAnalysis(Either.forRight(new JSHtmlWalaTaintAnalysis()), "html");
		bridge.addAnalysis(Either.forRight(new JavaSourceWalaTaintAnalysis()), "java");
		bridge.addAnalysis(Either.forRight(new PythonWalaTaintAnalysis()), "py", "python");
		return bridge;
	}

	public static void main(String[] args) throws IOException, WalaException, CancelException {
		com.ibm.wala.cast.js.ipa.callgraph.JSCallGraphUtil.setTranslatorFactory(new CAstRhinoTranslatorFactory());

		if (args.length < 1) {
			MagpieServer bridge = bridge();
			bridge.launchOnStdio();

		} else {
			Collection<Module> src = Collections.singleton(new SourceFileModule(new File(args[0]), args[0].substring(args[0].lastIndexOf('/')+1), null));
			AnalysisConsumer ac = (rs, s) -> { 
				System.err.println("results for " + s);
				rs.forEach(r -> {
					try {
						System.err.println(new SourceBuffer(r.position()));
					} catch (IOException e) {

					}
					System.err.println(r.toString(false) + ": " + r.position());
				});
			};
			switch (args[0].substring(args[0].lastIndexOf('.'))) {
			case ".py": {
				new PythonWalaTaintAnalysis().analyze(src, ac);
				break;
			}
			case ".java": {
				new JavaSourceWalaTaintAnalysis().analyze(src, ac);
				break;
			}
			case ".js": {
				new JSWalaTaintAnalysis().analyze(src, ac);
				break;
			}
			case ".html": {
				new JSHtmlWalaTaintAnalysis().analyze(src, ac);
				break;
			}
			}
		}
	}
}
