To build the project run:
	mvn clean install

To run the server:
	mvn exec:java -DfirstPrime=x -DsecondPrime=y
(where x and y and lengths for the prime numbers used to generate the semiprime.
For instance, x = 25 and y = 27)
