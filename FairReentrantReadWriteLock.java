package com.jerry.chapter8;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An implementation of {@link ReadWriteLock} supporting same semantics to ReentrantReadWriteLock.
 * These is a known bug of ReentrantReadWriteLock in Java 8 standard lib, see more in https://bugs.openjdk.java.net/browse/JDK-8051848
 *
 * Here we have to write our own version of ReentrantReadWriteLock and use this one before we move to Java 9 or higher.
 *
 * @date 2019-02-18
 * @author Jerry Yang
 *
 */
public class FairReentrantReadWriteLock implements ReadWriteLock {
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Lock readLock = new ReadLock();
    private final Lock writeLock = new WriteLock();
    private final Queue<Long> waitingQueue = new LinkedList<>();
    private boolean writer = false;
    private int reader = 0;

    /* owner is only valid for exclusive ownership.
     * For multiple readers, owner is meaningless. */
    private Set<Long> owners = new HashSet<>();
    private ThreadLocal<Integer> readHoldCounter = ThreadLocal.withInitial(() -> 0);
    private ThreadLocal<Integer> writeHoldCounter = ThreadLocal.withInitial(() -> 0);

    private class ReadLock implements Lock {
        @Override
        public void lock() {
            final long me = Thread.currentThread().getId();
            lock.lock();
            try {
                if (owners.contains(me)) {
                    // it's locked by writer or reader in current thread
                    reader++;
                    readHoldCounter.set(readHoldCounter.get() + 1);
                    return;
                }
                putQueued(me);
                try {
                    while (writer || hasQueuedPredecessors(me)) {
                        condition.await();
                    }
                } finally {
                    removeQueued(me);
                }
                reader++;

                owners.add(me);
                readHoldCounter.set(readHoldCounter.get() + 1);
            } catch (InterruptedException ie) {
                // TODO
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            final long me = Thread.currentThread().getId();
            lock.lock();
            try {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                if (owners.contains(me)) {
                    // it's locked by writer or reader in current thread
                    reader++;
                    readHoldCounter.set(readHoldCounter.get() + 1);
                    return;
                }
                putQueued(me);
                try {
                    while (writer || hasQueuedPredecessors(me)) {
                        condition.await();
                    }
                } finally {
                    removeQueued(me);
                }
                reader++;
                owners.add(me);
                readHoldCounter.set(readHoldCounter.get() + 1);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean tryLock() {
            final long me = Thread.currentThread().getId();
            lock.lock();
            try {
                if (owners.contains(me)) {
                    // it's locked by writer in current thread
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
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void unlock() {
            final long me = Thread.currentThread().getId();
            lock.lock();
            try {
                if (reader <= 0 || readHoldCounter.get() <= 0) {
                    throw new IllegalMonitorStateException();
                }
                reader--;
                readHoldCounter.set(readHoldCounter.get() - 1);
                if (readHoldCounter.get() == 0 && writeHoldCounter.get() == 0) {
                    owners.remove(me);
                }
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            final long me = Thread.currentThread().getId();
            lock.lock();
            try {
                if (owners.contains(me)) {
                    // it's locked by writer in current thread
                    reader++;
                    readHoldCounter.set(readHoldCounter.get() + 1);
                    return true;
                }
                putQueued(me);
                try {
                    final long deadlineNano = System.nanoTime() + unit.toNanos(time);
                    while (writer || hasQueuedPredecessors(me)) {
                        long remainNano = deadlineNano - System.nanoTime();
                        if (remainNano <= 0 || !condition.await(remainNano, TimeUnit.NANOSECONDS)) {
                            break;
                        }
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
            } finally {
                lock.unlock();
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
            lock.lock();
            try {
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
                        condition.await();
                    }
                } finally {
                    removeQueued(me);
                }
                writer = true;

                // for reentrantant
                owners.add(me);
                writeHoldCounter.set(writeHoldCounter.get() + 1);
            } catch (InterruptedException ie) {
                // TODO
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            final long me = Thread.currentThread().getId();
            lock.lock();
            try {
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
                        condition.await();
                    }
                } finally {
                    waitingQueue.poll();
                }
                writer = true;

                // for reentrant
                owners.add(me);
                writeHoldCounter.set(writeHoldCounter.get() + 1);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean tryLock() {
            final long me = Thread.currentThread().getId();
            lock.lock();
            try {
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

                // for reentrant
                owners.add(me);
                writeHoldCounter.set(writeHoldCounter.get() + 1);
                return true;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void unlock() {
            final long me = Thread.currentThread().getId();
            lock.lock();
            try {
                if (writeHoldCounter.get() <= 0 || !owners.contains(me)) {
                    throw new IllegalMonitorStateException();
                }
                writeHoldCounter.set(writeHoldCounter.get() - 1);
                if (writeHoldCounter.get() == 0) {
                    writer = false;
                }
                if (readHoldCounter.get() == 0 && writeHoldCounter.get() == 0) {
                    owners.remove(me);
                }
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            final long me = Thread.currentThread().getId();
            lock.lock();
            try {
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
                        if (remainNano <= 0 || !condition.await(remainNano, TimeUnit.NANOSECONDS)) {
                            break;
                        }
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

                // for reentrant
                owners.add(me);
                writeHoldCounter.set(writeHoldCounter.get() + 1);

                return true;
            } finally {
                lock.unlock();
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
        lock.lock();
        try {
            return this.writer ? 0 : this.reader;
        } finally {
            lock.unlock();
        }
    }

    public boolean isWriteLocked() {
        return this.writer;
    }

    public boolean isWriteLockedByCurrentThread() {
        lock.lock();
        try {
            return this.writer && ((WriteLock) (this.writeLock())).getOwner() == Thread.currentThread().getId();
        } finally {
            lock.unlock();
        }
    }

    public int getWriteHoldCount() {
        lock.lock();
        try {
            return this.writer ? ((WriteLock) (this.writeLock())).getHoldCount() : 0;
        } finally {
            lock.unlock();
        }
    }

    long getOwner() {
        /**
         * Owner is valid for Writer.
         * Since readers can share read lock and each reader is fair, no ownership for multiple readers.
         **/
        lock.lock();
        try {
            return this.writer ? ((WriteLock) (this.writeLock())).getOwner() : 0L;
        } finally {
            lock.unlock();
        }
    }

    boolean hasQueuedThreads(long tid) {
        lock.lock();
        try {
            return (!waitingQueue.isEmpty()) && waitingQueue.contains(tid);
        } finally {
            lock.unlock();
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
