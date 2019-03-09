# ReentrantReadWriteLock
My own implementation of ReentrantReadWriteLock

 An implementation of {@link ReadWriteLock} supporting same semantics to ReentrantReadWriteLock.
 These is a known bug of ReentrantReadWriteLock in Java 8 standard lib, see more in https://bugs.openjdk.java.net/browse/JDK-8051848
 *
 * Here we have to write our own version of ReentrantReadWriteLock and use this one before we move to Java 9 or higher.
 * This supports:
 * 1. Basic ReadWriteLock semantics.
 * 2. FIFO - First In First Out, as the naming 'fair' indicates.
 * 3. Reentrant semantics.

File Notes:
 * ReentrantReadWriteLock.java : use ReentrantLock() lock.
 
 * ReentrantReadWriteLockII.java : use intrinsic object lock by synchronized. This is improved version with fixes to make sure lock() is noninterruptable, which is compliant with Java ReentrantReadWriteLock semantic.
 * Test cases are mimic from the standard Java concurrent test cases.
