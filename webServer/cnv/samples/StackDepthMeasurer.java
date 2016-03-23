import BIT.highBIT.*;
import BIT.lowBIT.*;
import java.io.File;
import java.util.*;

public class StackDepthMeasurer {

	private static int maxStackDepth = 0;
	private static int currentStackDepth = 0;

	public static void main (String args[]) {
		if (args.length < 2 || args[0].equals("-h") || args[0].equals("--help") ) {
			System.out.println("Please enter the .class file to analyze and the name of the method to measure");
			System.out.println("StackDepthMeasurer Example.class SomeMethod");
			return;
		}

		String filename = args[0];
		String methodName = args[1];

		System.out.println("Method name: "+methodName);
		File file = new File(filename);

		if (filename.endsWith(".class")){
			String absolutePathFile = file.getAbsolutePath();
			ClassInfo ci = new ClassInfo(absolutePathFile);
			Vector routines = ci.getRoutines();

			for (Enumeration e = routines.elements(); e.hasMoreElements();){
				Routine routine = (Routine) e.nextElement();

				if (routine.getMethodName().equals(methodName)) {
					// NOTE
					// for the project one could also pass the argument of the call and after the routine returns (i.e., in decrStackDepth when the currentStackDepth is 0) store the maximum stack depth associated with the parameter
					routine.addBefore("StackDepthMeasurer", "incrStackDepth", "");
					routine.addAfter("StackDepthMeasurer", "decrStackDepth", "");
				}
			}	

			ci.addAfter("StackDepthMeasurer", "printMax", methodName);
			ci.write(filename);
		}	
	}

	public static synchronized void incrStackDepth(String _) {
		currentStackDepth++;
	}
	public static synchronized void decrStackDepth(String _) {
		maxStackDepth = currentStackDepth > maxStackDepth ? currentStackDepth : maxStackDepth;
		--currentStackDepth;
	}

	public static synchronized void printMax(String method) {
		System.out.println("Maximum stack for method '"+method+"' is "+maxStackDepth);
	}

}
