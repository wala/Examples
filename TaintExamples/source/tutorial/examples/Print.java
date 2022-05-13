package tutorial.examples;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IMethod.SourcePosition;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.shrikeCT.InvalidClassFileException;

public class Print {

	public static void printPath(List<Statement> path) throws IOException {
		for(Statement s: path) {
			String stmt = printStmt(s);
			if (stmt != null) {
				System.err.println(stmt);
			}
		}
	}

	public static Position getPosition(Statement s) {
		IMethod m = s.getNode().getMethod();
		if (m instanceof AstMethod) {
			switch(s.getKind()) {
			case NORMAL: 
				return ((AstMethod)m).getSourcePosition(((NormalStatement)s).getInstructionIndex());
			case PARAM_CALLER: 
				return ((AstMethod)m).getSourcePosition(((ParamCaller)s).getInstructionIndex());
			}
		} 
		
		return null;
	}
	
	public static String printStmt(Statement s) throws IOException {
		IMethod m = s.getNode().getMethod();
		boolean ast = m instanceof AstMethod;
		switch (s.getKind()) {
		case NORMAL: {
			if (ast) {
				Position p = getPosition(s);
				SourceBuffer buf = new SourceBuffer(p);
				return (buf + " (" + p + ")");
			} else {
				try {
					SourcePosition p = m.getSourcePosition(((NormalStatement)s).getInstructionIndex());
					if (p != null) {
						return p.toString();
					}
				} catch (InvalidClassFileException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			break;
		}
		case PARAM_CALLER: {
			if (ast) {
				Position p = getPosition(s);
				SourceBuffer buf = new SourceBuffer(p);
				return (buf + " (" + p + ")");
			} else {
				try {
					SourcePosition p = m.getSourcePosition(((ParamCaller)s).getInstructionIndex());
					if (p != null) {
						return p.toString();
					}
				} catch (InvalidClassFileException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (Exception e) {
					// ignore it
				}
			}
			break;
		}
		default: {

		}
		}
		
		return null;
	}

	public static void printPaths(Set<List<Statement>> paths) throws IOException {
		for(List<Statement> path : paths) {
			System.err.println("Witness:");
			printPath(path);
		}
	}
}
