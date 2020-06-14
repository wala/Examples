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
package com.ibm.wala.examples.SOAP2020.织女;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.cast.python.client.PythonAnalysisEngine;
import com.ibm.wala.cast.python.loader.PythonLoaderFactory;
import com.ibm.wala.cast.python.util.PythonInterpreter;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.SourceFileModule;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;

import magpiebridge.core.AnalysisConsumer;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.ServerConfiguration;
import magpiebridge.projectservice.java.JavaProjectService;

public class 织女 {

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

	public static MagpieServer bridge() {
		JavaProjectService services = new JavaProjectService();
		MagpieServer bridge = new MagpieServer(new ServerConfiguration()) {

			@Override
			public CompletableFuture<InitializeResult> initialize(InitializeParams arg0) {
				CompletableFuture<InitializeResult> ret = super.initialize(arg0);
				if (rootPath.isPresent()) {
					services.setRootPath(rootPath.get());
				}
				return ret;
			}
			
		};
		bridge.addProjectService("java", services);
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
				System.out.println("results for " + s);
				rs.forEach(r -> {
					try {
						System.out.println(new SourceBuffer(r.position()));
					} catch (IOException e) {

					}
					System.out.println(r.toString(false) + ": " + r.position());
				});
			};
			switch (args[0].substring(args[0].lastIndexOf('.'))) {
			case ".js": {
				new JSWalaTaintAnalysis().analyze(src, ac, false);
				break;
			}
			case ".py": {
				new PythonWalaTaintAnalysis().analyze(src, ac, false);
				break;
			}
			case ".java": {
				new JavaSourceWalaTaintAnalysis().analyze(src, ac, false);
				break;
			}
			case ".html": {
				new JSHtmlWalaTaintAnalysis().analyze(src, ac, false);
				break;
			}
			}
		}
	}
}
