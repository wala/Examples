package tutorial.examples;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.Statement;

public class Print {

	public static void printPath(List<Statement> path) throws IOException {
		for(Statement s: path) {
			IMethod m = s.getNode().getMethod();
			boolean ast = m instanceof AstMethod;
			switch (s.getKind()) {
			case NORMAL: {
				if (ast) {
					Position p = ((AstMethod)m).getSourcePosition(((NormalStatement)s).getInstructionIndex());
					SourceBuffer buf = new SourceBuffer(p);
					System.err.println(buf + " (" + p + ")");
				}
				break;
			}
			case PARAM_CALLER: {
				if (ast) {
					Position p = ((AstMethod)m).getSourcePosition(((ParamCaller)s).getInstructionIndex());
					SourceBuffer buf = new SourceBuffer(p);
					System.err.println(buf + " (" + p + ")");
				}
				break;
			}
			}
		}
	}
	
	public static void printPaths(Set<List<Statement>> paths) throws IOException {
		for(List<Statement> path : paths) {
			System.err.println("Witness:");
			printPath(path);
		}
	}
}
