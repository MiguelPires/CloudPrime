public class Recursive  {

	public static void main(String args[]) {
		
		if (args.length < 1) {
			System.out.println("Please enter a number of calls");
			return;
		} else {		
			try {
				int calls = Integer.parseInt(args[0]);
				recursiveFunction (calls);
			} catch(Exception e) {
				System.out.println("Please enter a number of calls");
			}
		}
	}
	
	public static void recursiveFunction (int calls) {
		if (calls == 1) 
			return;
		else
			recursiveFunction(calls-1);
	}
}
