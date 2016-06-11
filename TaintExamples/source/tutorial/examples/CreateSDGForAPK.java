package tutorial.examples;

import static tutorial.examples.Analysis.*;

import java.io.File;
import java.net.URI;
import java.util.Set;

import com.ibm.wala.dalvik.test.callGraph.DalvikCallGraphTestBase;
import com.ibm.wala.dalvik.test.util.Util;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;

public class CreateSDGForAPK {

	private static int timeout = -1;

	private static URI[] libs() {
		File f = new File("libs");
		if (f.exists() && f.isDirectory()) {
			Set<URI> libs = HashSetFactory.make();
			for(File l : f.listFiles()) {
				String name = l.getName();
				if (name.endsWith("jar") || name.endsWith("dex")) {
					libs.add(l.toURI());
				}
			}

			return libs.toArray(new URI[ libs.size() ]);
		}

		return Util.androidLibs();
	}

	public static void main(String[] args) {
		File apk = new File(args[0]);
		try {
			timeout = Integer.parseInt(args[1]);
			System.err.println("timeout is " + timeout);
		} catch (Throwable e) {
			// no timeout specified
		}
		System.err.println("Analyzing " + apk + "...");
		try {
			long time = System.currentTimeMillis();
			Pair<CallGraph, PointerAnalysis<InstanceKey>> CG;
			final long startTime = System.currentTimeMillis();
			IProgressMonitor pm = new IProgressMonitor() {
				private boolean cancelled = false;

				@Override
				public void beginTask(String task, int totalWork) {
					// TODO Auto-generated method stub	
				}

				@Override
				public void subTask(String subTask) {
					// TODO Auto-generated method stub
				}

				@Override
				public void cancel() {
					cancelled = true;
				}

				@Override
				public boolean isCanceled() {
					if (System.currentTimeMillis() - startTime > timeout) {
						cancelled = true;
					}
					return cancelled;
				}

				@Override
				public void done() {
					// TODO Auto-generated method stub					
				}

				@Override
				public void worked(int units) {
					// TODO Auto-generated method stub
				}

				@Override
				public String getCancelMessage() {
					return "timeout";
				}	
			};
			CG = DalvikCallGraphTestBase.makeAPKCallGraph(libs(), null, apk.getAbsolutePath(), pm, ReflectionOptions.NONE);
			System.err.println("Analyzed " + apk + " in " + (System.currentTimeMillis() - time));

			SDG<InstanceKey> sdg = new SDG<InstanceKey>(CG.fst, CG.snd, DataDependenceOptions.NO_BASE_NO_HEAP_NO_EXCEPTIONS, ControlDependenceOptions.NONE);
			
			System.err.println(getPaths(sdg, getDeviceSource, sendMessageSink));

		} catch (Throwable e) {
			e.printStackTrace(System.err);
		}

	}
}
