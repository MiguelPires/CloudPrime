import BIT.highBIT.*;
import BIT.lowBIT.*;
import java.io.File;
import java.util.*;

public class ByteCodeExecuted {

	static int byteCodesExecuted = 0; 

	public static void main (String args[]) {
		if (args.length < 1) {
			System.out.println("Please enter the .class file to analyze");
			return;
		}

		String filename = args[0];
		File file = new File(filename);
		if (filename.endsWith(".class")){
			String absolutePathFile = file.getAbsolutePath();
		
			ClassInfo ci = new ClassInfo(absolutePathFile);
			Vector routines = ci.getRoutines();

			for (Enumeration e = routines.elements(); e.hasMoreElements();){
				Routine routine = (Routine) e.nextElement();
				Instruction[] instructions = routine.getInstructions();

				for (int i = 0; i < instructions.length; ++i) {
					Instruction instr = instructions[i];
					int opcode = instr.getOpcode();
					instr.addBefore("ByteCodeExecuted", "incCounter", opcode);
				}
			}	

			ci.addAfter("ByteCodeExecuted", "printCounter", "");
			ci.write(filename);
		}	
	}

	public static synchronized void printCounter(String _) {
		System.out.println("Executed "+byteCodesExecuted+" byte code instructions");
	}

	public static synchronized void incCounter(int code){
		byteCodesExecuted++;	
	}

}
