package tutorial.examples;

import static tutorial.examples.Analysis.getDeviceSource;
import static tutorial.examples.Analysis.getPaths;
import static tutorial.examples.Analysis.sendMessageSink;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;

import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.java.client.impl.ZeroCFABuilderFactory;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.java.ipa.modref.AstJavaModRef;
import com.ibm.wala.cast.java.translator.jdt.ejc.ECJClassLoaderFactory;
import com.ibm.wala.classLoader.SourceDirectoryTreeModule;
import com.ibm.wala.dalvik.test.callGraph.DalvikCallGraphTestBase;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.io.CommandLine;
import com.ibm.wala.util.warnings.Warnings;

/**
 * Driver that constructs a call graph for an application specified as a directory of source code.
 * Example of using the JDT front-end based on ECJ.    
 * Useful for getting some code to copy-paste.    
 */
public class CreateSDGForJavaSource {

  /**
   * Usage: ScopeFileCallGraph -sourceDir file_path -mainClass class_name
   * 
   * If given -mainClass, uses main() method of class_name as entrypoint.  Class
   * name should start with an 'L'.
   * 
   * Example args: -sourceDir /tmp/srcTest -mainClass LFoo
   * 
   */
  public static void main(String[] args) throws ClassHierarchyException, IllegalArgumentException, CallGraphBuilderCancelException, IOException {
    Properties p = CommandLine.parse(args);
    String sourceDir = System.getProperty("sourceDir");
    AnalysisScope scope = new JavaSourceAnalysisScope();
    // add standard libraries to scope
    String[] stdlibs = WalaProperties.getJ2SEJarFiles();
    for (int i = 0; i < stdlibs.length; i++) {
      scope.addToScope(ClassLoaderReference.Primordial, new JarFile(stdlibs[i]));
    }
    scope.addToScope(ClassLoaderReference.Primordial, new JarFile("/Users/dolby/Android/android-sdk-macosx/extras/android/support/v7/appcompat/libs/android-support-v4.jar"));
    scope.addToScope(ClassLoaderReference.Primordial, new JarFile("/Users/dolby/Android/android-sdk-macosx/extras/android/support/v7/appcompat/libs/android-support-v7-appcompat.jar"));
    scope.addToScope(ClassLoaderReference.Primordial, new JarFile("/Users/dolby/Android/android-sdk-macosx/platforms/android-23/android.jar"));
    // add the source directory
    scope.addToScope(JavaSourceAnalysisScope.SOURCE, new SourceDirectoryTreeModule(new File(sourceDir)));
    
    // build the class hierarchy
    IClassHierarchy cha = ClassHierarchy.make(scope, new ECJClassLoaderFactory(scope.getExclusions()));
    System.out.println(cha.getNumberOfClasses() + " classes");
    System.out.println(Warnings.asString());
    Warnings.clear();
    AnalysisOptions options = new AnalysisOptions();
    Iterable<? extends Entrypoint> entrypoints = DalvikCallGraphTestBase.getEntrypoints(cha);
    options.setEntrypoints(entrypoints);
    // you can dial down reflection handling if you like
    options.setReflectionOptions(ReflectionOptions.NONE);
    AnalysisCache cache = new AnalysisCache(AstIRFactory.makeDefaultFactory());
    CallGraphBuilder builder = new ZeroCFABuilderFactory().make(options, cache, cha, scope, false);
    //CallGraphBuilder builder = new ZeroOneContainerCFABuilderFactory().make(options, cache, cha, scope, false);
    System.out.println("building call graph...");
    CallGraph cg = builder.makeCallGraph(options, null);
    
    PointerAnalysis<InstanceKey> ptr = builder.getPointerAnalysis();
	DataDependenceOptions data = DataDependenceOptions.NO_BASE_NO_HEAP_NO_EXCEPTIONS;
	ControlDependenceOptions control = ControlDependenceOptions.NONE;
	SDG<InstanceKey> sdg = new SDG<InstanceKey>(cg, ptr, new AstJavaModRef<InstanceKey>(), data, control);
	Set<List<Statement>> paths = getPaths(sdg, getDeviceSource, sendMessageSink);
	System.err.println(paths);
	Print.printPaths(paths);
}

}
