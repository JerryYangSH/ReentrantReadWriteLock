package com.jerry.chapter8;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * An implementation of {@link ReadWriteLock} supporting same semantics to ReentrantReadWriteLock.
 * These is a known bug of ReentrantReadWriteLock in Java 8 standard lib, see more in https://bugs.openjdk.java.net/browse/JDK-8051848
 *
 * Here we have to write our own version of ReentrantReadWriteLock and use this one before we move to Java 9 or higher.
 * This supports:
 * 1. Basic ReadWriteLock semantics.
 * 2. FIFO - First In First Out, as the naming 'fair' indicates.
 * 3. Reentrant semantics.
 *
 * @date 2019-02-18
 * @author Jerry Yang
 *
 */
public class FairReentrantReadWriteLockII implements ReadWriteLock {
    private final Object lock = new Object();
    private final Lock readLock = new ReadLock();
    private final Lock writeLock = new WriteLock();
    private final Queue<Long> waitingQueue = new LinkedList<>();
    private boolean writer = false;
    private int reader = 0;

    /**
     * Reader and writer share the same owner set.
     * For exclusive (write) mode, owner size should be one.
     * For share readers mode, there are more than one owners.
     **/
    private Set<Long> owners = new HashSet<>();
    private ThreadLocal<Integer> readHoldCounter = ThreadLocal.withInitial(() -> 0);
    private ThreadLocal<Integer> writeHoldCounter = ThreadLocal.withInitial(() -> 0);

    private class ReadLock implements Lock {
        @Override
        public void lock() {
            final long me = Thread.currentThread().getId();
            synchronized (lock) {
                if (owners.contains(me)) {
                    // it's locked by writer or reader in current thread
                    reader++;
                    readHoldCounter.set(readHoldCounter.get() + 1);
                    return;
                }
                putQueued(me);
                try {
                    while (writer || hasQueuedPredecessors(me)) {
                        try {
                            lock.wait();
                        } catch (InterruptedException ie) {
                            // eat it
                        }
                    }
                } finally {
                    removeQueued(me);
                }
                reader++;
                owners.add(me);
                readHoldCounter.set(readHoldCounter.get() + 1);
            }
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            final long me = Thread.currentThread().getId();
            synchronized (lock) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                if (owners.contains(me)) {
                    // reentrant, it's locked by writer or reader in current thread
                    reader++;
                    readHoldCounter.set(readHoldCounter.get() + 1);
                    return;
                }
                putQueued(me);
                try {
                    while (writer || hasQueuedPredecessors(me)) {
                        lock.wait();
                    }
                } finally {
                    removeQueued(me);
                }
                reader++;
                owners.add(me);
                readHoldCounter.set(readHoldCounter.get() + 1);
            }
        }

        @Override
        public boolean tryLock() {
            final long me = Thread.currentThread().getId();
            synchronized (lock) {
                if (owners.contains(me)) {
                    // reentrant, it's locked by writer or reader in current thread
                    reader++;
                    readHoldCounter.set(readHoldCounter.get() + 1);
                    return true;
                }
                if (writer) {
                    return false;
                }
                reader++;
                owners.add(me);
                readHoldCounter.set(readHoldCounter.get() + 1);
                return true;
            }
        }

        @Override
        public void unlock() {
            final long me = Thread.currentThread().getId();
            synchronized (lock) {
                if (reader <= 0 || readHoldCounter.get() <= 0) {
                    throw new IllegalMonitorStateException();
                }
                reader--;
                readHoldCounter.set(readHoldCounter.get() - 1);
                if (readHoldCounter.get() == 0 && writeHoldCounter.get() == 0) {
                    owners.remove(me);
                }
                if (readHoldCounter.get() == 0) {
                    readHoldCounter.remove();
                    lock.notifyAll();
                }
            }
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            final long me = Thread.currentThread().getId();
            synchronized (lock) {
                if (owners.contains(me)) {
                    // reentrant, it's locked by writer or reader in current thread
                    reader++;
                    readHoldCounter.set(readHoldCounter.get() + 1);
                    return true;
                }
                putQueued(me);
                try {
                    final long deadlineNano = System.nanoTime() + unit.toNanos(time);
                    while (writer || hasQueuedPredecessors(me)) {
                        long remainNano = deadlineNano - System.nanoTime();
                        if (remainNano <= 0) {
                            break;
                        }
                        lock.wait(TimeUnit.MILLISECONDS.convert(remainNano, TimeUnit.NANOSECONDS),
                                (int)(remainNano % 1000000));
                    }
                    if (writer || hasQueuedPredecessors(me)) {
                        return false;
                    }
                } finally {
                    removeQueued(me);
                }

                reader++;
                owners.add(me);
                readHoldCounter.set(readHoldCounter.get() + 1);
                return true;
            }
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            int r = getReadLockCount();
            return super.toString() +
                    "[Read locks = " + r + "]";
        }
    }

    private class WriteLock implements Lock {
        @Override
        public void lock() {
            final long me = Thread.currentThread().getId();
            synchronized (lock) {
                if ((owners.size() == 1 && owners.contains(me))) {
                    writeHoldCounter.set(writeHoldCounter.get() + 1);
                    writer = true;
                    return;
                }

                putQueued(me);
                try {
                    while (owners.size() > 1 ||
                            (owners.size() == 1 && !owners.contains(me)) ||
                            hasQueuedPredecessors(me)) {
                        try {
                            lock.wait();
                        } catch (InterruptedException ie) {
                            // eat it since non-interruptable.
                        }
                    }
                } finally {
                    removeQueued(me);
                }
                writer = true;
                owners.add(me);
                writeHoldCounter.set(writeHoldCounter.get() + 1);
            }
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            final long me = Thread.currentThread().getId();
            synchronized (lock) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                if (owners.size() == 1 && owners.contains(me)) {
                    writeHoldCounter.set(writeHoldCounter.get() + 1);
                    writer = true;
                    return;
                }
                putQueued(me);
                try {
                    while (owners.size() > 1 ||
                            (owners.size() == 1 && !owners.contains(me)) ||
                            hasQueuedPredecessors(me)) {
                        lock.wait();
                    }
                } finally {
                    waitingQueue.poll();
                }
                writer = true;
                owners.add(me);
                writeHoldCounter.set(writeHoldCounter.get() + 1);
            }
        }

        @Override
        public boolean tryLock() {
            final long me = Thread.currentThread().getId();
            synchronized (lock) {
                if (owners.size() == 1 && owners.contains(me)) {
                    writeHoldCounter.set(writeHoldCounter.get() + 1);
                    writer = true;
                    return true;
                }

                if (owners.size() > 1 ||
                        (owners.size() == 1 && !owners.contains(me)) ||
                        hasQueuedPredecessors(me)) {
                    return false;
                }
                writer = true;
                owners.add(me);
                writeHoldCounter.set(writeHoldCounter.get() + 1);
                return true;
            }
        }

        @Override
        public void unlock() {
            final long me = Thread.currentThread().getId();
            synchronized (lock) {
                if (writeHoldCounter.get() <= 0 || !owners.contains(me)) {
                    throw new IllegalMonitorStateException();
                }
                writeHoldCounter.set(writeHoldCounter.get() - 1);
                if (readHoldCounter.get() == 0 && writeHoldCounter.get() == 0) {
                    owners.remove(me);
                }
                if (writeHoldCounter.get() == 0) {
                    writer = false;
                    writeHoldCounter.remove();
                    lock.notifyAll();
                }
            }
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            final long me = Thread.currentThread().getId();
            synchronized (lock) {
                if (owners.size() == 1 && owners.contains(me)) {
                    writeHoldCounter.set(writeHoldCounter.get() + 1);
                    writer = true;
                    return true;
                }

                putQueued(me);
                try {
                    final long deadlineNano = System.nanoTime() + unit.toNanos(time);
                    while (owners.size() > 1 ||
                            (owners.size() == 1 && !owners.contains(me)) ||
                            hasQueuedPredecessors(me)) {
                        long remainNano = deadlineNano - System.nanoTime();
                        if (remainNano <= 0) {
                            break;
                        }
                        lock.wait(TimeUnit.MILLISECONDS.convert(remainNano, TimeUnit.NANOSECONDS),
                                (int)(remainNano % 1000000));
                    }
                    if (owners.size() > 1 ||
                            (owners.size() == 1 && !owners.contains(me)) ||
                            hasQueuedPredecessors(me)) {
                        return false;
                    }
                } finally {
                    removeQueued(me);
                }
                writer = true;
                owners.add(me);
                writeHoldCounter.set(writeHoldCounter.get() + 1);

                return true;
            }
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            long o = getOwner();
            return super.toString() + ((o == 0L) ?
                    "[Unlocked]" :
                    "[Locked by thread " + o + "]");
        }

        protected long getOwner() {
            if (owners.isEmpty()) {
                return 0;
            }
            // just pick one
            return owners.iterator().next();
        }

        /**
         * @return the number of holds on this write lock by the 'current' thread.
         */
        protected int getHoldCount() {
            return writeHoldCounter.get();
        }
    }

    @Override
    public Lock readLock() {
        return this.readLock;
    }

    @Override
    public Lock writeLock() {
        return this.writeLock;
    }

    // non-override
    public int getReadLockCount() {
        synchronized (lock) {
            return this.writer ? 0 : this.reader;
        }
    }

    public boolean isWriteLocked() {
        return this.writer;
    }

    public boolean isWriteLockedByCurrentThread() {
        synchronized (lock) {
            return this.writer && ((WriteLock) (this.writeLock())).getOwner() == Thread.currentThread().getId();
        }
    }

    public int getWriteHoldCount() {
        synchronized (lock) {
            return this.writer ? ((WriteLock) (this.writeLock())).getHoldCount() : 0;
        }
    }

    long getOwner() {
        /**
         * Owner is valid for Writer.
         * Since readers can share read lock and each reader is fair, no ownership for multiple readers.
         **/
        synchronized (lock) {
            return this.writer ? ((WriteLock) (this.writeLock())).getOwner() : 0L;
        }
    }

    boolean hasQueuedThreads(long tid) {
        synchronized (lock) {
            return (!waitingQueue.isEmpty()) && waitingQueue.contains(tid);
        }
    }

    // Called within lock critical section
    private boolean hasQueuedPredecessors(long me) {
        return (!waitingQueue.isEmpty()) && waitingQueue.peek() != me;
    }

    // Called within lock critical section
    private boolean isQueuedPredecessor(long me) {
        return (!waitingQueue.isEmpty()) && waitingQueue.peek() == me;
    }

    // Called within lock critical section
    private void putQueued(long me) {
        waitingQueue.offer(me);
    }

    // Called within lock critical section
    private void removeQueued(long me) {
        if (isQueuedPredecessor(me)) {
            waitingQueue.poll();
        } else {
            waitingQueue.remove(me);
        }
    }
}
