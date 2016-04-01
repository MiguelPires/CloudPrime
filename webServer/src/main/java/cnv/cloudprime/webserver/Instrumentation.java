package cnv.cloudprime.webserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Vector;

import BIT.highBIT.ClassInfo;
import BIT.highBIT.Instruction;
import BIT.highBIT.InstructionTable;
import BIT.highBIT.Routine;

public class Instrumentation {

    private static int maxStackDepth = 0;
    private static int currentStackDepth = 0;
    private static int bytesExecuted = 0;
    private static int calls = 0;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Enter a .class file to instrument");
            return;
        }
        String filename = args[0];
        String method = "calcPrimeFactors";

        instrument(filename, method);
    }

    /*
     *  Injects calls to the instrumentation methods in the desired .class
     */
    public static synchronized void instrument(String filename, String methodName) {
        File file = new File(filename);

        if (filename.endsWith(".class")) {
            String absolutePathFile = file.getAbsolutePath();
            System.out.println("The file's full path is: " + absolutePathFile);

            ClassInfo ci = new ClassInfo(absolutePathFile);
            Vector<?> routines = ci.getRoutines();
            
            for (Enumeration<?> e = routines.elements(); e.hasMoreElements();) {
                Routine routine = (Routine) e.nextElement();
                if (routine.getMethodName().equals(methodName)) {
                    
                    routine.addBefore("cnv/cloudprime/webserver/Instrumentation", "incrStackDepth",
                            "");
                    routine.addAfter("cnv/cloudprime/webserver/Instrumentation", "decrStackDepth",
                            "");
                }

                Instruction[] instructions = routine.getInstructions();

                for (int i = 0; i < instructions.length; ++i) {
                    Instruction instr = instructions[i];
                    // NOTE - a possible metric could be the number of memory-accessing operations
                    // since these are liable to be less efficient 
                    instr.addBefore("cnv/cloudprime/webserver/Instrumentation", "incrByteCode", "");

                    if (InstructionTable.OpcodeName[instr.getOpcode()].contains("invoke")) {
                        instr.addBefore("cnv/cloudprime/webserver/Instrumentation", "incrCall", "");
                    } 
                }
            }
            ci.write(filename);
        }
    }

    /*
     *  Stack Depth Metric - Increments the stack depth.
     */
    public static synchronized void incrStackDepth(String __) {
        currentStackDepth++;

        if (currentStackDepth > maxStackDepth) {
            maxStackDepth = currentStackDepth;
        }
    }

    /*
     *  Stack Depth Metric - Decrements the stack depth
     */
    public static synchronized void decrStackDepth(String __) throws FileNotFoundException {
        currentStackDepth--;

        if (currentStackDepth == 0) {
            flushMetrics();
            clearMetrics();
        }
    }

    /*
     * Byte Code Metric - Increments the executed byte code
     */
    public static synchronized void incrByteCode(String __) {
        bytesExecuted++;
    }


    /*
     *  Method Calls Metric - Increments the number of calls
     */
    public static synchronized void incrCall(String __) {
        calls++;
    }

    /*
     *  Logs every metric to disk after the execution ends 
     */
    public static synchronized void flushMetrics() throws FileNotFoundException {
        PrintWriter writer = new PrintWriter(new FileOutputStream(new File("metrics.log"), true));
        long threadId = Thread.currentThread().getId();

        // write metrics
        writer.write("Thread: " + threadId + " - Max depth: " + maxStackDepth + "\n");
        writer.write("Thread: " + threadId + " - Bytes executed: " + bytesExecuted + "\n");
        writer.write("Thread: " + threadId + " - Function calls: " + calls + "\n");

        writer.close();
    }

    /*
     *  Clears the metrics' value  
     */
    public static synchronized void clearMetrics() {
        currentStackDepth = 0;
        maxStackDepth = 0;
        bytesExecuted = 0;
        calls = 0;
    }
}
