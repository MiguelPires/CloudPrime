package cnv.cloudprime.webserver;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import BIT.highBIT.ClassInfo;
import BIT.highBIT.Instruction;
import BIT.highBIT.InstructionTable;
import BIT.highBIT.Routine;

public class Instrumentation {

    // can't use diamond operator => 1.4
    private static ConcurrentHashMap metrics = new ConcurrentHashMap();

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
            System.out.println("Instrumenting the class: " + absolutePathFile);

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
        } else {
            System.out.println("Invalid filename. Isn't a .class file");
        }
    }

    /*
     *  Stack Depth Metric - Increments the stack depth.
     */
    public static synchronized void incrStackDepth(String __) {
        Metrics threadMetrics = getOrCreate();
        threadMetrics.incrStackDepth(__);
    }

    /*
     *  Stack Depth Metric - Decrements the stack depth
     */
    public static synchronized void decrStackDepth(String __) throws IOException {
        Metrics threadMetrics = getOrCreate();
        threadMetrics.decrStackDepth(__);
    }

    /*
     * Byte Code Metric - Increments the executed byte code
     */
    public static synchronized void incrByteCode(String __) {
        Metrics threadMetrics = getOrCreate();
        threadMetrics.incrByteCode(__);
    }

    /*
     *  Method Calls Metric - Increments the number of calls
     */
    public static synchronized void incrCall(String __) {
        Metrics threadMetrics = getOrCreate();
        threadMetrics.incrCall(__);
    }

    private static Metrics getOrCreate() {
        long threadId = Thread.currentThread().getId();
        if (!metrics.containsKey(threadId))
            metrics.put(threadId, new Metrics());

        Metrics threadMetrics = (Metrics) metrics.get(threadId);
        return threadMetrics;
    }
}
