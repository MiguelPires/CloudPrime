import BIT.highBIT.*;
import BIT.lowBIT.*;
import java.io.File;
import java.util.*;

public class OpcodeCounter {

	static ArrayList<Integer> opcodesCounter; 

	public static void main (String args[]) {
		if (args.length < 1) {
			System.out.println("Please enter the .class file to analyze");
			return;
		}


		//System.out.println("Opcode names: "+InstructionTable.OpcodeName.length);

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
					instr.addBefore("OpcodeCounter", "incOpcode", opcode);
				}
			}	

			ci.addAfter("OpcodeCounter", "printOpcodes", "");
			ci.write(filename);
		}	
	}

	public static synchronized void printOpcodes(String _) {
		for (int i = 0; i < InstructionTable.OpcodeName.length; ++i) {
			Integer counter = opcodesCounter.get(i);			
			if (counter != 0) 
				System.out.println(InstructionTable.OpcodeName[i]+": "+counter);
		}

	}
	public static synchronized void incOpcode(int code){
		try {

			if (opcodesCounter == null)
				opcodesCounter = new ArrayList<Integer>(Collections.nCopies(InstructionTable.OpcodeName.length, 0));
			
			Integer counter = opcodesCounter.get(code);
			opcodesCounter.add(code, counter+1);	
		} catch(IndexOutOfBoundsException ex){
			opcodesCounter.add(code, 0);
		}	

	}

}
