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
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.cast.python.client.PythonAnalysisEngine;
import com.ibm.wala.cast.python.loader.PythonLoaderFactory;
import com.ibm.wala.cast.python.util.PythonInterpreter;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.SourceFileModule;
import com.ibm.wala.examples.SOAP2020.织女.ifds.JSHtmlWalaIFDSTaintAnalysis;
import com.ibm.wala.examples.SOAP2020.织女.ifds.JSWalaIFDSTaintAnalysis;
import com.ibm.wala.examples.SOAP2020.织女.ifds.JavaSourceWalaIFDSTaintAnalysis;
import com.ibm.wala.examples.SOAP2020.织女.ifds.PythonWalaIFDSTaintAnalysis;
import com.ibm.wala.examples.SOAP2020.织女.sdg.JSHtmlWalaSDGTaintAnalysis;
import com.ibm.wala.examples.SOAP2020.织女.sdg.JSWalaSDGTaintAnalysis;
import com.ibm.wala.examples.SOAP2020.织女.sdg.JavaSourceWalaSDGTaintAnalysis;
import com.ibm.wala.examples.SOAP2020.织女.sdg.PythonWalaSDGTaintAnalysis;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.MutableMapping;
import com.ibm.wala.util.io.Streams;
import com.sun.net.httpserver.HttpContext;

import magpiebridge.core.AnalysisConsumer;
import magpiebridge.core.MagpieClient;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.ServerConfiguration;
import magpiebridge.projectservice.java.JavaProjectService;
import magpiebridge.util.MessageLogger;

@SuppressWarnings("unchecked")
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

	public static MagpieServer bridge(boolean useIFDS) {
		MutableMapping<String> uriCache = MutableMapping.make();
		
		JavaProjectService services = new JavaProjectService();
		
		ServerConfiguration conf = new ServerConfiguration();
		conf.setWebServices(true);
		conf.setMagpieMessageLogger(new MessageLogger());
		
		MagpieServer bridge = new MagpieServer(conf) {

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
		if (useIFDS) {
			bridge.addAnalysis(new JSWalaIFDSTaintAnalysis(uriCache), "js", "javascript");
			bridge.addAnalysis(new JSHtmlWalaIFDSTaintAnalysis(uriCache), "html");
			bridge.addAnalysis(new JavaSourceWalaIFDSTaintAnalysis(uriCache), "java");
			bridge.addAnalysis(new PythonWalaIFDSTaintAnalysis(uriCache), "py", "python");
		} else {
			bridge.addAnalysis(new JSWalaSDGTaintAnalysis(uriCache), "js", "javascript");
			bridge.addAnalysis(new JSHtmlWalaSDGTaintAnalysis(uriCache), "html");
			bridge.addAnalysis(new JavaSourceWalaSDGTaintAnalysis(uriCache), "java");
			bridge.addAnalysis(new PythonWalaSDGTaintAnalysis(uriCache), "py", "python");
		}

		HttpContext zhinu = bridge.getHttpServer().createContext("/zhinu");
		zhinu.setHandler((he) -> {
			String htmlTemplate = new String(Streams.inputStream2ByteArray(织女.class.getClassLoader().getResourceAsStream("zhinu.html")));

			htmlTemplate = htmlTemplate.replace("!!HOST!!", InetAddress.getLocalHost().getHostName() + ":" + bridge.getHttpServer().getAddress().getPort());
			
			URI uri = he.getRequestURI();
			for (NameValuePair nvp : URLEncodedUtils.parse(uri, Charset.forName("UTF-8"))) {
				String val = nvp.getValue();
				switch(nvp.getName()) {
				case "nodes":
					htmlTemplate = htmlTemplate.replace("!!NODES!!", val);
					break;
				case "edges":
					htmlTemplate = htmlTemplate.replace("!!EDGES!!", val);
					break;					
				}
			}

			OutputStream outputStream = he.getResponseBody();
			he.sendResponseHeaders(200, htmlTemplate.length());
			outputStream.write(htmlTemplate.getBytes());
			outputStream.flush();
			outputStream.close();
		});

		HttpContext jump = bridge.getHttpServer().createContext("/jump");
		jump.setHandler((he) -> {
			String url = null;
			int fc = -1, fl = -1, lc= -1, ll = -1;
			MagpieClient client = (MagpieClient) bridge.client;
			URI uri = he.getRequestURI();
			for (NameValuePair nvp : URLEncodedUtils.parse(uri, Charset.forName("UTF-8"))) {
				String val = nvp.getValue();
				switch(nvp.getName()) {
				case "fc": fc = Integer.parseInt(val); break;
				case "fl": fl = Integer.parseInt(val); break;
				case "lc": lc = Integer.parseInt(val); break;
				case "ll": ll = Integer.parseInt(val); break;
				case "url": url = uriCache.getMappedObject(Integer.parseInt(val)); break;
				}
			}
			Position f = new Position();
			f.setCharacter(fc);
			f.setLine(fl);
			Position l = new Position();
			l.setCharacter(lc);
			l.setLine(ll);
			Range r = new Range();
			r.setStart(f);
			r.setEnd(l);
			Location loc = new Location();
			loc.setUri(url);
			loc.setRange(r);

			client.jumpTo(loc);
			
			OutputStream outputStream = he.getResponseBody();
			he.sendResponseHeaders(200, "OK".length());
			outputStream.write("OK".getBytes());
			outputStream.flush();
			outputStream.close();
		});

		return bridge;
	}

	public static void main(String[] args) throws IOException, WalaException, CancelException {
		com.ibm.wala.cast.js.ipa.callgraph.JSCallGraphUtil.setTranslatorFactory(new CAstRhinoTranslatorFactory());

		if (args.length < 1) {
			MagpieServer bridge = bridge(false);
			bridge.launchOnStdio();
		} else if ("useIFDS".equals(args[0])) {
			MagpieServer bridge = bridge(true);
			bridge.launchOnStdio();	
		} else {
			boolean useIFDS = args.length  > 1 && "useIFDS".equals(args[1]);
			MutableMapping<String> uriCache = MutableMapping.make();
			Collection<Module> src = Collections.singleton(new SourceFileModule(new File(args[0]), args[0].substring(args[0].lastIndexOf('/')+1), null));
			AnalysisConsumer ac = (rs, s) -> { 
				System.out.println("results for " + s);
				rs.forEach(r -> {
					try {
						System.out.println(r.toString(false) + ": " + r.position().toString().trim());
						Iterable<Pair<com.ibm.wala.cast.tree.CAstSourcePositionMap.Position, String>> related = r.related();
						if (related != null ) {
							System.out.println("related:");
							for(Pair<com.ibm.wala.cast.tree.CAstSourcePositionMap.Position, String> e : related) {
								System.out.println(new SourceBuffer(e.fst).toString().trim());
							}
						}
					} catch (IOException e) {

					}
				});
			};
			switch (args[0].substring(args[0].lastIndexOf('.'))) {
			case ".js": {
				(useIFDS? new JSWalaIFDSTaintAnalysis(uriCache): new JSWalaSDGTaintAnalysis(uriCache)).analyze(src, ac, false);
				break;
			}
			case ".py": {
				(useIFDS? new PythonWalaIFDSTaintAnalysis(uriCache): new PythonWalaSDGTaintAnalysis(uriCache)).analyze(src, ac, false);
				break;
			}
			case ".java": {
				(useIFDS? new JavaSourceWalaIFDSTaintAnalysis(uriCache): new JavaSourceWalaSDGTaintAnalysis(uriCache)).analyze(src, ac, false);
				break;
			}
			case ".html": {
				(useIFDS? new JSHtmlWalaIFDSTaintAnalysis(uriCache): new JSHtmlWalaSDGTaintAnalysis(uriCache)).analyze(src, ac, false);
				break;
			}
			}
		}
	}
}
