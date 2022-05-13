package com.ibm.wala.examples.SOAP2020.织女;

import java.io.IOException;
import java.util.Collection;
import java.util.jar.JarFile;

import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.java.client.impl.ZeroCFABuilderFactory;
import com.ibm.wala.cast.java.ipa.callgraph.AstJavaSSAPropagationCallGraphBuilder;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.java.translator.jdt.ecj.ECJClassLoaderFactory;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.dalvik.util.AndroidEntryPointLocator;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.core.util.warnings.Warnings;

import magpiebridge.core.AnalysisConsumer;

public class JavaWalaBuilder {
	
	public static AstJavaSSAPropagationCallGraphBuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server) throws WalaException {
	    AnalysisScope scope = new JavaSourceAnalysisScope();

		files.forEach((m) -> scope.addToScope(JavaSourceAnalysisScope.SOURCE, m));

		try {
			if (System.getProperty("androidJar") != null) {
				scope.addToScope(ClassLoaderReference.Primordial, new JarFile(System.getProperty("androidJar")));
			}
		} catch (IOException e) {
			assert false : e;
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
	    AstJavaSSAPropagationCallGraphBuilder builder = new ZeroCFABuilderFactory().make(options, cache, cha);
	 
	    return builder;
	}

}
