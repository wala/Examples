package com.ibm.wala.examples.SOAP2020.织女;

import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.List;
import java.util.Set;

import com.ibm.wala.cast.js.html.IncludedPosition;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.tree.impl.AbstractSourcePosition;
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
		Position p = null;
		IMethod m = s.getNode().getMethod();
		if (m instanceof AstMethod) {
			switch(s.getKind()) {
			case NORMAL: 
				p = ((AstMethod)m).getSourcePosition(((NormalStatement)s).getInstructionIndex());
				break;
			case PARAM_CALLER: 
				p = ((AstMethod)m).getSourcePosition(((ParamCaller)s).getInstructionIndex());
				break;
			}
		} 

		if (p != null) {
			Position mp = ((AstMethod)m).debugInfo().getCodeBodyPosition();
			if (p.compareTo(mp) == 0) {
				return null;
			}
		}
		
		if (p instanceof IncludedPosition) {
			if (p.getURL().toString().matches(".*#[0-9]+$")) {
				class FlatPos extends AbstractSourcePosition {
					private final IncludedPosition pp;
					private final Position incp;
					
					private FlatPos(Position p) {
						pp = (IncludedPosition)p;
						incp = pp.getIncludePosition();
					}
					
					@Override
					public URL getURL() {
						return incp.getURL();
					}

					@Override
					public Reader getReader() throws IOException {
						return incp.getReader();
					}

					@Override
					public int getFirstLine() {
						return incp.getFirstLine() + pp.getFirstLine() - 1;
					}

					@Override
					public int getLastLine() {
						return getFirstLine();
					}

					@Override
					public int getFirstCol() {
						return pp.getFirstCol();
					}

					@Override
					public int getLastCol() {
						return pp.getLastCol();
					}

					@Override
					public int getFirstOffset() {
						return incp.getFirstOffset() + pp.getFirstOffset();
					}

					@Override
					public int getLastOffset() {
						return incp.getFirstOffset() + pp.getLastOffset();
					}
				};
				
				p = new FlatPos(p);
			}
		}
		
		return p;
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
