package com.jerry.chapter8;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An implementation of {@link ReadWriteLock} supporting same semantics to ReentrantReadWriteLock.
 * These is a known bug of ReentrantReadWriteLock in Java 8 standard lib, see more in https://bugs.openjdk.java.net/browse/JDK-8051848
 *
 * Here we have to write our own version of ReadWriteLock and use this one before we move to Java 9 or higher.
 *
 * @date 2019-02-18
 * @author Jerry Yang
 *
 */
public class NonFairReentrantReadWriteLock implements ReadWriteLock {
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Lock readLock = new ReadLock();
    private final Lock writeLock = new WriteLock();
    private boolean writer = false;
    private int reader = 0;

    /* owner is only valid for exclusive ownership.
     * For multiple readers, owner is meaningless. */
    private long owner = 0;
    private int depth = 0;

    private class ReadLock implements Lock {
        @Override
        public void lock() {
            final long me = Thread.currentThread().getId();
            lock.lock();
            try {
                if (owner == me) {
                    // it's locked by writer in current thread
                    reader++;
                    System.out.println(String.format("DEBUG : read acquired lock that was locked by writer id %d, reader counter %d", owner, reader));
                    return;
                }
                while (writer) {
                    condition.await();
                }
                reader++;
                System.out.println(String.format("DEBUG : read acquired lock, reader counter %d", reader));
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
                if (owner == me) {
                    // it's locked by writer in current thread
                    reader++;
                    return;
                }
                while (writer) {
                    condition.await();
                }
                reader++;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean tryLock() {
            final long me = Thread.currentThread().getId();
            lock.lock();
            try {
                if (owner == me) {
                    // it's locked by writer in current thread
                    reader++;
                    return true;
                }
                if (writer) {
                    return false;
                }
                reader++;
                return true;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void unlock() {
            lock.lock();
            try {
                if (reader <= 0) {
                    throw new IllegalMonitorStateException();
                }
                System.out.println(String.format("DEBUG : read released lock, reader counter %d", reader));
                reader--;
                if (reader == 0) {
                    condition.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            lock.lock();
            try {
                final long deadlineNano = System.nanoTime() + unit.toNanos(time);
                while (writer) {
                    long remainNano = deadlineNano - System.nanoTime();
                    if (remainNano <= 0 || !condition.await(remainNano, TimeUnit.NANOSECONDS)) {
                        break;
                    }
                }
                if (writer) {
                    return false;
                }
                reader++;
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
                if (owner == me) {
                    depth++;
                    return;
                }

                while (writer || reader > 0) {
                    condition.await();
                }
                writer = true;

                // for reentrant
                owner = me;
                depth = 1;
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
                if (owner == me) {
                    depth++;
                    return;
                }
                while (writer || reader > 0) {
                    condition.await();
                }
                writer = true;

                // for reentrant
                owner = me;
                depth = 1;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean tryLock() {
            final long me = Thread.currentThread().getId();
            lock.lock();
            try {
                if (owner == me) {
                    depth++;
                    return true;
                }

                if (writer || reader > 0) {
                    return false;
                }
                writer = true;

                // for reentrant
                owner = me;
                depth = 1;
                return true;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void unlock() {
            lock.lock();
            try {
                if (depth <= 0 || owner != Thread.currentThread().getId()) {
                    throw new IllegalMonitorStateException();
                }
                depth--;
                if (depth == 0) {
                    owner = 0;
                    writer = false;
                    condition.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            final long me = Thread.currentThread().getId();
            lock.lock();
            try {
                if (owner == me) {
                    depth++;
                    return true;
                }

                final long deadlineNano = System.nanoTime() + unit.toNanos(time);
                while (writer || reader > 0) {
                    long remainNano = deadlineNano - System.nanoTime();
                    if (remainNano <= 0 || !condition.await(remainNano, TimeUnit.NANOSECONDS)) {
                        break;
                    }
                }
                if (writer || reader > 0) {
                    return false;
                }
                writer = true;

                // for reentrant
                owner = me;
                depth = 1;

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
            return owner;
        }
        protected int getWriteHoldCount() {
            return depth;
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
            return this.writer ? ((WriteLock) (this.writeLock())).getWriteHoldCount() : 0;
        } finally {
            lock.unlock();
        }
    }

    public long getOwner() {
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
}
