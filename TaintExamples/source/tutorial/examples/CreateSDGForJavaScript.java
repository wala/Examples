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

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Set;

import com.ibm.wala.cast.js.ipa.callgraph.JSCFABuilder;
import com.ibm.wala.cast.js.ipa.modref.JavaScriptModRef;
import com.ibm.wala.cast.js.test.JSCallGraphBuilderUtil;
import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;

public class CreateSDGForJavaScript {

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
  
  public static void main(String[] args) throws IOException, WalaException, CancelException {
    com.ibm.wala.cast.js.ipa.callgraph.JSCallGraphUtil.setTranslatorFactory(new CAstRhinoTranslatorFactory());

    SDG<InstanceKey> sdg = sdg(args[0], DataDependenceOptions.NO_BASE_NO_HEAP_NO_EXCEPTIONS, ControlDependenceOptions.NONE);
    Set<List<Statement>> paths = Analysis.getPaths(sdg, Analysis.documentUrlSource, Analysis.documentWriteSink);
    System.err.println(paths);
    Print.printPaths(paths);
  }
}
